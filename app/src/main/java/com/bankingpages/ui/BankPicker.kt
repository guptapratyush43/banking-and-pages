package com.bankingpages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddBusiness
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bankingpages.data.Bank
import com.bankingpages.data.BankCatalog
import com.bankingpages.ui.motion.bounceClick
import com.bankingpages.ui.motion.entrance

/** Picked bank: a catalogue id, or a custom one the user typed. */
data class PickedBank(val id: String, val name: String, val domain: String?)

@Composable
fun BankPickerScreen(onBack: () -> Unit, onPick: (PickedBank) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    var addOther by rememberSaveable { mutableStateOf(false) }
    val results = remember(query) { BankCatalog.search(query) }
    val grouped = remember(results, query) {
        if (query.isNotBlank()) listOf("Matches" to results)
        else BankCatalog.categories.map { c -> c to results.filter { it.category == c } }.filter { it.second.isNotEmpty() }
    }
    val seen = remember { mutableSetOf<Any>() }
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize().background(scheme.background)) {
        TopBar("Choose your bank", onBack, subtitle = "${BankCatalog.banks.size} Indian banks")
        WarmField(
            value = query,
            onValueChange = { query = it },
            label = "Search by name, e.g. SBI or Kotak",
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
            trailing = {
                if (query.isNotEmpty()) Icon(Icons.Outlined.Close, "Clear", tint = scheme.onSurfaceVariant, modifier = Modifier.bounceClick(0.85f) { query = "" })
                else Icon(Icons.Outlined.Search, null, tint = scheme.onSurfaceVariant)
            },
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "other") {
                WarmCard(
                    borderColor = scheme.primary.copy(alpha = 0.4f), padding = 14.dp, radius = 20.dp, elevation = 6.dp,
                    onClick = { addOther = true }, modifier = Modifier.entrance(0, "other", seen)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Outlined.AddBusiness, size = 40.dp, iconSize = 20.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (results.isEmpty() && query.isNotBlank()) "Add “${query.trim()}”" else "Can't find your bank?", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                            RowBody("Add any other bank, including new ones")
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            var index = 1
            grouped.forEach { (category, banks) ->
                item(key = "h-$category") { SectionLabel(category, Modifier.padding(top = 10.dp)) }
                val base = index
                index += banks.size
                itemsIndexed(banks, key = { _, b -> b.id }) { i, b ->
                    BankRow(b, Modifier.entrance(base + i, b.id, seen)) { onPick(PickedBank(b.id, b.name, b.domain)) }
                }
            }
            if (results.isEmpty()) item(key = "none") {
                Footnote("No bank called “${query.trim()}” in the list yet. Add it above and its logo will be looked up for you.", Modifier.padding(top = 24.dp))
            }
        }
    }

    if (addOther) {
        var name by rememberSaveable { mutableStateOf(query.trim()) }
        var site by rememberSaveable { mutableStateOf("") }
        WarmDialog(
            icon = Icons.Outlined.AddBusiness,
            accent = scheme.primary,
            title = "Add another bank",
            confirmLabel = "Add",
            confirmEnabled = name.isNotBlank(),
            onConfirm = {
                addOther = false
                val domain = site.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/').ifBlank { null }
                onPick(PickedBank(BankCatalog.customId(name.trim()), name.trim(), domain))
            },
            dismissLabel = "Cancel",
            onDismiss = { addOther = false }
        ) {
            WarmField(name, { name = it }, "Bank name", keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
            Spacer(Modifier.height(10.dp))
            WarmField(site, { site = it }, "Website (optional)", keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                supporting = "e.g. mybank.in, so we can find its logo")
        }
    }
}

@Composable
private fun BankRow(b: Bank, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    WarmCard(padding = 12.dp, radius = 20.dp, elevation = 6.dp, onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BankLogo(b.id, b.name, 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(b.name, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(b.category, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}
