package com.bankingpages.ui

import androidx.compose.material.icons.outlined.Badge
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.VisibilityThreshold
import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bankingpages.AppScope
import com.bankingpages.backup.BackupManager
import com.bankingpages.backup.DriveAuth
import com.bankingpages.backup.WrongPasswordException
import com.bankingpages.data.AppSettings
import com.bankingpages.data.Pin
import com.bankingpages.ui.motion.Motion
import com.bankingpages.ui.motion.bounceClick
import com.bankingpages.ui.motion.entrance
import com.bankingpages.ui.theme.LocalStatusColors
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// --- Fingerprint -----------------------------------------------------------

fun fingerprintStatus(context: Context): Int = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

/** Shows the system fingerprint sheet; [onOk] runs only on a real match. */
fun askFingerprint(activity: FragmentActivity, title: String, onOk: () -> Unit) {
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
    })
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Banking Pages")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    )
}

// --- Settings --------------------------------------------------------------

@Composable
fun SettingsScreen(onOverlay: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val warning = LocalStatusColors.current.warning
    val settings by AppSettings.state.collectAsStateWithLifecycle()
    var changePin by rememberSaveable { mutableStateOf(false) }
    var recoveryFlow by rememberSaveable { mutableStateOf(false) }
    // The floating bar steps aside while a PIN pad is up.
    androidx.compose.runtime.LaunchedEffect(changePin, recoveryFlow) { onOverlay(changePin || recoveryFlow) }
    val hasRecovery = remember(recoveryFlow) { com.bankingpages.data.Recovery.isSet }
    var fingerprintWarning by remember { mutableStateOf(false) }
    val seen = remember { mutableSetOf<Any>() }
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }

    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Profile", null, subtitle = "Backup, security and updates")
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 130.dp)) {
                Column(Modifier.entrance(0, "backup", seen)) {
                    SectionLabel("Backup")
                    BackupSection()
                }

                Spacer(Modifier.height(24.dp))
                Column(Modifier.entrance(1, "sec", seen)) {
                    SectionLabel("Security")
                    WarmCard {
                        SettingRow(Icons.Outlined.Password, "Change PIN", "The 4 digits you open the app with", onClick = { changePin = true })
                        Divider()
                        SettingRow(
                            Icons.Outlined.Badge, "Aadhaar for PIN reset",
                            if (hasRecovery) "Added. Lets you set a new PIN if you forget it" else "Not added. Add it so a forgotten PIN can be reset",
                            onClick = { recoveryFlow = true }
                        )
                        Divider()
                        SettingRow(Icons.Outlined.Fingerprint, "Unlock with fingerprint", "Quicker than typing your PIN") {
                            WarmSwitch(settings.fingerprint, { on ->
                                if (!on) AppSettings.setFingerprint(false)
                                else when (fingerprintStatus(context)) {
                                    BiometricManager.BIOMETRIC_SUCCESS -> fingerprintWarning = true
                                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> toast(context, "Add a fingerprint in your phone's settings first")
                                    else -> toast(context, "This phone has no fingerprint sensor Banking Pages can use")
                                }
                            })
                        }
                        AnimatedVisibility(settings.fingerprint, enter = expandVertically(Motion.smooth()) + fadeIn(), exit = shrinkVertically(Motion.smooth()) + fadeOut()) {
                            FingerprintNote()
                        }
                        Divider()
                        SettingRow(Icons.Outlined.ScreenLockPortrait, "Hide from screenshots", "Blocks screenshots and blurs the app in recent apps") {
                            WarmSwitch(settings.secureScreen, AppSettings::setSecureScreen)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Column(Modifier.entrance(2, "update", seen)) {
                    SectionLabel("App")
                    WarmCard {
                        var checking by remember { mutableStateOf(false) }
                        SettingRow(Icons.Outlined.SystemUpdate, "Check for updates", if (checking) "Checking…" else "You're on v$version", onClick = {
                            if (!checking) {
                                checking = true
                                AppScope.launch {
                                    try { if (com.bankingpages.update.UpdateManager.checkNow() == null) toast(context, "You're on the latest version") }
                                    catch (e: Exception) { toast(context, "Couldn't check right now. Try again in a bit.") }
                                    finally { checking = false }
                                }
                            }
                        })
                        Divider()
                        SettingRow(Icons.AutoMirrored.Outlined.Logout, "Log out", "Locks the app now. Your PIN opens it again", onClick = { Pin.lock() })
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().entrance(3, "about", seen).padding(horizontal = 4.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Banking Pages $version · everything is encrypted on this phone", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }

        AnimatedVisibility(
            changePin,
            enter = slideInHorizontally(Motion.push(androidx.compose.ui.unit.IntOffset.VisibilityThreshold)) { it },
            exit = slideOutHorizontally(Motion.push(androidx.compose.ui.unit.IntOffset.VisibilityThreshold)) { it }
        ) {
            BackHandler { changePin = false }
            ChangePinFlow { ok -> changePin = false; if (ok) toast(context, "PIN changed") }
        }

        AnimatedVisibility(
            recoveryFlow,
            enter = slideInHorizontally(Motion.push(androidx.compose.ui.unit.IntOffset.VisibilityThreshold)) { it },
            exit = slideOutHorizontally(Motion.push(androidx.compose.ui.unit.IntOffset.VisibilityThreshold)) { it }
        ) {
            BackHandler { recoveryFlow = false }
            RecoveryFlow { saved -> recoveryFlow = false; if (saved) toast(context, if (hasRecovery) "Aadhaar number updated for PIN reset" else "Aadhaar number saved for PIN reset") }
        }
    }

    if (fingerprintWarning) {
        WarmDialog(
            icon = Icons.Outlined.WarningAmber, accent = warning, title = "A quick heads-up",
            confirmLabel = "Turn on", onConfirm = {
                fingerprintWarning = false
                (context as? FragmentActivity)?.let { act ->
                    askFingerprint(act, "Confirm your fingerprint") { AppSettings.setFingerprint(true); toast(context, "Fingerprint unlock is on") }
                }
            },
            dismissLabel = "PIN only", onDismiss = { fingerprintWarning = false }
        ) {
            DialogText("Fingerprint unlock is handy, but it's less private than your PIN. While you're asleep or not looking, someone could press your finger to the phone and open Banking Pages. A PIN in your head can't be borrowed that way.")
        }
    }
}

@Composable
private fun FingerprintNote() {
    val warning = LocalStatusColors.current.warning
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(warning.copy(alpha = 0.11f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Icon(Icons.Outlined.WarningAmber, null, tint = warning, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "Handy, but less private than your PIN: someone could use your finger while you sleep. Turn it off if that worries you.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun Divider() { Spacer(Modifier.height(14.dp)); HairLine(); Spacer(Modifier.height(14.dp)) }

@Composable
private fun SettingRow(icon: ImageVector, title: String, body: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.bounceClick(0.97f, onClick = onClick) else Modifier)
    ) {
        IconBubble(icon, size = 40.dp, iconSize = 20.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            RowBody(body)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/** Current PIN, then the new one twice, on the same round-key pad as the lock screen. */
@Composable
private fun ChangePinFlow(onDone: (Boolean) -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var fresh by remember { mutableStateOf("") }
    androidx.compose.animation.AnimatedContent(
        step,
        transitionSpec = {
            val spec = Motion.push(androidx.compose.ui.unit.IntOffset.VisibilityThreshold)
            (slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 4 }).apply { targetContentZIndex = 1f }
        },
        label = "changePin"
    ) { s ->
        when (s) {
            0 -> PinPanel(Icons.Outlined.Lock, "Current PIN", "Enter the PIN you use now", onComplete = { pin ->
                val ok = withContext(Dispatchers.Default) { Pin.verify(pin) }
                if (ok) step = 1
                ok
            }, footer = { Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.bounceClick(0.92f) { onDone(false) }.padding(12.dp)) })
            1 -> PinPanel(Icons.Outlined.Lock, "New PIN", "Choose 4 new digits", onComplete = { pin -> fresh = pin; step = 2; true })
            else -> PinPanel(Icons.Outlined.Lock, "Confirm new PIN", "Enter it once more", onComplete = { pin ->
                if (pin != fresh) { toast(context, "PINs didn't match. Try again"); false }
                else { withContext(Dispatchers.Default) { Pin.set(pin) }; onDone(true); true }
            })
        }
    }
}

// --- Google Drive backup --------------------------------------------------

private fun whenText(ms: Long) = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.US).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

@Composable
fun BackupSection() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    val state by BackupManager.state.collectAsStateWithLifecycle()
    var offer by remember { mutableStateOf<BackupManager.RemoteInfo?>(null) }
    var restorePin by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    fun afterSignIn(token: String) {
        AppScope.launch {
            try {
                val remote = BackupManager.completeSignIn(token)
                if (remote != null) offer = remote
                else { BackupManager.backupNow(); toast(context, "Signed in. First backup done") }
            } catch (e: Throwable) {
                toast(context, BackupManager.friendly(e))
            }
        }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val token = if (res.resultCode == Activity.RESULT_OK) {
            runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data).accessToken }.getOrNull()
        } else null
        if (token != null) afterSignIn(token) else toast(context, "Sign-in cancelled")
    }

    fun signIn() {
        AppScope.launch {
            try {
                val result = DriveAuth.authorize(context)
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    Pin.awayOnPurpose = true
                    consent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else result.accessToken?.let(::afterSignIn) ?: toast(context, "Sign-in failed")
            } catch (e: ApiException) {
                toast(context, if (e.statusCode == 10) "Google sign-in isn't set up for this build yet (code 10)." else "Sign-in failed (code ${e.statusCode}).")
            } catch (e: Throwable) {
                toast(context, e.message ?: "Sign-in failed")
            }
        }
    }

    WarmCard {
        if (state.email == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Outlined.CloudUpload, size = 40.dp, iconSize = 20.dp)
                Spacer(Modifier.width(14.dp))
                Text("Google Drive backup", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Your bank details, photos and documents are saved to the Google Drive of the account you sign in with.",
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Sign in with Google", null, onClick = { signIn() }, modifier = Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Outlined.CloudDone, size = 40.dp, iconSize = 20.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.email!!, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.busy ?: if (state.lastBackupAt > 0) "Last backup · ${whenText(state.lastBackupAt)}" else "No backup yet",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                if (state.busy != null) CircularProgressIndicator(strokeWidth = 2.dp, color = scheme.primary, modifier = Modifier.size(18.dp))
                else Box {
                    RoundIcon(Icons.Outlined.MoreVert, "More", { menu = true }, size = 38.dp)
                    WarmMenu(menu, { menu = false }) {
                        MenuItem("Delete backup", Icons.Outlined.DeleteOutline, status.danger) { menu = false; confirmDelete = true }
                        MenuItem("Sign out", Icons.AutoMirrored.Outlined.Logout) { menu = false; BackupManager.signOut(); toast(context, "Signed out. Your backup stays in Drive") }
                    }
                }
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = status.danger)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Back up now", Icons.Outlined.CloudUpload, {
                    if (state.busy != null) toast(context, "Backup/Restore process is already going on")
                    else AppScope.launch { runCatching { BackupManager.backupNow() }.onSuccess { toast(context, "Backed up") }.onFailure { toast(context, BackupManager.friendly(it)) } }
                }, Modifier.weight(1f))
                SecondaryButton("Restore", Icons.Outlined.CloudDownload, {
                    if (state.busy != null) toast(context, "Backup/Restore process is already going on")
                    else AppScope.launch {
                        runCatching { BackupManager.remoteInfo() }
                            .onSuccess { if (it == null) toast(context, "No backup in Drive yet") else offer = it }
                            .onFailure { toast(context, BackupManager.friendly(it)) }
                    }
                }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Footnote("Every change is backed up instantly.")
        }
    }

    offer?.let { info ->
        WarmDialog(
            icon = Icons.Outlined.Restore, accent = scheme.primary, title = "Found your backup",
            confirmLabel = "Restore", onConfirm = {
                offer = null
                AppScope.launch {
                    try {
                        val n = BackupManager.restore()
                        toast(context, "Restored $n ${if (n == 1) "bank" else "banks"}")
                    } catch (e: com.bankingpages.backup.NeedsPinException) {
                        restorePin = true // only a backup made by an old version still asks
                    } catch (e: Throwable) {
                        toast(context, BackupManager.friendly(e))
                    }
                }
            },
            dismissLabel = "Not now", onDismiss = { offer = null }
        ) {
            DialogText("Saved ${whenText(info.modified)}. Restoring replaces what's on this phone now.")
        }
    }

    if (restorePin) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { restorePin = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            PinPanel(Icons.Outlined.CloudDownload, "Backup PIN", "Enter the PIN from when this backup was made", onComplete = { pin ->
                try {
                    val n = BackupManager.restore(pin)
                    restorePin = false
                    toast(context, "Restored $n ${if (n == 1) "bank" else "banks"}")
                    true
                } catch (e: WrongPasswordException) {
                    false
                } catch (e: Throwable) {
                    restorePin = false
                    toast(context, BackupManager.friendly(e))
                    true
                }
            }, footer = { Text("Cancel", style = MaterialTheme.typography.labelLarge, color = scheme.primary, modifier = Modifier.bounceClick(0.92f) { restorePin = false }.padding(12.dp)) })
        }
    }

    if (confirmDelete) {
        WarmDialog(
            icon = Icons.Outlined.DeleteOutline, accent = status.danger, title = "Delete your backup?",
            confirmLabel = "Delete", onConfirm = {
                confirmDelete = false
                AppScope.launch { runCatching { BackupManager.deleteBackup() }.onSuccess { toast(context, "Backup deleted from Drive") }.onFailure { toast(context, BackupManager.friendly(it)) } }
            },
            dismissLabel = "Keep", onDismiss = { confirmDelete = false }
        ) { DialogText("Removes it from your Drive. Everything on this phone stays, and the next change backs it up again unless you sign out.") }
    }
}
