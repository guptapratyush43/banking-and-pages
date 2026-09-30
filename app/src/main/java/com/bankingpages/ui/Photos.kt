package com.bankingpages.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DocumentScanner
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
import androidx.compose.ui.unit.dp
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
fun rememberScanner(onResult: (pages: List<Uri>, pdf: Uri?) -> Unit): Scanner {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val r = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
        if (res.resultCode == Activity.RESULT_OK && r != null) callback(r.pages?.map { it.imageUri }.orEmpty(), r.pdf?.uri)
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

/**
 * Cheque, passbook, debit card and credit card as one row that scrolls sideways,
 * each with its own icon and colour. An empty one opens the scanner; a card takes
 * its front and its back in the same scan and keeps them as one picture.
 */
@Composable
fun PhotoStrip(
    photos: Map<PhotoSlot, String>,
    onOpen: (PhotoSlot) -> Unit,
    onAdd: (PhotoSlot, List<Uri>) -> Unit,
    onRemove: ((PhotoSlot) -> Unit)?,
    busySlot: PhotoSlot? = null,
    edgePadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    var target by rememberSaveable { mutableStateOf<PhotoSlot?>(null) }
    val scanner = rememberScanner { pages, _ -> target?.let { if (pages.isNotEmpty()) onAdd(it, pages) } }
    val slots = PhotoSlot.entries.filter { !it.legacy || it in photos }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = edgePadding)
    ) {
        slots.forEach { slot ->
            PhotoTile(
                slot, photos[slot], busy = busySlot == slot,
                onClick = { if (photos[slot] != null) onOpen(slot) else { target = slot; scanner.scan(if (slot.isCard) 2 else 1, false) } },
                onRemove = if (onRemove != null && photos[slot] != null) ({ onRemove(slot) }) else null
            )
        }
    }
}

@Composable
private fun PhotoTile(slot: PhotoSlot, blobId: String?, busy: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val look = slot.look()
    val img = rememberPhoto(blobId, 640)
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .size(width = 196.dp, height = 132.dp)
            .bounceClick(0.95f, onClick = onClick)
            .clip(shape)
            .background(look.tint.copy(alpha = 0.13f))
            .border(0.5.dp, look.tint.copy(alpha = 0.35f), shape)
    ) {
        if (img != null) {
            // A card's picture is front above back, so the tile shows the front.
            Image(img, slot.label, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 10.dp)
            ) {
                Icon(look.icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(slot.label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(look.icon, size = 40.dp, iconSize = 21.dp, background = look.tint, tint = Color.White)
                    Spacer(Modifier.weight(1f))
                    if (busy || blobId != null) CircularProgressIndicator(strokeWidth = 2.dp, color = look.tint, modifier = Modifier.size(20.dp))
                    else Icon(Icons.Rounded.DocumentScanner, null, tint = look.tint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(slot.label, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1)
                Text(look.hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (onRemove != null) Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp)
                .bounceClick(0.85f, onClick = onRemove)
                .background(scheme.surface, CircleShape)
        ) { Icon(Icons.Rounded.Close, "Remove ${slot.label}", tint = scheme.onSurface, modifier = Modifier.size(15.dp)) }
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
