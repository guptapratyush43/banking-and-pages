package com.bankingpages.ui

import androidx.compose.animation.core.VisibilityThreshold
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bankingpages.data.Account
import com.bankingpages.data.PhotoSlot
import com.bankingpages.data.Pin
import com.bankingpages.data.SecurityQA
import com.bankingpages.data.Vault
import com.bankingpages.files.Media
import com.bankingpages.logo.LogoStore
import com.bankingpages.ui.motion.Motion
import com.bankingpages.ui.motion.bounceClick
import com.bankingpages.ui.motion.entrance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TYPES = listOf("Savings", "Current", "Salary", "NRE", "NRO", "Joint")

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
    var ifscNote by remember { mutableStateOf<String?>(null) }
    val seen = remember { mutableSetOf<Any>() }

    val changed = a != initial || questions.toList() != initial.questions
    fun discard() { added.forEach(Vault::deleteBlob); onBack() }
    BackHandler(enabled = !picking) { if (changed) confirmLeave = true else discard() }

    // Fill branch and MICR from the IFSC as soon as it is complete.
    LaunchedEffect(a.ifsc) {
        ifscNote = null
        val code = a.ifsc
        if (!Ifsc.PATTERN.matches(code)) return@LaunchedEffect
        val info = withContext(Dispatchers.IO) { Ifsc.lookup(code) } ?: return@LaunchedEffect
        val address = Ifsc.pretty(info.address)
        ifscNote = listOf(Ifsc.pretty(info.branch), Ifsc.pretty(info.city)).filter { it.isNotBlank() }.distinct().joinToString(", ")
        if (a.ifsc == code) a = a.copy(
            branch = a.branch.ifBlank { ifscNote!! },
            branchAddress = a.branchAddress.ifBlank { address },
            micr = a.micr.ifBlank { info.micr }
        )
    }

    val logoPick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream) }.getOrNull() }
            if (bmp == null) toast(context, "Couldn't read that picture") else withContext(Dispatchers.IO) { LogoStore.setManual(a.bankId, bmp) }
        }
    }

    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar(if (isNew) "Add bank" else "Edit bank", onBack = { if (changed) confirmLeave = true else discard() })
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp)
            ) {
                // Bank
                WarmCard(modifier = Modifier.entrance(0, "bank", seen)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BankLogo(a.bankId, a.bankName, 56.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.bankName, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                            RowBody(if (LogoStore.isManual(a.bankId)) "Using your own logo" else "Logo updates on its own")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("Change bank", Icons.Outlined.SwapHoriz, { picking = true })
                        SmallButton("Own logo", Icons.Outlined.Image, {
                            Pin.awayOnPurpose = true
                            logoPick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        if (LogoStore.isManual(a.bankId)) SmallButton("Reset logo", Icons.Outlined.Restore, { LogoStore.resetManual(a.bankId, a.bankDomain ?: com.bankingpages.data.BankCatalog.get(a.bankId)?.domain) })
                    }
                }

                Section("Account", 1, seen) {
                    WarmField(a.holder, { a = a.copy(holder = it) }, "Account holder name", keyboard = words())
                    Gap()
                    WarmField(a.number, { a = a.copy(number = it.filter(Char::isLetterOrDigit)) }, "Account number", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                    Gap()
                    val badIfsc = a.ifsc.length == 11 && !Ifsc.PATTERN.matches(a.ifsc)
                    WarmField(
                        a.ifsc, { a = a.copy(ifsc = it.uppercase().filter(Char::isLetterOrDigit).take(11)) }, "IFSC code",
                        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                        isError = badIfsc,
                        supporting = when { badIfsc -> "IFSC is 4 letters, a 0, then 6 letters or digits"; ifscNote != null -> "Found: $ifscNote"; else -> null }
                    )
                    Gap()
                    WarmField(a.branch, { a = a.copy(branch = it) }, "Branch", keyboard = words())
                    Gap()
                    WarmField(a.branchAddress, { a = a.copy(branchAddress = it) }, "Branch address", singleLine = false,
                        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words), supporting = "Fills in from the IFSC")
                    Spacer(Modifier.height(14.dp))
                    Text("Account type", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TYPES.forEach { t -> Chip(t, a.type == t) { a = a.copy(type = t) } }
                    }
                    Spacer(Modifier.height(14.dp))
                    WarmField(a.customerId, { a = a.copy(customerId = it) }, "Customer ID / CIF", keyboard = next())
                    Gap()
                    WarmField(a.mobile, { a = a.copy(mobile = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }) }, "Registered mobile", keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
                    Gap()
                    WarmField(a.email, { a = a.copy(email = it.trim()) }, "Registered email", keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                    Gap()
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WarmField(a.micr, { a = a.copy(micr = it.filter(Char::isDigit).take(9)) }, "MICR", Modifier.weight(1f), keyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                        WarmField(a.upi, { a = a.copy(upi = it.trim()) }, "UPI ID", Modifier.weight(1.4f), keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                    }
                }

                Section("Net banking", 2, seen) {
                    WarmField(a.netUserId, { a = a.copy(netUserId = it) }, "User ID", keyboard = next())
                    Gap()
                    SecretField(a.loginPassword, { a = a.copy(loginPassword = it) }, "Login password")
                    Gap()
                    SecretField(a.txnPassword, { a = a.copy(txnPassword = it) }, "Transaction password (if any)")
                    Gap()
                    SecretField(a.profilePassword, { a = a.copy(profilePassword = it) }, "Profile password (if any)")
                }

                Section("Security questions", 3, seen) {
                    Column(Modifier.animateContentSize(Motion.smooth())) {
                        questions.forEachIndexed { i, qa ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Question ${i + 1}", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, modifier = Modifier.weight(1f))
                                Icon(Icons.Outlined.DeleteOutline, "Remove question", tint = scheme.onSurfaceVariant,
                                    modifier = Modifier.bounceClick(0.85f) { questions.removeAt(i) }.padding(6.dp).size(20.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            WarmField(qa.question, { questions[i] = qa.copy(question = it) }, "Question", keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next))
                            Gap()
                            SecretField(qa.answer, { questions[i] = qa.copy(answer = it) }, "Answer")
                            Spacer(Modifier.height(16.dp))
                        }
                        if (questions.isEmpty()) RowBody("Add the questions your bank asks when you reset a password.")
                        Spacer(Modifier.height(10.dp))
                        SmallButton("Add question", Icons.Outlined.Add, { questions.add(SecurityQA("", "")) })
                    }
                }

                Section("Photos", 4, seen) {
                    PhotoStrip(
                        photos = a.photos,
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

                Section("Notes", 5, seen) {
                    WarmField(a.notes, { a = a.copy(notes = it) }, "Anything else to remember", singleLine = false, minLines = 3,
                        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                }
            }
            // Save stays in reach at the bottom, above the keyboard.
            Box(Modifier.fillMaxWidth().background(scheme.background).padding(horizontal = 20.dp, vertical = 12.dp)) {
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
            enter = slideInVertically(Motion.smooth(androidx.compose.ui.unit.IntOffset.VisibilityThreshold)) { it },
            exit = slideOutVertically(Motion.push(androidx.compose.ui.unit.IntOffset.VisibilityThreshold)) { it }
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

@Composable
private fun Section(title: String, index: Int, seen: MutableSet<Any>, content: @Composable () -> Unit) {
    Spacer(Modifier.height(22.dp))
    Column(Modifier.entrance(index, title, seen)) {
        SectionLabel(title)
        WarmCard { content() }
    }
}

@Composable private fun Gap() = Spacer(Modifier.height(10.dp))
private fun words() = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
private fun next() = KeyboardOptions(imeAction = ImeAction.Next)

@Composable
fun SecretField(value: String, onChange: (String) -> Unit, label: String) {
    var show by remember { mutableStateOf(false) }
    WarmField(
        value, onChange, label,
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        visual = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "Hide" else "Show",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.bounceClick(0.85f) { show = !show }.padding(8.dp))
        }
    )
}
