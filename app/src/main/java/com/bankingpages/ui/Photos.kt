package com.bankingpages.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.bankingpages.data.Account
import com.bankingpages.data.PhotoSlot
import com.bankingpages.data.Pin
import com.bankingpages.files.Media
import com.bankingpages.ui.motion.bounceClick
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Opens Google's document scanner: [pages] is the most it will take, [pdf] also asks for one PDF of them all. */
class Scanner(val scan: (pages: Int, pdf: Boolean) -> Unit)

/**
 * Google's own scanner screen: it finds the edges, lets you drag the corners,
 * and can also take a picture from the gallery. Reports each page's image and,
 * when asked, the PDF.
 */
@Composable
fun rememberScanner(onCancel: () -> Unit = {}, onResult: (pages: List<Uri>, pdf: Uri?) -> Unit): Scanner {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onResult)
    val cancelled by rememberUpdatedState(onCancel)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val r = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
        if (res.resultCode == Activity.RESULT_OK && r != null) callback(r.pages?.map { it.imageUri }.orEmpty(), r.pdf?.uri)
        else cancelled()
    }
    return remember {
        Scanner { pages, pdf ->
            val activity = context as? Activity ?: return@Scanner
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(pages)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .apply {
                    if (pdf) setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                    else setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                }
                .build()
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { Pin.awayOnPurpose = true; launcher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { toast(context, "The scanner isn't available: ${it.message}") }
        }
    }
}

@Composable
fun rememberPhoto(blobId: String?, max: Int): ImageBitmap? {
    val img by produceState<ImageBitmap?>(null, blobId, max) { value = blobId?.let { Media.photo(it, max) } }
    return img
}

private class SlotLook(val icon: ImageVector, val tint: Color, val hint: String)

private fun PhotoSlot.look() = when (this) {
    PhotoSlot.CHEQUE -> SlotLook(Icons.Rounded.ReceiptLong, Color(0xFF1E9E8A), "Scan the cheque leaf")
    PhotoSlot.PASSBOOK -> SlotLook(Icons.AutoMirrored.Rounded.MenuBook, Color(0xFFD9932B), "Scan the first page")
    PhotoSlot.DEBIT_FRONT, PhotoSlot.DEBIT_BACK -> SlotLook(Icons.Rounded.CreditCard, Color(0xFF3B7BE0), "Scan front, then back")
    PhotoSlot.CREDIT_FRONT, PhotoSlot.CREDIT_BACK -> SlotLook(Icons.Rounded.Payments, Color(0xFF8A55E0), "Scan front, then back")
}

private class Tile(val key: String, val label: String, val look: SlotLook, val blobId: String?, val card: Boolean)

/**
 * Cheque, passbook and cards as a two-column grid, each with its own icon and colour.
 * An empty tile opens the scanner; a card is scanned twice, front then back, and kept
 * as one picture. "Add another card" takes as many debit and credit cards as you have.
 * Every filled tile carries its own small Share and Download.
 */
@Composable
fun PhotoGrid(
    account: Account,
    onOpen: (blobId: String, label: String) -> Unit,
    onAdd: (key: String, uris: List<Uri>) -> Unit,
    onRemove: ((key: String) -> Unit)?,
    busyKey: String? = null
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    // A card's front, waiting for its back.
    var front by rememberSaveable { mutableStateOf<String?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    fun isCard(key: String) = key.startsWith("n:") || (key.startsWith("s:") && PhotoSlot.valueOf(key.drop(2)).isCard)
    val again = remember { arrayOfNulls<Scanner>(1) }
    val scanner = rememberScanner(
        // Backing out of the second scan keeps the front on its own.
        onCancel = { front?.let { f -> target?.let { onAdd(it, listOf(Uri.parse(f))) } }; front = null }
    ) { pages, _ ->
        val key = target
        val page = pages.firstOrNull()
        if (key != null && page != null) {
            if (isCard(key) && front == null) {
                front = page.toString()
                toast(context, "Front saved. Now scan the back")
                again[0]?.scan?.invoke(1, false)
            } else {
                onAdd(key, listOfNotNull(front?.let(Uri::parse), page))
                front = null
            }
        }
    }
    again[0] = scanner
    fun start(key: String) {
        target = key; front = null
        if (isCard(key)) toast(context, "Scan the front first")
        scanner.scan(1, false)
    }

    val tiles = buildList {
        PhotoSlot.entries.filter { !it.legacy || it in account.photos }.forEach { add(Tile("s:${it.name}", it.label, it.look(), account.photos[it], it.isCard)) }
        var debit = 1; var credit = 1
        account.cards.forEach { c ->
            val n = if (c.credit) ++credit else ++debit
            add(Tile("x:${c.id}", "${if (c.credit) "Credit" else "Debit"} Card $n", (if (c.credit) PhotoSlot.CREDIT_FRONT else PhotoSlot.DEBIT_FRONT).look(), c.blobId, true))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The last cell is always "Add another card".
        (tiles + null).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { t ->
                    if (t != null) PhotoTile(
                        t, busy = busyKey == t.key, fileName = "${account.bankName} - ${t.label}.jpg",
                        onClick = { if (t.blobId != null) onOpen(t.blobId, t.label) else start(t.key) },
                        onRemove = if (onRemove != null && t.blobId != null) ({ onRemove(t.key) }) else null,
                        modifier = Modifier.weight(1f)
                    ) else Box(Modifier.weight(1f)) {
                        val shape = RoundedCornerShape(16.dp)
                        val tint = scheme.primary
                        Column(
                            Modifier.fillMaxWidth().aspectRatio(1.4f).bounceClick(0.95f) { addMenu = true }
                                .clip(shape).background(tint.copy(alpha = 0.13f)).border(0.5.dp, tint.copy(alpha = 0.35f), shape).padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBubble(Icons.Rounded.AddCard, size = 34.dp, iconSize = 18.dp, background = tint, tint = Color.White)
                                Spacer(Modifier.weight(1f))
                                if (busyKey?.startsWith("n:") == true) CircularProgressIndicator(strokeWidth = 2.dp, color = tint, modifier = Modifier.size(18.dp))
                                else Icon(Icons.Rounded.Add, null, tint = tint, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            Text("Add Another Card", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Debit or credit", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                        }
                        WarmMenu(addMenu, { addMenu = false }) {
                            MenuItem("Debit card", Icons.Rounded.CreditCard) { addMenu = false; start("n:d") }
                            HairLine(Modifier.padding(horizontal = 12.dp))
                            MenuItem("Credit card", Icons.Rounded.Payments) { addMenu = false; start("n:c") }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(30.dp).bounceClick(0.85f, onClick = onClick).background(scheme.surface.copy(alpha = 0.78f), CircleShape).glass(CircleShape, Color.White.copy(alpha = 0.25f))
    ) { Icon(icon, description, tint = scheme.primary, modifier = Modifier.size(15.dp)) }
}

@Composable
private fun PhotoTile(t: Tile, busy: Boolean, fileName: String, onClick: () -> Unit, onRemove: (() -> Unit)?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val look = t.look
    val blobId = t.blobId
    val img = rememberPhoto(blobId, 640)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .aspectRatio(1.4f)
            .bounceClick(0.95f, onClick = onClick)
            .clip(shape)
            .background(look.tint.copy(alpha = 0.13f))
            .border(0.5.dp, look.tint.copy(alpha = 0.35f), shape)
    ) {
        if (img != null && blobId != null) {
            // A card's picture is front above back, so the tile shows the front.
            Image(img, t.label, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                    .padding(start = 10.dp, end = 10.dp, top = 20.dp, bottom = 9.dp)
            ) {
                Icon(look.icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(t.label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.align(Alignment.TopStart).padding(7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniAction(Icons.Rounded.Share, "Share ${t.label}") {
                    Pin.awayOnPurpose = true
                    scope.launch { runCatching { Media.share(context, blobId, fileName, "image/jpeg") }.onFailure { toast(context, "Couldn't share") } }
                }
                MiniAction(Icons.Rounded.ArrowDownward, "Download ${t.label}") {
                    scope.launch { runCatching { Media.saveToPhone(blobId, fileName, "image/jpeg") }.onSuccess { toast(context, it) }.onFailure { toast(context, it.message ?: "Couldn't save") } }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(look.icon, size = 34.dp, iconSize = 18.dp, background = look.tint, tint = Color.White)
                    Spacer(Modifier.weight(1f))
                    if (busy || blobId != null) CircularProgressIndicator(strokeWidth = 2.dp, color = look.tint, modifier = Modifier.size(18.dp))
                    else Icon(Icons.Rounded.DocumentScanner, null, tint = look.tint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(t.label, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(look.hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onRemove != null) Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(28.dp)
                .bounceClick(0.85f, onClick = onRemove)
                .background(scheme.surface, CircleShape)
        ) { Icon(Icons.Rounded.Close, "Remove ${t.label}", tint = scheme.onSurface, modifier = Modifier.size(15.dp)) }
    }
}

/** Branch details for an IFSC from Razorpay's free public IFSC service. */
object Ifsc {
    val PATTERN = Regex("^[A-Z]{4}0[A-Z0-9]{6}$")
    data class Info(val bank: String, val branch: String, val micr: String, val city: String, val address: String)

    /**
     * Turns the bank register's SHOUTING into readable text, for any branch:
     * "NARIMAN PT-MUMBAI" -> "Nariman Pt-Mumbai", "M.G. ROAD (MAIN)" -> "M.G. Road (Main)",
     * "HSR LAYOUT 1ST SECTOR" -> "HSR Layout 1st Sector". Short vowel-less
     * abbreviations stay in capitals.
     */
    fun pretty(raw: String): String = Regex("[A-Za-z0-9']+").replace(raw.trim().replace(Regex(",(?=\\S)"), ", ").replace(Regex("\\s+"), " ")) { m ->
        val w = m.value
        when {
            w.first().isDigit() -> w.lowercase()
            w.length in 2..4 && w.none { it.lowercaseChar() in "aeiou" } && w.any(Char::isLetter) -> w.uppercase()
            else -> w.lowercase().replaceFirstChar(Char::uppercase)
        }
    }

    fun lookup(code: String): Info? = runCatching {
        val c = URL("https://ifsc.razorpay.com/$code").openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        try {
            if (c.responseCode != 200) return null
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            Info(o.optString("BANK"), o.optString("BRANCH"), o.optString("MICR").takeIf { it != "null" }.orEmpty(), o.optString("CITY"),
                o.optString("ADDRESS").takeIf { it != "null" }.orEmpty())
        } finally { c.disconnect() }
    }.getOrNull()
}
