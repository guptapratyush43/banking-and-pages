package com.bankingpages.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.ContentCopy
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
import com.bankingpages.data.PhotoSlot
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

/** The form's chapters, top to bottom. The side rail shows one icon for each. */
private class Chapter(val title: String, val icon: ImageVector, val tint: Color)

private val CHAPTERS = listOf(
    Chapter("Customer ID", Icons.Outlined.Badge, Color(0xFF8A55E0)),
    Chapter("Account details", Icons.Outlined.AccountBalance, Color(0xFFD9542B)),
    Chapter("Registered contact", Icons.Outlined.ContactPhone, Color(0xFF1E9E8A)),
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
    var busySlot by remember { mutableStateOf<PhotoSlot?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val seen = remember { mutableSetOf<Any>() }

    val scroll = rememberScrollState()
    // Where each chapter starts in the scrolling form, for the rail to jump to.
    val tops = remember { mutableStateMapOf<Int, Int>() }
    val current by remember { derivedStateOf { tops.filter { it.value <= scroll.value + 260 }.keys.maxOrNull() ?: 0 } }
    val backdrop = rememberLayerBackdrop()

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
                        .padding(start = 20.dp, end = 66.dp, top = 8.dp, bottom = 24.dp)
                ) {
                    // The bank itself, with a pencil to pick another.
                    WarmCard(modifier = Modifier.entrance(0, "bank", seen)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BankLogo(a.bankId, a.bankName, 56.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.bankName, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                                RowBody("Its logo keeps itself up to date")
                            }
                            Spacer(Modifier.width(8.dp))
                            CardAction(Icons.Rounded.Edit, "Change bank", { picking = true }, Modifier.width(44.dp))
                        }
                    }

                    Section(0, tops, seen) {
                        WarmField(a.customerId, { a = a.copy(customerId = it) }, "Customer ID / CIF", leading = Icons.Outlined.Badge, keyboard = next())
                    }

                    Section(1, tops, seen, action = {
                        CardAction(Icons.Rounded.ContentCopy, "Copy account details", {
                            copy(context, "Account details", a.shareText())
                            if (Build.VERSION.SDK_INT >= 33) toast(context, "Account details copied")
                        }, Modifier.width(44.dp), filled = false)
                    }) {
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

                    Section(2, tops, seen) {
                        WarmField(a.mobile, { a = a.copy(mobile = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }) }, "Registered mobile", leading = Icons.Outlined.Phone,
                            keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
                        Gap()
                        WarmField(a.email, { a = a.copy(email = it.trim()) }, "Registered email", leading = Icons.Outlined.Email,
                            keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                    }

                    Section(3, tops, seen) {
                        WarmField(a.netUserId, { a = a.copy(netUserId = it) }, "User ID", leading = Icons.Outlined.AccountCircle, keyboard = next())
                        Gap()
                        SecretField(a.loginPassword, { a = a.copy(loginPassword = it) }, "Login password")
                        Gap()
                        SecretField(a.txnPassword, { a = a.copy(txnPassword = it) }, "Transaction password (if any)")
                        Gap()
                        SecretField(a.profilePassword, { a = a.copy(profilePassword = it) }, "Profile password (if any)")
                    }

                    Section(4, tops, seen) {
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

                    Section(5, tops, seen) {
                        PhotoGrid(
                            photos = a.photos,
                            fileLabel = a.bankName,
                            onOpen = {},
                            onAdd = { slot, uris ->
                                busySlot = slot
                                scope.launch {
                                    runCatching { Media.importScan(uris) }
                                        .onSuccess { id -> added += id; a = a.copy(photos = a.photos + (slot to id)) }
                                        .onFailure { toast(context, it.message ?: "Couldn't add that photo") }
                                    busySlot = null
                                }
                            },
                            onRemove = { slot -> a = a.copy(photos = a.photos - slot) },
                            busySlot = busySlot
                        )
                    }

                    Section(6, tops, seen) {
                        WarmField(a.notes, { a = a.copy(notes = it) }, "Anything else to remember", leading = Icons.Outlined.EditNote, singleLine = false, minLines = 3,
                            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    }
                }

                SideRail(backdrop, current, Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) { i ->
                    tops[i]?.let { y -> scope.launch { scroll.animateScrollTo((y - 24).coerceAtLeast(0), Motion.smooth()) } }
                }
            }
            // Save stays in reach at the bottom, above the keyboard.
            Box(Modifier.fillMaxWidth().background(scheme.background).padding(horizontal = 20.dp, vertical = 14.dp)) {
                PrimaryButton(if (isNew) "Save bank" else "Save changes", Icons.Outlined.Check, onClick = {
                    val final = a.copy(questions = questions.filter { it.question.isNotBlank() || it.answer.isNotBlank() }, updatedAt = System.currentTimeMillis())
                    // Blobs dropped again before saving are cleared here too.
                    added.filter { it !in final.photos.values }.forEach(Vault::deleteBlob)
                    onSave(final)
                }, modifier = Modifier.fillMaxWidth())
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
 * The floating glass rail on the right edge: one icon per chapter, in order. The one
 * you are reading is lit; tap any to glide to it.
 */
@Composable
private fun SideRail(backdrop: LayerBackdrop, current: Int, modifier: Modifier = Modifier, onJump: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val surface = scheme.surface.copy(alpha = if (Build.VERSION.SDK_INT >= 31) 0.6f else 0.96f)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(24.dp) },
                effects = { vibrancy(); blur(8.dp.toPx()); lens(10.dp.toPx(), 20.dp.toPx()) },
                onDrawSurface = { drawRect(surface) }
            )
            .padding(5.dp)
    ) {
        CHAPTERS.forEachIndexed { i, c ->
            val on = i == current
            val glow by animateFloatAsState(if (on) 1f else 0f, Motion.bouncy(), label = "railOn")
            val tint by animateColorAsState(if (on) Color.White else scheme.onSurfaceVariant, label = "railTint")
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(38.dp).bounceClick(0.85f) { onJump(i) }) {
                Box(Modifier.size(38.dp).graphicsLayer { alpha = glow.coerceIn(0f, 1f); val s = 0.6f + 0.4f * glow; scaleX = s; scaleY = s }.background(AccentBrush, CircleShape))
                Icon(c.icon, c.title, tint = tint, modifier = Modifier.size(19.dp))
            }
        }
    }
}

/** One chapter of the form: its own card, headed by a coloured icon, with an optional button top right. */
@Composable
private fun Section(index: Int, tops: MutableMap<Int, Int>, seen: MutableSet<Any>, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val chapter = CHAPTERS[index]
    Spacer(Modifier.height(18.dp))
    WarmCard(modifier = Modifier.onGloballyPositioned { tops[index] = it.positionInParent().y.toInt() }.entrance(index + 1, chapter.title, seen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(chapter.icon, size = 38.dp, iconSize = 20.dp, background = chapter.tint, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Text(chapter.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        Spacer(Modifier.height(16.dp))
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

@Composable private fun Gap() = Spacer(Modifier.height(10.dp))
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
