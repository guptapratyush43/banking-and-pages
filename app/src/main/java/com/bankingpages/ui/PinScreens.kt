package com.bankingpages.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.WarningAmber
import com.bankingpages.data.Recovery
import com.bankingpages.backup.BackupManager
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bankingpages.data.Pin
import com.bankingpages.ui.motion.Motion
import com.bankingpages.ui.motion.bounceClick
import com.bankingpages.ui.motion.popIn
import com.bankingpages.ui.theme.LocalStatusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PIN_LENGTH = 4

// --- The pad ---------------------------------------------------------------

/** Four dots that pop as digits go in, and shake (in red) when the PIN is wrong. */
@Composable
private fun PinDots(filled: Int, error: Boolean, shake: Animatable<Float, *>) {
    val scheme = MaterialTheme.colorScheme
    val danger = LocalStatusColors.current.danger
    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.graphicsLayer { translationX = shake.value * density }
    ) {
        repeat(PIN_LENGTH) { i ->
            val on = i < filled
            val scale by animateFloatAsState(if (on) 1f else 0.72f, Motion.bouncy(), label = "dot")
            val color by animateColorAsState(if (error) danger else if (on) scheme.primary else Color.Transparent, label = "dotColor")
            Box(
                Modifier
                    .size(16.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .background(color, CircleShape)
                    .border(1.5.dp, if (error) danger else if (on) scheme.primary else scheme.outline, CircleShape)
            )
        }
    }
}

private val LETTERS = mapOf('2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ", '0' to "+")

/** One round key, like the phone's own lock screen: digit with its letters beneath. */
@Composable
private fun Key(digit: Char, onPress: (Char) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, if (pressed) Motion.snappy() else Motion.bouncy(), label = "key")
    val fill by animateColorAsState(if (pressed) scheme.primaryContainer else scheme.surface, spring(stiffness = 900f), label = "keyFill")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(fill, CircleShape)
            .border(0.5.dp, if (pressed) scheme.primary.copy(alpha = 0.5f) else scheme.outline, CircleShape)
            .clickable(source, indication = null) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPress(digit) }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digit.toString(), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 30.sp), color = scheme.onSurface)
            Text(LETTERS[digit] ?: " ", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.6.sp, lineHeight = 10.sp), color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SideKey(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp).bounceClick(0.86f, onClick = onClick)) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun Keypad(onDigit: (Char) -> Unit, onDelete: () -> Unit, onFingerprint: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { row.forEach { Key(it, onDigit) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (onFingerprint != null) SideKey(Icons.Outlined.Fingerprint, "Use fingerprint", onFingerprint) else Spacer(Modifier.size(76.dp))
            Key('0', onDigit)
            SideKey(Icons.AutoMirrored.Outlined.Backspace, "Delete", onDelete)
        }
    }
}

/**
 * Title, dots and pad. [onComplete] gets the 4 digits and answers whether they
 * were right; a wrong answer shakes the dots and clears them.
 */
@Composable
fun PinPanel(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onComplete: suspend (String) -> Boolean,
    onFingerprint: (() -> Unit)? = null,
    locked: Boolean = false,
    footer: @Composable () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    fun press(d: Char) {
        if (checking || locked || entered.length >= PIN_LENGTH) return
        error = false
        entered += d
        if (entered.length == PIN_LENGTH) {
            checking = true
            scope.launch {
                delay(120) // let the last dot land before judging
                val ok = onComplete(entered)
                if (!ok) {
                    error = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, spring(stiffness = 3000f))
                    delay(250)
                    entered = ""
                }
                checking = false
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().background(scheme.background).padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.weight(0.8f))
        IconBubble(icon, size = 64.dp, iconSize = 28.dp, modifier = Modifier.popIn(80))
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = scheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = if (error) LocalStatusColors.current.danger else scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        PinDots(entered.length, error, shake)
        Spacer(Modifier.weight(1f))
        Keypad(::press, { if (!checking) { error = false; entered = entered.dropLast(1) } }, onFingerprint)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) { footer() }
        Spacer(Modifier.height(12.dp))
    }
}

// --- Lock screen -----------------------------------------------------------

@Composable
fun LockScreen(fingerprintOn: Boolean, onFingerprint: () -> Unit, onForgot: () -> Unit) {
    var wait by remember { mutableLongStateOf(Pin.waitMillis()) }
    LaunchedEffect(wait > 0) { while (Pin.waitMillis() > 0) { wait = Pin.waitMillis(); delay(500) }; wait = 0 }
    // Offer the fingerprint straight away, as the phone's own lock does.
    LaunchedEffect(Unit) { if (fingerprintOn) { delay(350); onFingerprint() } }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var resetting by rememberSaveable { mutableStateOf(false) }
    var looking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val danger = LocalStatusColors.current.danger

    // Forgot PIN: reset with the Aadhaar number if one was added (here, or in the Drive backup);
    // otherwise the only way in is to erase and set up again.
    fun forgot() {
        if (looking) return
        if (Recovery.isSet) { resetting = true; return }
        looking = true
        scope.launch {
            val found = BackupManager.fetchRecovery()
            looking = false
            if (found != null && Recovery.import(found)) resetting = true else confirmReset = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        PinPanel(
            icon = Icons.Outlined.Lock,
            title = "Welcome back",
            subtitle = if (wait > 0) "Too many tries. Try again in ${(wait + 999) / 1000} s" else "Enter your 4-digit PIN",
            onComplete = { pin ->
                val ok = withContext(Dispatchers.Default) { Pin.verify(pin) }
                if (!ok) wait = Pin.waitMillis()
                ok
            },
            onFingerprint = if (fingerprintOn) onFingerprint else null,
            locked = wait > 0,
            footer = {
                Text(if (looking) "Checking…" else "Forgot PIN?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.bounceClick(0.92f) { forgot() }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        )
        if (resetting) ResetPinFlow(
            onDone = { resetting = false },
            // Wrong Aadhaar tries count like wrong PINs, so the pad picks up any wait at once.
            onCancel = { resetting = false; wait = Pin.waitMillis() },
            onErase = { resetting = false; wait = Pin.waitMillis(); confirmReset = true }
        )
    }

    if (confirmReset) {
        WarmDialog(
            icon = Icons.Outlined.DeleteForever,
            accent = danger,
            title = "Start over?",
            confirmLabel = "Erase all",
            onConfirm = { confirmReset = false; onForgot() },
            dismissLabel = "Cancel",
            onDismiss = { confirmReset = false }
        ) {
            DialogText(
                if (Recovery.isSet) "This erases everything on this phone so you can set a new PIN. If you back up to Google Drive, you can restore it all while setting up again."
                else "No Aadhaar number was added, so this PIN can't be reset. Erase everything on this phone and set up again. If you back up to Google Drive, you can restore it all while setting up."
            )
        }
    }
}

// --- First launch ------------------------------------------------------------

private class IntroPage(val icon: ImageVector, val title: String, val body: String)

private val INTRO = listOf(
    IntroPage(Icons.Outlined.AccountBalance, "Every bank, one tidy place",
        "Account numbers, IFSC codes, net banking logins and security answers for each bank you use, kept neatly under its logo."),
    IntroPage(Icons.Outlined.CreditCard, "Cheques, cards and passbooks",
        "Snap your cancelled cheque, passbook and cards, and keep your Aadhaar and PAN PDFs too. Open, share or save any of them in a tap."),
    IntroPage(Icons.Outlined.Shield, "Locked and private",
        "Everything is encrypted on this phone and opens only with your PIN. Back it up to your own Google Drive whenever you like.")
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(0) } // 0 intro, 1 create PIN, 2 confirm PIN
    var first by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val spec = Motion.push(androidx.compose.ui.unit.IntOffset.VisibilityThreshold)
            val forward = targetState > initialState
            (if (forward) slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 4 }
            else slideInHorizontally(spec) { -it / 4 } togetherWith slideOutHorizontally(spec) { it })
                .apply { targetContentZIndex = if (forward) 1f else -1f }
        },
        label = "onboarding"
    ) { s ->
        when (s) {
            0 -> {
                val pager = rememberPagerState { INTRO.size }
                Column(Modifier.fillMaxSize().background(scheme.background)) {
                    HorizontalPager(pager, modifier = Modifier.weight(1f)) { page ->
                        val p = INTRO[page]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)
                        ) {
                            // Each page's bubble springs up as it comes into view.
                            val shown = pager.currentPage == page
                            val scale by animateFloatAsState(if (shown) 1f else 0.7f, Motion.bouncy(), label = "introIcon")
                            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
                                IconBubble(p.icon, size = 116.dp, iconSize = 52.dp)
                            }
                            Spacer(Modifier.height(34.dp))
                            Text(p.title, style = MaterialTheme.typography.displaySmall, color = scheme.onBackground, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(14.dp))
                            Text(p.body, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        repeat(INTRO.size) { i ->
                            val active = pager.currentPage == i
                            val w by animateFloatAsState(if (active) 22f else 8f, Motion.bouncy(), label = "introDot")
                            Box(
                                Modifier.height(8.dp).width(w.dp)
                                    .background(if (active) scheme.primary else scheme.outline, RoundedCornerShape(50))
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    val last = pager.currentPage == INTRO.lastIndex
                    PrimaryButton(
                        if (last) "Set up my PIN" else "Next", null,
                        onClick = { if (last) step = 1 else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    )
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        if (!last) Text("Skip", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
                            modifier = Modifier.bounceClick(0.92f) { step = 1 }.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
            1 -> PinPanel(
                icon = Icons.Outlined.Lock,
                title = "Create your PIN",
                subtitle = "You'll use these 4 digits every time you open Banking and Pages",
                onComplete = { pin -> first = pin; step = 2; true },
                footer = { Text("Don't use your ATM or UPI PIN", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
            )
            2 -> PinPanel(
                icon = Icons.Outlined.Lock,
                title = "Confirm your PIN",
                subtitle = "Enter the same 4 digits once more",
                onComplete = { pin ->
                    if (pin != first) {
                        toast(context, "PINs didn't match. Let's try again")
                        false
                    } else {
                        withContext(Dispatchers.Default) { Pin.set(pin) }
                        step = 3
                        true
                    }
                },
                footer = {
                    Text("Start again", style = MaterialTheme.typography.labelLarge, color = scheme.primary,
                        modifier = Modifier.bounceClick(0.92f) { first = ""; step = 1 }.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            )
            3 -> AadhaarSetup(
                title = "Add your Aadhaar number", primary = "Save and continue", secondary = "Skip for now",
                onSaved = { step = 4 }, onSecondary = { step = 4 }, skipWarning = true
            )
            else -> RestoreStep(onDone)
        }
    }
}

// --- Aadhaar for PIN reset -------------------------------------------------

/** Shows the 12 digits as "1234 5678 9012" while the field itself holds only digits. */
private object AadhaarGroups : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val d = text.text
        val out = buildString { d.forEachIndexed { i, c -> if (i > 0 && i % 4 == 0) append(' '); append(c) } }
        return TransformedText(AnnotatedString(out), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + (offset - 1).coerceAtLeast(0) / 4
            override fun transformedToOriginal(offset: Int) = (offset - offset / 5).coerceIn(0, d.length)
        })
    }
}

@Composable
private fun Note(icon: ImageVector, text: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().background(tint.copy(alpha = 0.10f), RoundedCornerShape(14.dp)).padding(12.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The page frame the Aadhaar screens share: icon, title, line of text, then the rest. */
@Composable
private fun AadhaarPage(title: String, subtitle: String, subtitleColor: Color, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().background(scheme.background).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        IconBubble(Icons.Outlined.Badge, size = 72.dp, iconSize = 32.dp, modifier = Modifier.popIn(80))
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = scheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = subtitleColor, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        content()
    }
}

/**
 * Saves the Aadhaar number that can later reset a forgotten PIN (first setup, or Profile).
 * [skipWarning] explains, at setup, what skipping it means.
 */
@Composable
fun AadhaarSetup(title: String, primary: String, secondary: String, onSaved: () -> Unit, onSecondary: () -> Unit, skipWarning: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var digits by rememberSaveable { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    var shake by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    AadhaarPage(title, "If you ever forget your PIN, this number lets you set a new one.", scheme.onSurfaceVariant) {
        Note(Icons.Outlined.Lock, "It stays on this phone and in your own Google Drive backup. It is never sent anywhere else.", scheme.primary)
        Spacer(Modifier.height(16.dp))
        WarmField(
            digits, { v -> digits = v.filter(Char::isDigit).take(12); bad = false }, "Aadhaar number", leading = Icons.Outlined.Badge,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visual = AadhaarGroups,
            isError = bad, supporting = if (bad) "That isn't a valid Aadhaar number. Check the 12 digits." else null, shakeKey = shake
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton(if (saving) "Saving…" else primary, null, {
            if (!saving) {
                if (!Recovery.isValid(digits)) { bad = true; shake++ }
                else {
                    saving = true
                    scope.launch { withContext(Dispatchers.Default) { Recovery.set(digits) }; saving = false; onSaved() }
                }
            }
        }, Modifier.fillMaxWidth(), enabled = digits.length == 12)
        Spacer(Modifier.height(10.dp))
        SecondaryButton(secondary, null, onSecondary, Modifier.fillMaxWidth(), tint = scheme.onSurface)
        if (skipWarning) {
            Spacer(Modifier.height(16.dp))
            Note(Icons.Outlined.WarningAmber, "Without it, a forgotten PIN can't be reset. You would have to erase the app and set it up again, restoring from your Google Drive backup if you have one, or start afresh.", LocalStatusColors.current.warning)
        }
    }
}

/** Forgot PIN: the Aadhaar number, then a new PIN twice. The new PIN works from that moment. */
@Composable
fun ResetPinFlow(onDone: () -> Unit, onCancel: () -> Unit, onErase: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(0) }
    var fresh by rememberSaveable { mutableStateOf("") }
    BackHandler { onCancel() }
    val cancel: @Composable () -> Unit = {
        Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.bounceClick(0.92f) { onCancel() }.padding(horizontal = 12.dp, vertical = 8.dp))
    }
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val spec = Motion.push(androidx.compose.ui.unit.IntOffset.VisibilityThreshold)
            (slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 4 }).apply { targetContentZIndex = 1f }
        },
        label = "resetPin"
    ) { s ->
        when (s) {
            0 -> AadhaarCheck(onOk = { step = 1 }, onCancel = onCancel, onErase = onErase)
            1 -> PinPanel(Icons.Outlined.Lock, "New PIN", "Choose 4 new digits", onComplete = { pin -> fresh = pin; step = 2; true }, footer = cancel)
            else -> PinPanel(Icons.Outlined.Lock, "Confirm new PIN", "Enter it once more", onComplete = { pin ->
                if (pin != fresh) { toast(context, "PINs didn't match. Try again"); false }
                else {
                    withContext(Dispatchers.Default) { Pin.set(pin) }
                    toast(context, "PIN reset. Use your new PIN from now on")
                    onDone()
                    true
                }
            }, footer = cancel)
        }
    }
}

/** Checks the Aadhaar number against the saved hash. Wrong answers count like wrong PINs. */
@Composable
private fun AadhaarCheck(onOk: () -> Unit, onCancel: () -> Unit, onErase: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val danger = LocalStatusColors.current.danger
    var digits by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var shake by remember { mutableStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    var wait by remember { mutableLongStateOf(Pin.waitMillis()) }
    LaunchedEffect(wait > 0) { while (Pin.waitMillis() > 0) { wait = Pin.waitMillis(); delay(500) }; wait = 0 }
    AadhaarPage(
        "Reset your PIN",
        if (wait > 0) "Too many tries. Try again in ${(wait + 999) / 1000} s" else "Enter the Aadhaar number you added when you set up the app.",
        if (wait > 0) danger else scheme.onSurfaceVariant
    ) {
        WarmField(
            digits, { v -> digits = v.filter(Char::isDigit).take(12); error = null }, "Aadhaar number", leading = Icons.Outlined.Badge,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visual = AadhaarGroups,
            isError = error != null, supporting = error, shakeKey = shake
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton(if (checking) "Checking…" else "Continue", null, {
            if (!checking && wait == 0L) {
                // A mistyped number (bad check digit) is caught here and doesn't use up a try.
                if (!Recovery.isValid(digits)) { error = "That isn't a valid Aadhaar number. Check the 12 digits."; shake++ }
                else {
                    checking = true
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) { Recovery.matches(digits) }
                        checking = false
                        if (ok) { Pin.clearFailures(); onOk() }
                        else {
                            Pin.registerFailure()
                            wait = Pin.waitMillis()
                            error = "That number doesn't match the one saved for this app."
                            shake++
                        }
                    }
                }
            }
        }, Modifier.fillMaxWidth(), enabled = digits.length == 12 && wait == 0L)
        Spacer(Modifier.height(10.dp))
        SecondaryButton("Cancel", null, onCancel, Modifier.fillMaxWidth(), tint = scheme.onSurface)
        Spacer(Modifier.height(18.dp))
        Text("Don't have it? Erase and start over", style = MaterialTheme.typography.labelLarge, color = scheme.primary,
            modifier = Modifier.bounceClick(0.92f) { onErase() }.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/** Profile: check the current PIN, then add or change the Aadhaar number for PIN reset. */
@Composable
fun RecoveryFlow(onDone: (Boolean) -> Unit) {
    var step by remember { mutableStateOf(0) }
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val spec = Motion.push(androidx.compose.ui.unit.IntOffset.VisibilityThreshold)
            (slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 4 }).apply { targetContentZIndex = 1f }
        },
        label = "recovery"
    ) { s ->
        if (s == 0) PinPanel(Icons.Outlined.Lock, "Current PIN", "Enter your PIN first", onComplete = { pin ->
            val ok = withContext(Dispatchers.Default) { Pin.verify(pin) }
            if (ok) step = 1
            ok
        }, footer = {
            Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.bounceClick(0.92f) { onDone(false) }.padding(12.dp))
        })
        else AadhaarSetup(
            title = if (Recovery.isSet) "Change your Aadhaar number" else "Add your Aadhaar number",
            primary = "Save", secondary = "Cancel", onSaved = { onDone(true) }, onSecondary = { onDone(false) }, skipWarning = false
        )
    }
}

/** Last step of setup: back up to Google Drive, and bring an earlier backup back. */
@Composable
private fun RestoreStep(onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().background(scheme.background).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        IconBubble(Icons.Outlined.CloudDownload, size = 72.dp, iconSize = 32.dp, modifier = Modifier.popIn(80))
        Spacer(Modifier.height(18.dp))
        Text("Back up to Google Drive", style = MaterialTheme.typography.headlineMedium, color = scheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Sign in to keep a copy of everything in your own Drive. Used the app before? Your backup comes back here too.",
            style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        BackupSection()
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Start using the app", null, onDone, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Footnote("You can also do this later in Profile.")
    }
}
