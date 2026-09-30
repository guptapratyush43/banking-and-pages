package com.bankingpages.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bankingpages.AppScope
import com.bankingpages.data.Doc
import com.bankingpages.data.DocKind
import com.bankingpages.data.Pin
import com.bankingpages.data.Vault
import com.bankingpages.files.Media
import com.bankingpages.ui.motion.Motion
import com.bankingpages.ui.motion.entrance
import com.bankingpages.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch

/** Pinch to zoom, drag to pan, double-tap to jump in or out, all springing back into bounds. */
@Composable
fun ZoomImage(bitmap: ImageBitmap, description: String, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val ox = remember { Animatable(0f) }
    val oy = remember { Animatable(0f) }
    BoxWithConstraints(modifier.fillMaxSize().clip(RoundedCornerShape(0.dp)), contentAlignment = Alignment.Center) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        fun clampTo(s: Float) {
            val maxX = (w * (s - 1f)) / 2f; val maxY = (h * (s - 1f)) / 2f
            scope.launch { ox.animateTo(ox.value.coerceIn(-maxX, maxX), Motion.smooth()) }
            scope.launch { oy.animateTo(oy.value.coerceIn(-maxY, maxY), Motion.smooth()) }
        }
        Image(
            bitmap, description, contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { tap ->
                        scope.launch {
                            if (scale.value > 1.05f) {
                                launch { scale.animateTo(1f, Motion.bouncy()) }
                                launch { ox.animateTo(0f, Motion.smooth()) }
                                oy.animateTo(0f, Motion.smooth())
                            } else {
                                val target = 2.6f
                                launch { ox.animateTo((w / 2 - tap.x) * (target - 1), Motion.smooth()) }
                                launch { oy.animateTo((h / 2 - tap.y) * (target - 1), Motion.smooth()) }
                                scale.animateTo(target, Motion.bouncy())
                                clampTo(target)
                            }
                        }
                    })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scope.launch {
                            val s = (scale.value * zoom).coerceIn(1f, 6f)
                            scale.snapTo(s)
                            ox.snapTo(ox.value + pan.x); oy.snapTo(oy.value + pan.y)
                            if (s <= 1.01f) { ox.snapTo(0f); oy.snapTo(0f) }
                            else {
                                val maxX = (w * (s - 1f)) / 2f; val maxY = (h * (s - 1f)) / 2f
                                ox.snapTo(ox.value.coerceIn(-maxX, maxX)); oy.snapTo(oy.value.coerceIn(-maxY, maxY))
                            }
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale.value; scaleY = scale.value
                    translationX = ox.value; translationY = oy.value
                }
        )
    }
}

@Composable
private fun ActionBar(onSave: () -> Unit, onShare: () -> Unit, saveLabel: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().background(scheme.background).padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        SecondaryButton(saveLabel, Icons.Rounded.ArrowDownward, onSave, Modifier.weight(1f))
        PrimaryButton("Share", Icons.Rounded.Share, onShare, Modifier.weight(1f))
    }
}

/** A photo (cheque, passbook, card) full screen, with save-to-gallery and share. */
@Composable
fun PhotoViewer(title: String, subtitle: String, blobId: String, fileName: String, onBack: () -> Unit, onReplace: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val img = rememberPhoto(blobId, 2600)
    Column(Modifier.fillMaxSize().background(scheme.background)) {
        TopBar(title, onBack, subtitle = subtitle)
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)).background(scheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (img != null) ZoomImage(img, title) else CircularProgressIndicator(strokeWidth = 2.dp, color = scheme.primary, modifier = Modifier.size(26.dp))
        }
        ActionBar(
            saveLabel = "Save to gallery",
            onSave = { scope.launch { runCatching { Media.saveToPhone(blobId, fileName, "image/jpeg") }.onSuccess { toast(context, it) }.onFailure { toast(context, it.message ?: "Couldn't save") } } },
            onShare = { Pin.awayOnPurpose = true; scope.launch { runCatching { Media.share(context, blobId, fileName, "image/jpeg") }.onFailure { toast(context, "Couldn't share") } } }
        )
    }
}

/** A PDF or image document, every page stacked; tap a page to zoom into it. */
@Composable
fun DocViewer(doc: Doc, onBack: () -> Unit, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pages by produceState(-1, doc.id) { value = if (doc.isPdf) Media.pageCount(doc) else 1 }
    var zoomPage by rememberSaveable { mutableStateOf<Int?>(null) }
    val seen = remember { mutableSetOf<Any>() }
    var confirmDelete by remember { mutableStateOf(false) }
    val widthPx = remember { Media.viewerWidth }

    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(doc.title, onBack, subtitle = if (doc.isPdf) "PDF · ${if (pages > 0) "$pages ${if (pages == 1) "page" else "pages"}" else "…"}" else doc.kind.label) {
                RoundIcon(Icons.Outlined.DeleteOutline, "Delete", { confirmDelete = true }, tint = LocalStatusColors.current.danger)
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                // A short document sits in the middle of the screen rather than hugging the top.
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                modifier = Modifier.weight(1f)
            ) {
                doc.password?.let { pw ->
                    item(key = "pw") {
                        WarmCard(padding = 14.dp, modifier = Modifier.entrance(0, "pw", seen)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBubble(Icons.Outlined.Lock, size = 38.dp, iconSize = 18.dp)
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Password protected", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                                    RowBody("Shared copies ask for it too")
                                }
                                SmallButton("Copy password", null, { copy(context, "PDF password", pw, sensitive = true) })
                            }
                        }
                    }
                }
                items(count = pages.coerceAtLeast(0), key = { "p$it" }) { i ->
                    val img by produceState<ImageBitmap?>(null, doc.id, i) { value = Media.renderPage(doc, i, widthPx) }
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .entrance(i + 1, "p$i", seen)
                            .clip(shape)
                            .background(androidx.compose.ui.graphics.Color.White, shape)
                            .border(0.5.dp, scheme.outline, shape)
                            .then(if (img != null) Modifier.aspectRatio(img!!.width.toFloat() / img!!.height) else Modifier.aspectRatio(0.707f))
                            .pointerInput(i) { detectTapGestures(onTap = { zoomPage = i }) }
                    ) {
                        img?.let { Image(it, "Page ${i + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                            ?: CircularProgressIndicator(strokeWidth = 2.dp, color = scheme.primary, modifier = Modifier.size(22.dp))
                    }
                }
            }
            ActionBar(
                saveLabel = "Save to phone",
                onSave = { scope.launch { runCatching { Media.saveToPhone(doc.blobId, doc.fileName, doc.mime) }.onSuccess { toast(context, it) }.onFailure { toast(context, it.message ?: "Couldn't save") } } },
                onShare = { Pin.awayOnPurpose = true; scope.launch { runCatching { Media.share(context, doc.blobId, doc.fileName, doc.mime) }.onFailure { toast(context, "Couldn't share") } } }
            )
        }

        if (confirmDelete) WarmDialog(
            icon = Icons.Outlined.DeleteOutline, accent = LocalStatusColors.current.danger, title = "Delete ${doc.title}?",
            confirmLabel = "Delete", onConfirm = { confirmDelete = false; onDelete() },
            dismissLabel = "Keep", onDismiss = { confirmDelete = false }
        ) { DialogText("The file is removed from Banking and Pages. Copies you saved to the phone stay.") }

        zoomPage?.let { p ->
            androidx.activity.compose.BackHandler { zoomPage = null }
            val big by produceState<ImageBitmap?>(null, doc.id, p) { value = Media.renderPage(doc, p, 2200) }
            Column(Modifier.fillMaxSize().background(scheme.background)) {
                TopBar("Page ${p + 1}", { zoomPage = null }, subtitle = doc.title)
                Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)).background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
                    big?.let { ZoomImage(it, "Page ${p + 1}") } ?: CircularProgressIndicator(strokeWidth = 2.dp, color = scheme.primary, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

/** Starts adding a document: [scan] opens Google's scanner, [import] the file picker. */
class DocAdder(val scan: () -> Unit, val import: () -> Unit)

private class NewFile(val blobId: String, val mime: String, val password: String?)

/**
 * Adding a document. Scanning takes the front, comes back to a full screen that asks
 * for the back, and lays both sides on one A4 page like a photocopy. A locked PDF (as
 * every e-Aadhaar is) asks for its password. Last of all the document gets its name and tags.
 */
@Composable
fun rememberDocAdder(onAdded: (Doc) -> Unit): DocAdder {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var ready by remember { mutableStateOf<NewFile?>(null) }
    var locked by remember { mutableStateOf<ByteArray?>(null) }
    // The scanned front, waiting for its back.
    var front by rememberSaveable { mutableStateOf<String?>(null) }
    var kind by rememberSaveable { mutableStateOf(DocKind.AADHAAR) }
    var title by rememberSaveable { mutableStateOf("") }
    // True once the custom-name field is in use: no kind chip stays lit then.
    var custom by rememberSaveable { mutableStateOf(false) }
    var tags by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    // True while a scan, photo or PDF is being turned into a document.
    var preparing by remember { mutableStateOf(false) }

    suspend fun takePdf(bytes: ByteArray) {
        when (Media.checkPdf(bytes, null)) {
            is Media.PdfCheck.Ok -> ready = NewFile(Vault.putBlob(bytes), "application/pdf", null)
            Media.PdfCheck.NeedsPassword -> { password = ""; wrong = false; locked = bytes }
            else -> toast(context, "That PDF couldn't be opened")
        }
    }
    /** One picture stays a photo; two become one A4 page, front beside back. */
    suspend fun takeSides(sides: List<Uri>) {
        ready = if (sides.size == 1) NewFile(Media.importPhoto(sides[0]), "image/jpeg", null)
        else NewFile(Vault.putBlob(Media.idSheet(sides.take(2))), "application/pdf", null)
    }
    fun take(block: suspend () -> Unit) {
        preparing = true
        scope.launch { runCatching { block() }.onFailure { toast(context, it.message ?: "Couldn't add that file") }; preparing = false }
    }

    // The scanner is never reopened from inside its own result; the full screen below does it on a tap.
    val scanner = rememberScanner { pages, _ ->
        val page = pages.firstOrNull() ?: return@rememberScanner
        val f = front
        if (f == null) front = page.toString()
        else { front = null; take { takeSides(listOf(Uri.parse(f), page)) } }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        take {
            val images = uris.filter { Media.mimeOf(it).startsWith("image/") }
            if (images.isNotEmpty()) takeSides(images) else takePdf(Media.readUri(uris[0]))
        }
    }

    if (preparing) Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.background(scheme.surface, RoundedCornerShape(22.dp)).padding(horizontal = 34.dp, vertical = 28.dp)
        ) {
            CircularProgressIndicator(strokeWidth = 3.dp, color = scheme.primary, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(14.dp))
            Text("Preparing your document…", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        }
    }

    front?.let { f ->
        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().background(scheme.background).padding(28.dp)
            ) {
                IconBubble(Icons.Outlined.NoteAdd, size = 96.dp, iconSize = 44.dp)
                Spacer(Modifier.height(26.dp))
                Text("Front saved", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                Spacer(Modifier.height(6.dp))
                Text("Now scan the back", style = MaterialTheme.typography.displaySmall, color = scheme.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text("Both sides are placed side by side on one A4 page, like a photocopy.", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(34.dp))
                PrimaryButton("Scan the back", Icons.Outlined.NoteAdd, { scanner.scan(1, false) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                SecondaryButton("It has one side only", null, { front = null; take { takeSides(listOf(Uri.parse(f))) } }, Modifier.fillMaxWidth(), tint = scheme.onSurface)
            }
        }
    }

    locked?.let { bytes ->
        WarmDialog(
            icon = Icons.Outlined.Lock, accent = scheme.primary, title = "This PDF is locked",
            confirmLabel = "Unlock", confirmEnabled = password.isNotEmpty() && !working,
            onConfirm = {
                working = true
                scope.launch {
                    if (Media.checkPdf(bytes, password) is Media.PdfCheck.Ok) { ready = NewFile(Vault.putBlob(bytes), "application/pdf", password); locked = null }
                    else wrong = true
                    working = false
                }
            },
            dismissLabel = "Cancel", onDismiss = { locked = null }
        ) {
            DialogText("For e-Aadhaar it's the first 4 letters of your name in CAPITALS, then your birth year. For example SURE1990.")
            Spacer(Modifier.height(12.dp))
            WarmField(password, { password = it; wrong = false }, "PDF password",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Password), visual = PasswordVisualTransformation(),
                isError = wrong, supporting = if (wrong) "That password didn't open it" else "Saved with the file so it opens here")
        }
    }

    ready?.let { file ->
        fun reset() { ready = null; title = ""; tags = ""; custom = false }
        WarmDialog(
            icon = Icons.Outlined.NoteAdd, accent = scheme.primary, title = "Name this document",
            confirmLabel = "Save", confirmEnabled = !custom || title.isNotBlank(),
            onConfirm = {
                val name = if (custom) title.trim() else kind.label
                val labels = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                val doc = Doc(Vault.newId(), if (custom) DocKind.OTHER else kind, name, file.blobId, file.mime,
                    "$name.${if (file.mime == "application/pdf") "pdf" else "jpg"}", file.password, labels)
                Vault.saveDoc(doc)
                // Get its preview and viewer page ready in the background, so opening it is instant.
                AppScope.launch {
                    Media.renderPage(doc, 0, 420)
                    if (doc.isPdf) { Media.pageCount(doc); Media.renderPage(doc, 0, Media.viewerWidth) }
                }
                reset()
                onAdded(doc)
            },
            dismissLabel = "Discard", onDismiss = { Vault.deleteBlob(file.blobId); reset() }
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Tapping the custom-name field switches every chip off; tapping a chip switches back.
                DocKind.entries.filter { it != DocKind.OTHER }.forEach { k -> Chip(k.label, !custom && kind == k) { kind = k; custom = false; title = ""; focus.clearFocus() } }
            }
            Spacer(Modifier.height(12.dp))
            WarmField(title, { title = it; custom = true }, "Custom name", keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                onFocus = { custom = true })
            Spacer(Modifier.height(8.dp))
            WarmField(tags, { tags = it }, "Tags", keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                supporting = "Whose it is, e.g. Mummy, Papa. Separate with commas.")
        }
    }

    return remember {
        DocAdder(
            scan = { front = null; scanner.scan(1, false) },
            import = { Pin.awayOnPurpose = true; picker.launch(arrayOf("application/pdf", "image/*")) }
        )
    }
}
