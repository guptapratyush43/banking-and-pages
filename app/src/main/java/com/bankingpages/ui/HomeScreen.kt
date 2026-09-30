package com.bankingpages.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bankingpages.data.Account
import com.bankingpages.data.Doc
import com.bankingpages.data.Pin
import com.bankingpages.data.Vault
import com.bankingpages.files.Media
import com.bankingpages.ui.motion.Motion
import com.bankingpages.ui.motion.bounceClick
import com.bankingpages.ui.motion.entrance
import com.bankingpages.ui.theme.AccentEnd
import com.bankingpages.ui.theme.AccentStart
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RowPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)

/**
 * The vault: the app name, then Documents and Banking, each a titled row of
 * cards that scrolls sideways. Hold a card to drag it to a new place in its row.
 */
@Composable
fun HomeScreen(
    accounts: List<Account>,
    docs: List<Doc>,
    seen: MutableSet<Any>,
    onAddBank: () -> Unit,
    onOpenAccount: (Account) -> Unit,
    onScanDoc: () -> Unit,
    onImportDoc: () -> Unit,
    onOpenDoc: (Doc) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var addMenu by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            // A soft glow of the accent behind the title, so the page isn't flat.
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(AccentStart.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width * 0.92f, 0f), radius = size.width * 0.8f),
                    radius = size.width * 0.8f, center = Offset(size.width * 0.92f, 0f)
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(top = 30.dp, bottom = 130.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp).entrance(0, "head", seen, baseDelay = 0)) {
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US)).uppercase(),
                style = MaterialTheme.typography.labelSmall, color = scheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text("Banking and Pages", style = MaterialTheme.typography.displaySmall, color = scheme.onBackground)
        }

        Spacer(Modifier.height(28.dp))
        SectionHeader("Documents", docs.size, Modifier.padding(horizontal = 20.dp).entrance(1, "docsHead", seen, baseDelay = 0)) {
            Box {
                AddPill("Add", { addMenu = true })
                WarmMenu(addMenu, { addMenu = false }) {
                    MenuItem("Scan a document", Icons.Rounded.DocumentScanner) { addMenu = false; onScanDoc() }
                    HairLine(Modifier.padding(horizontal = 12.dp))
                    MenuItem("Import a PDF or photo", Icons.Rounded.FileOpen) { addMenu = false; onImportDoc() }
                }
            }
        }
        if (docs.isEmpty()) EmptyCard(
            Icons.Rounded.DocumentScanner, "Scan your first document", "Aadhaar, PAN, passport, licence. Tap to scan.",
            onScanDoc, Modifier.entrance(2, "docsEmpty", seen, baseDelay = 0)
        ) else DocsRow(docs, seen, onOpenDoc)

        Spacer(Modifier.height(22.dp))
        SectionHeader("Banking", accounts.size, Modifier.padding(horizontal = 20.dp).entrance(3, "banksHead", seen, baseDelay = 0)) {
            AddPill("Add", onAddBank)
        }
        if (accounts.isEmpty()) EmptyCard(
            Icons.Rounded.AccountBalance, "Add your first bank", "Account number, IFSC, logins, cheque and cards.",
            onAddBank, Modifier.entrance(4, "banksEmpty", seen, baseDelay = 0)
        ) else BanksRow(accounts, seen, onOpenAccount)
    }
}

/** The six dots that show on a card while it is being dragged. */
@Composable
private fun DragBadge(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier, enter = scaleIn(Motion.bouncy()) + fadeIn(), exit = scaleOut() + fadeOut()) {
        Box(Modifier.size(width = 40.dp, height = 24.dp).background(MaterialTheme.colorScheme.primary, Pill), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.DragIndicator, "Drag to rearrange", tint = Color.White, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = 90f })
        }
    }
}

/** A picked-up card grows a little and tilts, and settles back with a bounce. */
@Composable
private fun Modifier.lifted(dragging: Boolean): Modifier {
    val lift by animateFloatAsState(if (dragging) 1f else 0f, Motion.bouncy(), label = "lift")
    return graphicsLayer { val s = 1f + 0.06f * lift; scaleX = s; scaleY = s; rotationZ = -2.5f * lift }
}

@Composable
private fun DocsRow(docs: List<Doc>, seen: MutableSet<Any>, onOpen: (Doc) -> Unit) {
    val haptic = LocalHapticFeedback.current
    // The row keeps its own order while a card is in the air; the vault is written once, on drop.
    var list by remember(docs) { mutableStateOf(docs) }
    val state = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(state) { from, to ->
        list = list.toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LazyRow(state = state, contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(list, key = { _, d -> d.id }) { i, d ->
            ReorderableItem(reorder, key = d.id) { dragging ->
                DocCard(
                    d, dragging, onOpen = { onOpen(d) },
                    modifier = Modifier.entrance(i + 2, d.id, seen, baseDelay = 0).longPressDraggableHandle(
                        onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onDragStopped = { Vault.reorderDocs(list.map { it.id }) }
                    )
                )
            }
        }
    }
}

/** A document: page one as its cover, its name, and Share and Download right beneath it. */
@Composable
private fun DocCard(d: Doc, dragging: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview by produceState<ImageBitmap?>(null, d.blobId) { value = Media.renderPage(d, 0, 420) }
    Column(modifier.width(156.dp).lifted(dragging), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopCenter) {
            WarmCard(padding = 8.dp, onClick = onOpen) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.02f).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceVariant)
                ) {
                    val img = preview
                    if (img != null) Image(img, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                    else Icon(if (d.isPdf) Icons.Rounded.Description else Icons.Rounded.Image, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
                    if (d.password != null) Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).background(scheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Lock, "Password protected", tint = scheme.primary, modifier = Modifier.size(13.dp)) }
                }
                Spacer(Modifier.height(9.dp))
                Text(d.title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                Spacer(Modifier.height(5.dp))
                Tag(if (d.isPdf) "PDF" else "Photo", Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardAction(Icons.Rounded.Share, "Share ${d.title}", {
                        Pin.awayOnPurpose = true
                        scope.launch { runCatching { Media.share(context, d.blobId, d.fileName, d.mime) }.onFailure { toast(context, "Couldn't share") } }
                    }, Modifier.weight(1f))
                    CardAction(Icons.Rounded.ArrowDownward, "Download ${d.title}", filled = false, onClick = {
                        scope.launch { runCatching { Media.saveToPhone(d.blobId, d.fileName, d.mime) }.onSuccess { toast(context, it) }.onFailure { toast(context, it.message ?: "Couldn't save") } }
                    }, modifier = Modifier.weight(1f))
                }
            }
            DragBadge(dragging, Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
private fun BanksRow(accounts: List<Account>, seen: MutableSet<Any>, onOpen: (Account) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var list by remember(accounts) { mutableStateOf(accounts) }
    val state = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(state) { from, to ->
        list = list.toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LazyRow(state = state, contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(list, key = { _, a -> a.id }) { i, a ->
            ReorderableItem(reorder, key = a.id) { dragging ->
                BankCard(
                    a, dragging, onOpen = { onOpen(a) },
                    modifier = Modifier.entrance(i + 4, a.id, seen, baseDelay = 0).longPressDraggableHandle(
                        onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onDragStopped = { Vault.reorderAccounts(list.map { it.id }) }
                    )
                )
            }
        }
    }
}

/** A bank: its logo and a hint of what's inside, with "share account details only" right beneath it. */
@Composable
private fun BankCard(a: Account, dragging: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(modifier.width(184.dp).lifted(dragging)) {
        Box(contentAlignment = Alignment.TopCenter) {
            WarmCard(padding = 16.dp, onClick = onOpen) {
                Row(verticalAlignment = Alignment.Top) {
                    BankLogo(a.bankId, a.bankName, 56.dp)
                    Spacer(Modifier.weight(1f))
                    if (a.allBlobs.isNotEmpty()) Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(scheme.surfaceVariant, Pill).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${a.allBlobs.size}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(a.bankName, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    listOf(a.maskedNumber, a.holder.substringBefore(' ')).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "No account number yet" },
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Tag(a.type.ifBlank { "Account" })
                Spacer(Modifier.height(12.dp))
                CardAction(Icons.Rounded.Share, "Share account details only", {
                    Pin.awayOnPurpose = true
                    Media.shareText(context, a.shareText(), "Share ${a.bankName} details")
                }, Modifier.fillMaxWidth(), label = "Share A/C details")
            }
            DragBadge(dragging, Modifier.padding(top = 6.dp))
        }
    }
}

/** What a row shows before anything is in it: one wide, tappable invitation. */
@Composable
private fun EmptyCard(icon: ImageVector, title: String, body: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(RowPadding)
            .fillMaxWidth()
            .bounceClick(0.97f, onClick = onClick)
            .clip(shape)
            .background(Brush.linearGradient(listOf(AccentStart.copy(alpha = 0.14f), AccentEnd.copy(alpha = 0.07f))))
            .border(1.dp, scheme.primary.copy(alpha = 0.25f), shape)
            .padding(18.dp)
    ) {
        IconBubble(icon, size = 52.dp, iconSize = 26.dp, background = scheme.primary, tint = Color.White)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            RowBody(body)
        }
    }
}
