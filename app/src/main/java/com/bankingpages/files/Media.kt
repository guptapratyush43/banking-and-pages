package com.bankingpages.files

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.bankingpages.data.Crypto
import com.bankingpages.data.Doc
import com.bankingpages.data.Vault
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Photos and PDFs in and out of the vault. Decrypted bytes only ever touch the
 * disk as short-lived copies in cache/share (for the share sheet) and are
 * wiped at the next launch.
 */
object Media {
    private lateinit var app: Context
    // Bounded by bytes, not by count: a full-page render is ~100 times a thumbnail.
    private val thumbs = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(8 * 1024 * 1024)) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val pdfLock = Mutex()
    /** Rendered PDF pages, sealed like everything else, so a preview costs a decrypt and not a re-render. */
    private lateinit var renders: File
    private lateinit var pageCounts: android.content.SharedPreferences

    /** Width to render document pages at: about the screen, never absurdly large. */
    val viewerWidth: Int get() = app.resources.displayMetrics.widthPixels.coerceIn(720, 1200)

    fun init(context: Context) {
        app = context.applicationContext
        PDFBoxResourceLoader.init(app)
        renders = File(app.filesDir, "vault/renders").apply { mkdirs() }
        pageCounts = app.getSharedPreferences("pagecounts", Context.MODE_PRIVATE)
        File(app.cacheDir, "share").deleteRecursively()
        File(app.cacheDir, "camera").deleteRecursively()
    }

    /** A fresh file the camera app writes its photo into. */
    fun cameraUri(): Uri {
        val f = File(File(app.cacheDir, "camera").apply { mkdirs() }, "capture-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(app, "${app.packageName}.files", f)
    }

    /** Reads a picked, captured or scanned photo upright, at most 2600px, and seals it in the vault. */
    suspend fun importPhoto(uri: Uri): String = withContext(Dispatchers.IO) {
        val cr = app.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        val rotation = runCatching { cr.openInputStream(uri)!!.use { ExifInterface(it).rotationDegrees } }.getOrDefault(0)
        // The scanner hands over a finished, upright JPEG: keep its bytes, skip decoding and re-encoding.
        if (bounds.outMimeType == "image/jpeg" && rotation == 0 && maxOf(bounds.outWidth, bounds.outHeight) <= 2600) {
            return@withContext Vault.putBlob(cr.openInputStream(uri)!!.use { it.readBytes() })
        }
        val out = ByteArrayOutputStream()
        load(uri).compress(Bitmap.CompressFormat.JPEG, 92, out)
        Vault.putBlob(out.toByteArray())
    }

    /**
     * One scan stays as it is. Two (a card's front and back) are laid out on one white
     * sheet, side by side with a clear gap, the way a photocopy shop does it.
     */
    suspend fun importScan(uris: List<Uri>): String {
        if (uris.size == 1) return importPhoto(uris[0])
        return withContext(Dispatchers.IO) {
            val height = 1000; val margin = 80; val gap = 150
            val sides = uris.map { u ->
                val b = load(u, 1600)
                Bitmap.createScaledBitmap(b, (b.width * height.toFloat() / b.height).toInt().coerceAtLeast(1), height, true)
            }
            val sheet = Bitmap.createBitmap(margin * 2 + sides.sumOf { it.width } + gap * (sides.size - 1), height + margin * 2, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(sheet).apply { drawColor(Color.WHITE) }
            var x = margin.toFloat()
            sides.forEach { canvas.drawBitmap(it, x, margin.toFloat(), null); x += it.width + gap }
            val out = ByteArrayOutputStream()
            sheet.compress(Bitmap.CompressFormat.JPEG, 92, out)
            Vault.putBlob(out.toByteArray())
        }
    }

    /**
     * Two sides of a document on one white A4 page, side by side, front first. An ID card
     * (Aadhaar, PAN, licence) is drawn at its true size, 85.6 x 54 mm, so the page prints
     * like a photocopy; anything else shares the page width.
     *
     * The PDF is written directly with each side embedded as a JPEG. Android's PdfDocument
     * would re-encode raw pixels instead: seconds slower and several megabytes larger.
     */
    suspend fun idSheet(uris: List<Uri>): ByteArray = withContext(Dispatchers.IO) {
        val pageW = 210f; val pageH = 297f; val gap = 8f; val top = 18f
        // 1400px is ~400 dpi at card size: plenty for print, and quick to handle.
        val faces = uris.map { load(it, 1400) }
        val sizes = faces.map { f ->
            val ratio = f.width.toFloat() / f.height
            when {
                ratio in 1.35f..1.85f -> 85.6f to 53.98f            // a card lying flat
                ratio in (1 / 1.85f)..(1 / 1.35f) -> 53.98f to 85.6f // a card held upright
                else -> { val w = (pageW - 3 * gap) / 2; val h = (w / ratio).coerceAtMost(pageH - 2 * top); (h * ratio) to h }
            }
        }
        val jpegs = faces.map { f -> ByteArrayOutputStream().also { f.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray() }
        val k = 72f / 25.4f // points per millimetre
        val us = java.util.Locale.US
        val content = StringBuilder()
        var x = (pageW - (sizes.sumOf { it.first.toDouble() }.toFloat() + gap * (sizes.size - 1))) / 2
        sizes.forEachIndexed { i, (w, h) ->
            val px = x * k; val py = (pageH - top - h) * k
            content.append("q %.2f 0 0 %.2f %.2f %.2f cm /Im$i Do Q\n".format(us, w * k, h * k, px, py))
            content.append("0.62 G 0.15 w %.2f %.2f %.2f %.2f re S\n".format(us, px, py, w * k, h * k)) // hairline cut guide
            x += w + gap
        }
        val out = ByteArrayOutputStream()
        val offsets = ArrayList<Int>()
        fun w(t: String) = out.write(t.toByteArray(Charsets.ISO_8859_1))
        fun obj(n: Int, body: () -> Unit) { offsets.add(out.size()); w("$n 0 obj\n"); body(); w("endobj\n") }
        w("%PDF-1.4\n")
        obj(1) { w("<< /Type /Catalog /Pages 2 0 R >>\n") }
        obj(2) { w("<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n") }
        val images = faces.indices.joinToString(" ") { "/Im$it ${5 + it} 0 R" }
        obj(3) { w("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.2f %.2f] /Resources << /XObject << $images >> >> /Contents 4 0 R >>\n".format(us, pageW * k, pageH * k)) }
        val stream = content.toString().toByteArray(Charsets.ISO_8859_1)
        obj(4) { w("<< /Length ${stream.size} >>\nstream\n"); out.write(stream); w("\nendstream\n") }
        faces.forEachIndexed { i, f ->
            obj(5 + i) {
                w("<< /Type /XObject /Subtype /Image /Width ${f.width} /Height ${f.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpegs[i].size} >>\nstream\n")
                out.write(jpegs[i]); w("\nendstream\n")
            }
        }
        val xref = out.size()
        val count = offsets.size + 1
        w("xref\n0 $count\n0000000000 65535 f \n")
        offsets.forEach { w("%010d 00000 n \n".format(it)) }
        w("trailer\n<< /Size $count /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        out.toByteArray()
    }

    private fun load(uri: Uri, max: Int = 2600): Bitmap {
        val cr = app.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
        var bmp = cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("That file isn't a photo")
        val rotation = runCatching {
            cr.openInputStream(uri)!!.use { ExifInterface(it).rotationDegrees }
        }.getOrDefault(0)
        if (rotation != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        val scale = max.toFloat() / maxOf(bmp.width, bmp.height)
        if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        return bmp
    }

    suspend fun readUri(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    }

    fun displayName(uri: Uri): String = runCatching {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: "document"

    fun mimeOf(uri: Uri): String = app.contentResolver.getType(uri) ?: "application/octet-stream"

    /** A downsampled photo for grids and viewers; [max] is the longest side in pixels. */
    suspend fun photo(blobId: String, max: Int): ImageBitmap? {
        val key = "$blobId@$max"
        thumbs.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Vault.readBlob(blobId)
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                var s = 1
                while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            }.getOrNull()?.also { thumbs.put(key, it) }
        }
    }

    fun forget(blobId: String) {
        thumbs.snapshot().keys.filter { it.startsWith(blobId) }.forEach { thumbs.remove(it) }
    }

    // --- PDFs ---------------------------------------------------------------

    sealed interface PdfCheck {
        data class Ok(val pages: Int) : PdfCheck
        data object NeedsPassword : PdfCheck
        data object WrongPassword : PdfCheck
        data object Broken : PdfCheck
    }

    /** Page count through the system renderer: instant, and it refuses a password-protected file. */
    private fun quickPages(bytes: ByteArray): Int {
        val tmp = File(app.cacheDir, "check-${System.nanoTime()}.pdf")
        try {
            tmp.writeBytes(bytes)
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { return it.pageCount } }
        } finally {
            tmp.delete()
        }
    }

    /** Opens the PDF the way the viewer will, so a locked one asks for its password up front. */
    suspend fun checkPdf(bytes: ByteArray, password: String?): PdfCheck = withContext(Dispatchers.IO) {
        if (password == null) {
            try { return@withContext PdfCheck.Ok(quickPages(bytes)) }
            catch (e: SecurityException) { return@withContext PdfCheck.NeedsPassword }
            catch (e: Exception) { /* something unusual: let PDFBox have a go */ }
        }
        try {
            PDDocument.load(bytes, password ?: "").use { PdfCheck.Ok(it.numberOfPages) }
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            if (password == null) PdfCheck.NeedsPassword else PdfCheck.WrongPassword
        } catch (e: Exception) {
            PdfCheck.Broken
        }
    }

    suspend fun pageCount(doc: Doc): Int {
        pageCounts.getInt(doc.blobId, 0).takeIf { it > 0 }?.let { return it }
        return pdfLock.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (doc.password == null) withRenderer(doc) { it.pageCount }
                    else PDDocument.load(Vault.readBlob(doc.blobId), doc.password).use { it.numberOfPages }
                }.getOrDefault(0)
            }
        }.also { if (it > 0) pageCounts.edit().putInt(doc.blobId, it).apply() }
    }

    private fun renderFile(key: String) = File(renders, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".bin")

    private fun diskGet(key: String): Bitmap? = runCatching {
        val f = renderFile(key)
        if (!f.exists()) null else Crypto.decrypt(f.readBytes()).let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }.getOrNull()

    private fun diskPut(key: String, bmp: Bitmap) {
        runCatching {
            renders.mkdirs()
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
            renderFile(key).writeBytes(Crypto.encrypt(out.toByteArray()))
        }
    }

    /**
     * Page [index] rendered [width] pixels wide on white. Locked PDFs go through PDFBox, which is
     * slow, so every PDF render is kept on disk (sealed) and comes back at once next time.
     */
    suspend fun renderPage(doc: Doc, index: Int, width: Int): ImageBitmap? {
        val key = "${doc.blobId}#$index@$width"
        thumbs.get(key)?.let { return it }
        if (doc.isPdf) withContext(Dispatchers.IO) { diskGet(key) }?.asImageBitmap()?.let { thumbs.put(key, it); return it }
        return pdfLock.withLock {
            // Someone else may have rendered it while this one waited its turn.
            thumbs.get(key)?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!doc.isPdf) return@runCatching photoBytesBitmap(Vault.readBlob(doc.blobId), width)
                    if (doc.password == null) withRenderer(doc) { r ->
                        r.openPage(index).use { p ->
                            val h = (width * p.height.toFloat() / p.width).toInt()
                            Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(Color.WHITE)
                                p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    } else PDDocument.load(Vault.readBlob(doc.blobId), doc.password).use { d ->
                        val box = d.getPage(index).mediaBox
                        PDFRenderer(d).renderImage(index, width / box.width)
                    }
                }.getOrNull()?.also { if (doc.isPdf) diskPut(key, it) }?.asImageBitmap()?.also { thumbs.put(key, it) }
            }
        }
    }

    private fun photoBytesBitmap(bytes: ByteArray, width: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (o.outWidth / (s * 2) >= width) s *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private inline fun <T> withRenderer(doc: Doc, block: (PdfRenderer) -> T): T {
        val tmp = File(app.cacheDir, "render-${doc.blobId}.pdf")
        try {
            tmp.writeBytes(Vault.readBlob(doc.blobId))
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { return block(it) }
            }
        } finally {
            tmp.delete()
        }
    }

    // --- Out of the app ---------------------------------------------------

    private fun safeName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "file" }

    /** Hands a decrypted copy to the share sheet. */
    suspend fun share(context: Context, blobId: String, fileName: String, mime: String) {
        val uri = withContext(Dispatchers.IO) {
            val dir = File(app.cacheDir, "share/${System.nanoTime()}").apply { mkdirs() }
            val f = File(dir, safeName(fileName)).apply { writeBytes(Vault.readBlob(blobId)) }
            FileProvider.getUriForFile(app, "${app.packageName}.files", f)
        }
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share $fileName"))
    }

    fun shareText(context: Context, text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, title))
    }

    /** Photos land in Pictures/Banking and Pages (the gallery); PDFs in Download/Banking and Pages. */
    suspend fun saveToPhone(blobId: String, fileName: String, mime: String): String = withContext(Dispatchers.IO) {
        val image = mime.startsWith("image/")
        val folder = (if (image) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS) + "/Banking and Pages"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName(fileName))
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (image) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val cr = app.contentResolver
        val uri = cr.insert(collection, values) ?: error("Couldn't save to the phone")
        try {
            cr.openOutputStream(uri)!!.use { it.write(Vault.readBlob(blobId)) }
            cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            cr.delete(uri, null, null)
            throw e
        }
        if (image) "Saved to gallery · Banking and Pages" else "Saved to Downloads · Banking and Pages"
    }
}
