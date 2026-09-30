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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bankingpages.data.Account
import com.bankingpages.data.PhotoSlot
import com.bankingpages.data.Pin
import com.bankingpages.data.Vault
import com.bankingpages.files.Media
import com.bankingpages.ui.motion.bounceClick
import com.bankingpages.ui.motion.entrance
import com.bankingpages.ui.motion.popIn
import com.bankingpages.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch

fun copy(context: Context, label: String, text: String, sensitive: Boolean = false) {
    val clip = ClipData.newPlainText(label, text)
    // Android 13+ keeps sensitive copies out of the clipboard preview pop-up.
    if (sensitive && Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < 33) toast(context, "$label copied")
}

@Composable
fun DetailScreen(a: Account, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onOpenPhoto: (PhotoSlot) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val seen = remember { mutableSetOf<Any>() }
    var confirmDelete by remember { mutableStateOf(false) }
    var busySlot by remember { mutableStateOf<PhotoSlot?>(null) }

    Column(Modifier.fillMaxSize().background(scheme.background)) {
        TopBar(a.bankName, onBack) {
            RoundIcon(Icons.Rounded.Edit, "Edit", onEdit)
            RoundIcon(Icons.Outlined.DeleteOutline, "Delete bank", { confirmDelete = true }, tint = LocalStatusColors.current.danger)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 36.dp)) {
            // Hero: the logo springs in, the rest rises after it.
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
                BankLogo(a.bankId, a.bankName, 92.dp, Modifier.popIn())
                Spacer(Modifier.height(14.dp))
                Text(a.bankName, style = MaterialTheme.typography.headlineMedium, color = scheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.entrance(0, "name", seen))
                if (a.holder.isNotBlank()) Text(a.holder, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, modifier = Modifier.entrance(1, "holder", seen))
                Spacer(Modifier.height(10.dp))
                Tag(a.type.ifBlank { "Account" }, Modifier.entrance(1, "type", seen))
            }

            Column(Modifier.padding(horizontal = 20.dp)) {
                // The details someone needs to pay you, with one Copy for the lot.
                Spacer(Modifier.height(24.dp))
                WarmCard(padding = 8.dp, modifier = Modifier.entrance(2, "account", seen)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp)) {
                        Text("Account details", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
                        RoundIcon(Icons.Rounded.ContentCopy, "Copy account details", {
                            copy(context, "Account details", a.shareText())
                            if (Build.VERSION.SDK_INT >= 33) toast(context, "Account details copied")
                        }, tint = scheme.primary, size = 40.dp)
                    }
                    Field("Account holder", a.holder)
                    Field("Account number", a.number, mono = true)
                    Field("IFSC code", a.ifsc, mono = true)
                    Field("Branch", a.branch)
                    Field("Branch address", a.branchAddress)
                    Field("Registered mobile", a.mobile)
                    Field("Registered email", a.email)
                    Field("Customer ID / CIF", a.customerId, mono = true)
                    Field("MICR", a.micr, mono = true)
                    Field("UPI ID", a.upi)
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    "Share account details", Icons.Rounded.IosShare,
                    { Pin.awayOnPurpose = true; Media.shareText(context, a.shareText(), "Share ${a.bankName} details") },
                    Modifier.fillMaxWidth().entrance(3, "share", seen)
                )
                Footnote("Copy and Share send the name, account number, IFSC, branch, address and phone. Never passwords.", Modifier.padding(top = 10.dp).entrance(3, "sharenote", seen))
            }

            Spacer(Modifier.height(26.dp))
            Column(Modifier.entrance(4, "photos", seen)) {
                SectionLabel("Cheque, passbook and cards", Modifier.padding(start = 20.dp))
                PhotoStrip(
                    photos = a.photos,
                    onOpen = onOpenPhoto,
                    onAdd = { slot, uris ->
                        busySlot = slot
                        scope.launch {
                            runCatching { Media.importScan(uris) }
                                .onSuccess { id -> Vault.saveAccount(a.copy(photos = a.photos + (slot to id), updatedAt = System.currentTimeMillis())) }
                                .onFailure { toast(context, it.message ?: "Couldn't add that photo") }
                            busySlot = null
                        }
                    },
                    onRemove = null,
                    busySlot = busySlot,
                    edgePadding = 20.dp
                )
            }

            Column(Modifier.padding(horizontal = 20.dp)) {
                if (listOf(a.netUserId, a.loginPassword, a.txnPassword, a.profilePassword).any { it.isNotBlank() }) Block("Net banking", 5, seen) {
                    Field("User ID", a.netUserId)
                    Field("Login password", a.loginPassword, secret = true)
                    Field("Transaction password", a.txnPassword, secret = true)
                    Field("Profile password", a.profilePassword, secret = true)
                }

                if (a.questions.isNotEmpty()) Block("Security questions", 6, seen) {
                    a.questions.forEach { Field(it.question.ifBlank { "Question" }, it.answer, secret = true) }
                }

                if (a.notes.isNotBlank()) Block("Notes", 7, seen) {
                    Text(a.notes, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }

    if (confirmDelete) {
        WarmDialog(
            icon = Icons.Outlined.DeleteOutline, accent = LocalStatusColors.current.danger, title = "Delete ${a.bankName}?",
            confirmLabel = "Delete", onConfirm = { confirmDelete = false; onDelete() },
            dismissLabel = "Keep", onDismiss = { confirmDelete = false }
        ) { DialogText("Its details and photos are removed from this phone. This can't be undone.") }
    }
}

@Composable
private fun Block(title: String, index: Int, seen: MutableSet<Any>, content: @Composable () -> Unit) {
    Spacer(Modifier.height(26.dp))
    Column(Modifier.entrance(index, title, seen)) {
        SectionLabel(title)
        WarmCard(padding = 8.dp) { content() }
    }
}

/**
 * A label and its value. No button per row: the card's one Copy covers the account
 * details, and holding any row copies just that value. Secrets get a reveal eye.
 */
@Composable
private fun Field(label: String, value: String, secret: Boolean = false, mono: Boolean = false) {
    if (value.isBlank()) return
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var shown by remember { mutableStateOf(!secret) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(0.98f, onLongClick = {
                copy(context, label, value, sensitive = secret)
                if (Build.VERSION.SDK_INT >= 33) toast(context, "$label copied")
            }) { if (secret) shown = !shown }
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            AnimatedContent(shown, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "reveal") { s ->
                Text(
                    if (s) value else "•".repeat(value.length.coerceIn(6, 12)),
                    style = MaterialTheme.typography.bodyLarge.let { if (mono) it.copy(fontFamily = FontFamily.Monospace) else it },
                    color = scheme.onSurface
                )
            }
        }
        if (secret) Box(Modifier.padding(end = 6.dp)) {
            Icon(if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (shown) "Hide" else "Show", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
