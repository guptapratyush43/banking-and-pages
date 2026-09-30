package com.bankingpages.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bankingpages.data.Account
import com.bankingpages.data.Pin
import com.bankingpages.data.Vault
import com.bankingpages.files.Media
import com.bankingpages.ui.motion.Motion
import com.bankingpages.ui.motion.bounceClick
import com.bankingpages.ui.motion.entrance
import com.bankingpages.ui.theme.LocalStatusColors
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

fun copy(context: Context, label: String, text: String, sensitive: Boolean = false) {
    val clip = ClipData.newPlainText(label, text)
    // Android 13+ keeps sensitive copies out of the clipboard preview pop-up.
    if (sensitive && Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < 33) toast(context, "$label copied")
}

/**
 * A saved bank, laid out exactly like the form that made it: the same chapters in the
 * same order, the same coloured headers, and the same glass bar to jump between them.
 */
@Composable
fun DetailScreen(a: Account, scroll: ScrollState, seen: MutableSet<Any>, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onOpenPhoto: (blobId: String, label: String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var removeKey by remember { mutableStateOf<String?>(null) }

    val tops = remember { mutableStateMapOf<Int, Int>() }
    val reading by remember {
        derivedStateOf {
            if (scroll.maxValue > 0 && scroll.value >= scroll.maxValue - 12) CHAPTERS.lastIndex
            else tops.filter { it.value <= scroll.value + 420 }.keys.maxOrNull() ?: 0
        }
    }
    var picked by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(scroll) { scroll.interactionSource.interactions.collect { if (it is DragInteraction.Start) picked = null } }
    var pulse by remember { mutableStateOf(0 to -1) }
    val backdrop = rememberLayerBackdrop()

    Column(Modifier.fillMaxSize().background(scheme.background)) {
        TopBar(a.bankName, onBack) {
            RoundIcon(Icons.Rounded.Edit, "Edit", onEdit)
            RoundIcon(Icons.Outlined.DeleteOutline, "Delete bank", { confirmDelete = true }, tint = LocalStatusColors.current.danger)
        }
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().layerBackdrop(backdrop).verticalScroll(scroll).padding(start = 20.dp, end = 20.dp, bottom = 146.dp)) {
                // The bank: logo on the left, its name and the holder right beside it.
                WarmCard(padding = 14.dp, modifier = Modifier.padding(top = 8.dp).entrance(0, "bank", seen)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BankLogo(a.bankId, a.bankName, 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.bankName, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                            if (a.holder.isNotBlank()) Text(a.holder, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                    }
                }

                ChapterCard(0, tops, seen, pulse) {
                    Rows { Field(Icons.Outlined.Badge, "Customer ID / CIF", a.customerId, mono = true) }
                }

                ChapterCard(1, tops, seen, pulse) {
                    Rows {
                        Field(Icons.Outlined.Phone, "Registered mobile", a.mobile)
                        Field(Icons.Outlined.Email, "Registered email", a.email)
                    }
                }

                // One Copy for everything someone needs to pay you.
                ChapterCard(2, tops, seen, pulse, action = {
                    CardAction(Icons.Rounded.ContentCopy, "Copy account details", {
                        copy(context, "Account details", a.shareText())
                        if (Build.VERSION.SDK_INT >= 33) toast(context, "Account details copied")
                    }, Modifier.width(44.dp), filled = false)
                }) {
                    Rows {
                        Field(Icons.Outlined.Person, "Account holder name", a.holder)
                        Field(Icons.Outlined.Numbers, "Account number", a.number, mono = true)
                        Field(Icons.Outlined.QrCode2, "IFSC code", a.ifsc, mono = true)
                        Field(Icons.Outlined.LocationOn, "Branch", a.branchLine)
                        Field(Icons.Outlined.Pin, "MICR", a.micr, mono = true)
                        Field(Icons.Outlined.Category, "Account type", a.type)
                    }
                }

                ChapterCard(3, tops, seen, pulse) {
                    Rows {
                        Field(Icons.Outlined.AccountCircle, "User ID", a.netUserId)
                        Field(Icons.Outlined.Key, "Login password", a.loginPassword, secret = true)
                        Field(Icons.Outlined.Key, "Transaction password", a.txnPassword, secret = true)
                        Field(Icons.Outlined.Key, "Profile password", a.profilePassword, secret = true)
                    }
                }

                ChapterCard(4, tops, seen, pulse) {
                    Rows { a.questions.forEach { Field(Icons.Outlined.HelpOutline, it.question.ifBlank { "Question" }, it.answer, secret = true) } }
                }

                ChapterCard(5, tops, seen, pulse) {
                    PhotoGrid(
                        account = a,
                        onOpen = onOpenPhoto,
                        onAdd = { key, uris ->
                            busyKey = key
                            scope.launch {
                                runCatching { Media.importScan(uris) }
                                    .onSuccess { id -> Vault.saveAccount(a.withPhoto(key, id).copy(updatedAt = System.currentTimeMillis())) }
                                    .onFailure { toast(context, it.message ?: "Couldn't add that photo") }
                                busyKey = null
                            }
                        },
                        onRemove = { key -> removeKey = key },
                        busyKey = busyKey
                    )
                }

                ChapterCard(6, tops, seen, pulse) {
                    Rows { Field(Icons.Outlined.EditNote, "Notes", a.notes) }
                }
            }

            // The chapter bar and Share float over the page, which fades out softly behind them.
            val bg = scheme.background
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(0f to bg.copy(alpha = 0f), 0.3f to bg.copy(alpha = 0.72f), 0.62f to bg, 1f to bg))
                    .padding(start = 20.dp, end = 20.dp, top = 46.dp, bottom = 14.dp)
            ) {
                ChapterBar(backdrop, picked ?: reading, Modifier.padding(bottom = 12.dp)) { i ->
                    picked = i
                    pulse = pulse.first + 1 to i
                    tops[i]?.let { y -> scope.launch { scroll.animateScrollTo((y - 24).coerceAtLeast(0), Motion.smooth()) } }
                }
                PrimaryButton(
                    "Share account details", Icons.Rounded.Share,
                    { Pin.awayOnPurpose = true; Media.shareText(context, a.shareText(), "Share ${a.bankName} details") },
                    Modifier.fillMaxWidth()
                )
            }
        }
    }

    removeKey?.let { key ->
        WarmDialog(
            icon = Icons.Outlined.DeleteOutline, accent = LocalStatusColors.current.danger, title = "Delete this photo?",
            confirmLabel = "Delete", onConfirm = { removeKey = null; Vault.saveAccount(a.withoutPhoto(key).copy(updatedAt = System.currentTimeMillis())) },
            dismissLabel = "Keep", onDismiss = { removeKey = null }
        ) { DialogText("It is removed from ${a.bankName}. Copies you saved to the phone stay.") }
    }

    if (confirmDelete) {
        WarmDialog(
            icon = Icons.Outlined.DeleteOutline, accent = LocalStatusColors.current.danger, title = "Delete ${a.bankName}?",
            confirmLabel = "Delete", onConfirm = { confirmDelete = false; onDelete() },
            dismissLabel = "Keep", onDismiss = { confirmDelete = false }
        ) { DialogText("Its details and photos are removed from this phone. This can't be undone.") }
    }
}

/** Tracks whether a chapter showed anything, so an empty one says so instead of sitting blank. */
private class RowsScope { var shown = 0 }

@Composable
private fun Rows(content: @Composable RowsScope.() -> Unit) {
    val scope = remember { RowsScope() }
    scope.shown = 0
    Column { scope.content() }
    if (scope.shown == 0) Empty()
}

@Composable
private fun Empty() {
    Text("Nothing added yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
}

/**
 * One saved value, shaped like the form's field: its icon, its label, the value. Holding
 * a row copies just that value; a secret shows dots until tapped.
 */
@Composable
private fun RowsScope.Field(icon: ImageVector, label: String, value: String, secret: Boolean = false, mono: Boolean = false) {
    if (value.isBlank()) return
    shown++
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var revealed by remember { mutableStateOf(!secret) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(vertical = 3.dp)
            .fillMaxWidth()
            .bounceClick(0.98f, onLongClick = {
                copy(context, label, value, sensitive = secret)
                if (Build.VERSION.SDK_INT >= 33) toast(context, "$label copied")
            }) { if (secret) revealed = !revealed }
            .background(scheme.surfaceVariant, Pill)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            AnimatedContent(revealed, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "reveal") { s ->
                Text(
                    if (s) value else "•".repeat(value.length.coerceIn(6, 12)),
                    style = MaterialTheme.typography.bodyLarge.let { if (mono) it.copy(fontFamily = FontFamily.Monospace) else it },
                    color = scheme.onSurface
                )
            }
        }
        if (secret) Icon(if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (revealed) "Hide" else "Show", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
