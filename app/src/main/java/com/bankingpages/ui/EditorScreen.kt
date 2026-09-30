package com.bankingpages.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContactPhone
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bankingpages.data.Account
import com.bankingpages.data.SecurityQA
import com.bankingpages.data.Vault
import com.bankingpages.files.Media
import com.bankingpages.ui.motion.Motion
import com.bankingpages.ui.motion.bounceClick
import com.bankingpages.ui.motion.entrance
import com.bankingpages.ui.theme.AccentBrush
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TYPES = listOf("Savings", "Current", "Salary")
private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** The form's chapters, top to bottom. The side rail shows one icon for each. */
class Chapter(val title: String, val icon: ImageVector, val tint: Color)

val CHAPTERS = listOf(
    Chapter("Customer ID", Icons.Outlined.Badge, Color(0xFF8A55E0)),
    Chapter("Registered contact", Icons.Outlined.ContactPhone, Color(0xFF1E9E8A)),
    Chapter("Account details", Icons.Outlined.AccountBalance, Color(0xFFD9542B)),
    Chapter("Net banking", Icons.Outlined.Language, Color(0xFF3B7BE0)),
    Chapter("Security questions", Icons.Outlined.Shield, Color(0xFFC2459B)),
    Chapter("Documents", Icons.Outlined.Description, Color(0xFF5B6BD6)),
    Chapter("Notes", Icons.Outlined.EditNote, Color(0xFFD9932B))
)

@Composable
fun EditorScreen(initial: Account, isNew: Boolean, onBack: () -> Unit, onSave: (Account) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var a by remember { mutableStateOf(initial) }
    val questions = remember { mutableStateListOf<SecurityQA>().apply { addAll(initial.questions) } }
    // Photos taken during this edit; thrown away again if the edit is abandoned.
    val added = remember { mutableListOf<String>() }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val seen = remember { mutableSetOf<Any>() }

    val scroll = rememberScrollState()
    // Where each chapter starts in the scrolling form, for the rail to jump to.
    val tops = remember { mutableStateMapOf<Int, Int>() }
    val reading by remember {
        derivedStateOf {
            // At the very bottom it is the last chapter, which can never reach the top of the screen.
            if (scroll.maxValue > 0 && scroll.value >= scroll.maxValue - 12) CHAPTERS.lastIndex
            else tops.filter { it.value <= scroll.value + 420 }.keys.maxOrNull() ?: 0
        }
    }
    // A chapter picked from the bar stays lit (the last ones can't scroll to the top) until you scroll by hand.
    var picked by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(scroll) { scroll.interactionSource.interactions.collect { if (it is DragInteraction.Start) picked = null } }
    val current = picked ?: reading
    // Bumped on every jump so the chosen card breathes.
    var pulse by remember { mutableStateOf(0 to -1) }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val backdrop = rememberLayerBackdrop()

    var mobileBad by remember { mutableStateOf(false) }
    var mobileShake by remember { mutableStateOf(0) }
    val emailBad = a.email.isNotBlank() && !EMAIL.matches(a.email)
    var emailChecked by remember { mutableStateOf(false) }
    var emailShake by remember { mutableStateOf(0) }

    val changed = a != initial || questions.toList() != initial.questions
    fun discard() { added.forEach(Vault::deleteBlob); onBack() }
    BackHandler(enabled = !picking) { if (changed) confirmLeave = true else discard() }

    // The branch and its address (and the MICR) come from the IFSC; they are not typed.
    LaunchedEffect(a.ifsc) {
        val code = a.ifsc
        if (!Ifsc.PATTERN.matches(code)) return@LaunchedEffect
        val info = withContext(Dispatchers.IO) { Ifsc.lookup(code) } ?: return@LaunchedEffect
        val branch = listOf(Ifsc.pretty(info.branch), Ifsc.pretty(info.city)).filter { it.isNotBlank() }.distinct().joinToString(", ")
        if (a.ifsc == code) a = a.copy(branch = branch, branchAddress = Ifsc.pretty(info.address), micr = a.micr.ifBlank { info.micr })
    }

    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar(if (isNew) "Add bank" else "Edit bank", onBack = { if (changed) confirmLeave = true else discard() })
            Box(Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxSize().layerBackdrop(backdrop).verticalScroll(scroll)
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = if (imeOpen) 24.dp else 146.dp)
                ) {
                    // The bank itself, with a pencil to pick another.
                    WarmCard(padding = 14.dp, modifier = Modifier.entrance(0, "bank", seen)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BankLogo(a.bankId, a.bankName, 48.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(a.bankName, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            CardAction(Icons.Rounded.Edit, "Change bank", { picking = true }, Modifier.width(44.dp))
                        }
                    }

                    ChapterCard(0, tops, seen, pulse) {
                        WarmField(a.customerId, { a = a.copy(customerId = it) }, "Customer ID / CIF", leading = Icons.Outlined.Badge, keyboard = next())
                    }

                    ChapterCard(1, tops, seen, pulse) {
                        WarmField(
                            a.mobile, { v ->
                                val clean = v.filter { c -> c.isDigit() || c == '+' || c == ' ' }
                                // An eleventh digit is refused, with a shake.
                                if (clean.count(Char::isDigit) > 10) { mobileBad = true; mobileShake++ } else { mobileBad = false; a = a.copy(mobile = clean) }
                            }, "Registered mobile", leading = Icons.Outlined.Phone,
                            keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                            isError = mobileBad, supporting = if (mobileBad) "Invalid phone number: a mobile number has 10 digits" else null,
                            shakeKey = mobileShake, onBlur = { mobileBad = false }
                        )
                        Gap()
                        WarmField(
                            a.email, { a = a.copy(email = it.trim()) }, "Registered email", leading = Icons.Outlined.Email,
                            keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            isError = emailBad && emailChecked, supporting = if (emailBad && emailChecked) "Invalid email: it needs an @, like name@mail.com" else null,
                            shakeKey = emailShake, onBlur = { emailChecked = true; if (emailBad) emailShake++ }
                        )
                    }

                    ChapterCard(2, tops, seen, pulse) {
                        WarmField(a.holder, { a = a.copy(holder = it) }, "Account holder name", leading = Icons.Outlined.Person, keyboard = words())
                        Gap()
                        WarmField(a.number, { a = a.copy(number = it.filter(Char::isLetterOrDigit)) }, "Account number", leading = Icons.Outlined.Numbers,
                            keyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                        Gap()
                        val badIfsc = a.ifsc.length == 11 && !Ifsc.PATTERN.matches(a.ifsc)
                        WarmField(
                            a.ifsc, { a = a.copy(ifsc = it.uppercase().filter(Char::isLetterOrDigit).take(11)) }, "IFSC code", leading = Icons.Outlined.QrCode2,
                            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                            isError = badIfsc, supporting = if (badIfsc) "IFSC is 4 letters, a 0, then 6 letters or digits" else null
                        )
                        Gap()
                        BranchPanel(a.branch, a.branchAddress)
                        Gap()
                        WarmField(a.micr, { a = a.copy(micr = it.filter(Char::isDigit).take(9)) }, "MICR", leading = Icons.Outlined.Pin,
                            keyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                        Spacer(Modifier.height(14.dp))
                        Text("Account type", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TYPES.forEach { t -> Chip(t, a.type == t) { a = a.copy(type = t) } }
                        }
                    }

                    ChapterCard(3, tops, seen, pulse) {
                        WarmField(a.netUserId, { a = a.copy(netUserId = it) }, "User ID", leading = Icons.Outlined.AccountCircle, keyboard = next())
                        Gap()
                        SecretField(a.loginPassword, { a = a.copy(loginPassword = it) }, "Login password")
                        Gap()
                        SecretField(a.txnPassword, { a = a.copy(txnPassword = it) }, "Transaction password (if any)")
                        Gap()
                        SecretField(a.profilePassword, { a = a.copy(profilePassword = it) }, "Profile password (if any)")
                    }

                    ChapterCard(4, tops, seen, pulse) {
                        Column(Modifier.animateContentSize(Motion.smooth())) {
                            questions.forEachIndexed { i, qa ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Question ${i + 1}", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, modifier = Modifier.weight(1f))
                                    Icon(Icons.Outlined.DeleteOutline, "Remove question", tint = scheme.onSurfaceVariant,
                                        modifier = Modifier.bounceClick(0.85f) { questions.removeAt(i) }.padding(6.dp).size(20.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                                WarmField(qa.question, { questions[i] = qa.copy(question = it) }, "Question", leading = Icons.Outlined.HelpOutline,
                                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next))
                                Gap()
                                SecretField(qa.answer, { questions[i] = qa.copy(answer = it) }, "Answer")
                                Spacer(Modifier.height(16.dp))
                            }
                            if (questions.isEmpty()) RowBody("Add the questions your bank asks when you reset a password.")
                            Spacer(Modifier.height(10.dp))
                            SmallButton("Add question", Icons.Outlined.Add, { questions.add(SecurityQA("", "")) })
                        }
                    }

                    ChapterCard(5, tops, seen, pulse) {
                        PhotoGrid(
                            account = a,
                            onOpen = { _, _ -> },
                            onAdd = { key, uris ->
                                busyKey = key
                                scope.launch {
                                    runCatching { Media.importScan(uris) }
                                        .onSuccess { id -> added += id; a = a.withPhoto(key, id) }
                                        .onFailure { toast(context, it.message ?: "Couldn't add that photo") }
                                    busyKey = null
                                }
                            },
                            onRemove = { key -> a = a.withoutPhoto(key) },
                            busyKey = busyKey
                        )
                    }

                    ChapterCard(6, tops, seen, pulse) {
                        WarmField(a.notes, { a = a.copy(notes = it) }, "Anything else to remember", leading = Icons.Outlined.EditNote, singleLine = false, minLines = 3,
                            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    }
                }

                // The chapter bar and Save float over the form; the page fades out softly behind them.
                val bg = scheme.background
                androidx.compose.animation.AnimatedVisibility(!imeOpen, Modifier.align(Alignment.BottomCenter), enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(0f to bg.copy(alpha = 0f), 0.3f to bg.copy(alpha = 0.72f), 0.62f to bg, 1f to bg))
                        .padding(start = 20.dp, end = 20.dp, top = 46.dp, bottom = 14.dp)
                ) {
                    ChapterBar(backdrop, current, Modifier.padding(bottom = 12.dp)) { i ->
                        picked = i
                        pulse = pulse.first + 1 to i
                        tops[i]?.let { y -> scope.launch { scroll.animateScrollTo((y - 24).coerceAtLeast(0), Motion.smooth()) } }
                    }
                    PrimaryButton(if (isNew) "Save bank" else "Save changes", Icons.Outlined.Check, onClick = {
                        if (emailBad) {
                            // Not saved with a broken email: go to it and shake it.
                            emailChecked = true; emailShake++
                            tops[1]?.let { y -> scope.launch { scroll.animateScrollTo((y - 24).coerceAtLeast(0), Motion.smooth()) } }
                            return@PrimaryButton
                        }
                        val final = a.copy(questions = questions.filter { it.question.isNotBlank() || it.answer.isNotBlank() }, updatedAt = System.currentTimeMillis())
                        // Blobs dropped again before saving are cleared here too.
                        added.filter { it !in final.allBlobs }.forEach(Vault::deleteBlob)
                        onSave(final)
                    }, modifier = Modifier.fillMaxWidth())
                }
                }
            }
        }

        AnimatedVisibility(
            visible = picking,
            enter = slideInVertically(Motion.smooth(IntOffset.VisibilityThreshold)) { it },
            exit = slideOutVertically(Motion.push(IntOffset.VisibilityThreshold)) { it }
        ) {
            BackHandler { picking = false }
            BankPickerScreen(onBack = { picking = false }, onPick = { b ->
                a = a.copy(bankId = b.id, bankName = b.name, bankDomain = if (b.id.startsWith("c_")) b.domain else null)
                picking = false
            })
        }
    }

    if (confirmLeave) {
        WarmDialog(
            icon = Icons.Outlined.DeleteOutline, accent = scheme.primary, title = "Leave without saving?",
            confirmLabel = "Discard", onConfirm = { confirmLeave = false; discard() },
            dismissLabel = "Keep editing", onDismiss = { confirmLeave = false }
        ) { DialogText("Your changes to ${a.bankName} haven't been saved.") }
    }
}

/**
 * The floating glass bar above Save: one icon per chapter, in order. The chapter in
 * view is lit; tap any to glide to it.
 */
@Composable
fun ChapterBar(backdrop: LayerBackdrop, current: Int, modifier: Modifier = Modifier, onJump: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Thin enough that the form shows through and the glass reads as glass.
    val surface = scheme.surface.copy(alpha = if (Build.VERSION.SDK_INT >= 31) 0.38f else 0.96f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(24.dp) },
                effects = { vibrancy(); blur(6.dp.toPx()); lens(12.dp.toPx(), 24.dp.toPx()) },
                onDrawSurface = { drawRect(surface) }
            )
            .padding(5.dp)
    ) {
        CHAPTERS.forEachIndexed { i, c ->
            val on = i == current
            val glow by animateFloatAsState(if (on) 1f else 0f, Motion.bouncy(), label = "barOn")
            val tint by animateColorAsState(if (on) Color.White else scheme.onSurface, label = "barTint")
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).bounceClick(0.85f) { onJump(i) }) {
                Box(Modifier.size(40.dp).graphicsLayer { alpha = glow.coerceIn(0f, 1f); val s = 0.6f + 0.4f * glow; scaleX = s; scaleY = s }.background(AccentBrush, CircleShape).gloss(CircleShape))
                Icon(c.icon, c.title, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** One chapter of the form: its own card, headed by a coloured icon, with an optional button top right. */
@Composable
fun ChapterCard(
    index: Int, tops: MutableMap<Int, Int>, seen: MutableSet<Any>, pulse: Pair<Int, Int>,
    action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit
) {
    val chapter = CHAPTERS[index]
    // Picked from the bar: the card breathes once, ringed in its own colour, so the eye lands on it.
    val breath = remember { Animatable(0f) }
    LaunchedEffect(pulse) {
        if (pulse.second == index && pulse.first > 0) {
            delay(180)
            breath.animateTo(1f, tween(360)); breath.animateTo(0f, tween(520))
        }
    }
    Spacer(Modifier.height(12.dp))
    WarmCard(
        padding = 14.dp,
        modifier = Modifier
            .onGloballyPositioned { tops[index] = it.positionInParent().y.toInt() }
            .entrance(index + 1, chapter.title, seen)
            .graphicsLayer { val s = 1f + 0.025f * breath.value; scaleX = s; scaleY = s }
            .drawWithContent {
                drawContent()
                val v = breath.value
                if (v > 0f) {
                    val r = CornerRadius(18.dp.toPx())
                    drawRoundRect(chapter.tint.copy(alpha = 0.10f * v), cornerRadius = r)
                    drawRoundRect(chapter.tint.copy(alpha = v), cornerRadius = r, style = Stroke(3.dp.toPx()))
                }
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(chapter.icon, size = 32.dp, iconSize = 17.dp, background = chapter.tint, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Text(chapter.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** The branch, shown rather than typed: plainly not a field, and it says where it comes from. */
@Composable
private fun BranchPanel(branch: String, address: String) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
        Icon(Icons.Outlined.LocationOn, null, tint = scheme.primary, modifier = Modifier.padding(top = 2.dp).size(21.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Branch", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Tag("Auto from IFSC", color = scheme.primary)
            }
            Spacer(Modifier.height(4.dp))
            if (branch.isBlank() && address.isBlank()) Text("Appears here once the IFSC is entered", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            else {
                if (branch.isNotBlank()) Text(branch, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                if (address.isNotBlank()) Text(address, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun Gap() = Spacer(Modifier.height(8.dp))
private fun words() = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
private fun next() = KeyboardOptions(imeAction = ImeAction.Next)

@Composable
fun SecretField(value: String, onChange: (String) -> Unit, label: String) {
    var show by remember { mutableStateOf(false) }
    WarmField(
        value, onChange, label, leading = Icons.Outlined.Key,
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        visual = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "Hide" else "Show",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.bounceClick(0.85f) { show = !show }.padding(8.dp))
        }
    )
}
