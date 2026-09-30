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
    private val thumbs = LruCache<String, ImageBitmap>(48)
    private val pdfLock = Mutex()

    fun init(context: Context) {
        app = context.applicationContext
        PDFBoxResourceLoader.init(app)
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
        val out = ByteArrayOutputStream()
        load(uri).compress(Bitmap.CompressFormat.JPEG, 92, out)
        Vault.putBlob(out.toByteArray())
    }

    /**
     * One scan stays as it is. Two (a card's front and back) are laid out on one white
     * sheet, one above the other with a clear gap, the way a photocopy shop does it.
     */
    suspend fun importScan(uris: List<Uri>): String {
        if (uris.size == 1) return importPhoto(uris[0])
        return withContext(Dispatchers.IO) {
            val width = 1600; val margin = 90; val gap = 170
            val sides = uris.map { u ->
                val b = load(u)
                Bitmap.createScaledBitmap(b, width, (b.height * width.toFloat() / b.width).toInt().coerceAtLeast(1), true)
            }
            val sheet = Bitmap.createBitmap(width + margin * 2, margin * 2 + sides.sumOf { it.height } + gap * (sides.size - 1), Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(sheet).apply { drawColor(Color.WHITE) }
            var y = margin.toFloat()
            sides.forEach { canvas.drawBitmap(it, margin.toFloat(), y, null); y += it.height + gap }
            val out = ByteArrayOutputStream()
            sheet.compress(Bitmap.CompressFormat.JPEG, 92, out)
            Vault.putBlob(out.toByteArray())
        }
    }

    private fun load(uri: Uri): Bitmap {
        val cr = app.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2600) sample *= 2
        var bmp = cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("That file isn't a photo")
        val rotation = runCatching {
            cr.openInputStream(uri)!!.use { ExifInterface(it).rotationDegrees }
        }.getOrDefault(0)
        if (rotation != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        val scale = 2600f / maxOf(bmp.width, bmp.height)
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

    /** Opens the PDF the way the viewer will, so a locked one asks for its password up front. */
    suspend fun checkPdf(bytes: ByteArray, password: String?): PdfCheck = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(bytes, password ?: "").use { PdfCheck.Ok(it.numberOfPages) }
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            if (password == null) PdfCheck.NeedsPassword else PdfCheck.WrongPassword
        } catch (e: Exception) {
            PdfCheck.Broken
        }
    }

    suspend fun pageCount(doc: Doc): Int = pdfLock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                if (doc.password == null) withRenderer(doc) { it.pageCount }
                else PDDocument.load(Vault.readBlob(doc.blobId), doc.password).use { it.numberOfPages }
            }.getOrDefault(0)
        }
    }

    /** Page [index] rendered [width] pixels wide on white. Locked PDFs go through PDFBox. */
    suspend fun renderPage(doc: Doc, index: Int, width: Int): ImageBitmap? {
        val key = "${doc.blobId}#$index@$width"
        thumbs.get(key)?.let { return it }
        return pdfLock.withLock {
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
                }.getOrNull()?.asImageBitmap()?.also { thumbs.put(key, it) }
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

    /** Photos land in Pictures/Banking Pages (the gallery); PDFs in Download/Banking Pages. */
    suspend fun saveToPhone(blobId: String, fileName: String, mime: String): String = withContext(Dispatchers.IO) {
        val image = mime.startsWith("image/")
        val folder = (if (image) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS) + "/Banking Pages"
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
        if (image) "Saved to gallery · Banking Pages" else "Saved to Downloads · Banking Pages"
    }
}
