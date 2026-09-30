package com.bankingpages.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bankingpages.ui.motion.Motion
import com.bankingpages.ui.motion.bounceClick
import com.bankingpages.ui.theme.AccentBrush
import com.bankingpages.ui.theme.LocalStatusColors

fun toast(context: Context, msg: String) {
    if (msg.isNotBlank()) Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()
}

/** One shape and one height for every full-size button, so they line up wherever they sit. */
val Pill = RoundedCornerShape(14.dp)
private val ButtonHeight = 52.dp

/** The soft, wide shadow that lifts cards and round buttons off the page. */
fun Modifier.softShadow(shape: Shape, elevation: Dp = 14.dp) = composed {
    val dark = isSystemInDarkTheme()
    shadow(
        elevation, shape, clip = false,
        ambientColor = Color.Black.copy(alpha = if (dark) 0.5f else 0.08f),
        spotColor = Color.Black.copy(alpha = if (dark) 0.6f else 0.13f)
    )
}

/**
 * Clear glass for quiet controls: a see-through pane that is brighter at the top, with a
 * lit rim along its upper edge. Used by every secondary button, round icon and chip.
 */
fun Modifier.glass(shape: Shape, tint: Color? = null) = composed {
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val base = tint ?: if (dark) Color.White.copy(alpha = 0.10f) else scheme.surfaceVariant.copy(alpha = 0.78f)
    val rim = if (dark) 0.34f else 0.95f
    clip(shape)
        .background(Brush.verticalGradient(listOf(base.copy(alpha = (base.alpha * 1.15f).coerceAtMost(1f)), base.copy(alpha = base.alpha * 0.62f))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = rim), Color.White.copy(alpha = rim * 0.12f), scheme.outline.copy(alpha = 0.55f))), shape)
}

/** Tinted glass for filled controls: a soft sheen over the top half and a bright rim, laid over their colour. */
fun Modifier.gloss(shape: Shape) = this
    .drawWithContent {
        drawContent()
        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.26f), 0.5f to Color.White.copy(alpha = 0.04f), 1f to Color.Transparent))
    }
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.06f))), shape)

/** The soft, floating card every screen is built from. */
@Composable
fun WarmCard(
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
    padding: Dp = 18.dp,
    radius: Dp = 18.dp,
    elevation: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.bounceClick(0.96f, onLongClick = onLongClick, onClick = onClick) else Modifier)
            .softShadow(shape, elevation)
            .clip(shape)
            .background(background, shape)
            .border(0.5.dp, borderColor, shape)
            .padding(padding),
        content = content
    )
}

/** The very fine rule under section titles and between rows. */
@Composable
fun HairLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 4.dp, bottom = 10.dp)
    )
}

/** A home section's title, its count, and the add button on the same line, over a fine rule. */
@Composable
fun SectionHeader(title: String, count: Int, modifier: Modifier = Modifier, action: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The count sits right beside the word, level with its letters.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                if (count > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(top = 1.dp).heightIn(min = 22.dp).background(MaterialTheme.colorScheme.primary, CircleShape).padding(horizontal = 8.dp)
                    ) {
                        Text("$count", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 14.sp), color = Color.White)
                    }
                }
            }
            action()
        }
        Spacer(Modifier.height(12.dp))
        HairLine()
    }
}

/** The small gradient "+ Add" capsule used by both home sections. */
@Composable
fun AddPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .bounceClick(0.92f, onClick = onClick)
            .softShadow(Pill, 8.dp)
            .clip(Pill)
            .background(AccentBrush)
            .gloss(Pill)
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
    }
}

@Composable
fun IconBubble(
    icon: ImageVector,
    size: Dp = 76.dp,
    iconSize: Dp = 34.dp,
    background: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size).background(background, CircleShape)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** A round floating icon button (back, edit, share), bouncy instead of rippling. */
@Composable
fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface, size: Dp = 44.dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .bounceClick(0.88f, onClick = onClick)
            .softShadow(CircleShape, 8.dp)
            .background(scheme.surface.copy(alpha = 0.72f), CircleShape)
            .glass(CircleShape, Color.White.copy(alpha = if (isSystemInDarkTheme()) 0.10f else 0.5f))
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.45f))
    }
}

/** Screen title row. With no [onBack] it is a tab's large title. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = if (onBack == null) 28.dp else 12.dp, bottom = 8.dp)
    ) {
        if (onBack != null) {
            RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, style = if (onBack == null) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun PrimaryButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = MaterialTheme.colorScheme.primary) {
    val brush = if (color == MaterialTheme.colorScheme.primary) AccentBrush else SolidColor(color)
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .bounceClick(0.95f, enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .softShadow(Pill, 10.dp)
            .clip(Pill)
            .background(brush)
            .gloss(Pill)
            .height(ButtonHeight)
            .padding(horizontal = 22.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .bounceClick(0.95f, onClick = onClick)
            .glass(Pill)
            .height(ButtonHeight)
            .padding(horizontal = 20.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Small tinted capsule for secondary actions inside cards. */
@Composable
fun SmallButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .bounceClick(0.93f, onClick = onClick)
            .glass(Pill, scheme.primary.copy(alpha = 0.16f))
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        icon?.let { Icon(it, null, tint = scheme.primary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = scheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Tag inside a card: "SAVINGS", "PDF". */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, Pill)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.6.sp), color = color, maxLines = 1)
    }
}

/** The app's text field: a soft filled block with its label inside and an accent ring on focus. */
@Composable
fun WarmField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visual: VisualTransformation = VisualTransformation.None,
    supporting: String? = null,
    isError: Boolean = false,
    leading: ImageVector? = null,
    /** Bump to shake the field from side to side: "that isn't valid". */
    shakeKey: Int = 0,
    onBlur: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val danger = LocalStatusColors.current.danger
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val haptic = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            for (x in listOf(13f, -11f, 8f, -5f, 2f, 0f)) shake.animateTo(x, spring(stiffness = 2600f))
        }
    }
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) { if (wasFocused && !focused) onBlur?.invoke(); if (!wasFocused && focused) onFocus?.invoke(); wasFocused = focused }
    val ring by animateColorAsState(if (isError) danger else if (focused) scheme.primary else Color.Transparent, label = "fieldRing")
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.fillMaxWidth().graphicsLayer { translationX = shake.value * density }) {
        Row(
            // A multi-line field (notes) keeps its icon level with the first line.
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(scheme.surfaceVariant)
                .border(1.5.dp, ring, shape)
                .heightIn(min = 50.dp)
                .padding(start = 16.dp, end = if (trailing != null) 6.dp else 16.dp, top = 7.dp, bottom = 8.dp)
        ) {
            if (leading != null) {
                val tint by animateColorAsState(if (focused) scheme.primary else scheme.onSurfaceVariant, label = "fieldIcon")
                Icon(leading, null, tint = tint, modifier = Modifier.padding(top = if (singleLine) 0.dp else 5.dp).size(21.dp))
                Spacer(Modifier.width(13.dp))
            }
            Column(Modifier.weight(1f)) {
                // With nothing typed the label rests in the middle as a hint; it moves up once there is text or focus.
                val raised = focused || value.isNotEmpty()
                if (raised) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (isError) danger else if (focused) scheme.primary else scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    singleLine = singleLine,
                    minLines = if (raised) minLines else 1,
                    keyboardOptions = keyboard,
                    visualTransformation = visual,
                    interactionSource = source,
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (!raised) Text(label, style = MaterialTheme.typography.bodyLarge, color = if (isError) danger else scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            inner()
                        }
                    }
                )
            }
            trailing?.invoke()
        }
        if (supporting != null) Text(
            supporting, style = MaterialTheme.typography.bodySmall, color = if (isError) danger else scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 5.dp)
        )
    }
}

/** An iOS-style switch: the track changes colour and the thumb springs across. */
@Composable
fun WarmSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val track by animateColorAsState(if (checked) scheme.primary else scheme.outline, label = "switchTrack")
    val x by animateDpAsState(if (checked) 22.dp else 0.dp, Motion.bouncy(Dp.VisibilityThreshold), label = "switchThumb")
    Box(
        Modifier
            .size(54.dp, 32.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(!checked)
            }
            .padding(3.dp)
    ) {
        Box(Modifier.offset { IntOffset(x.roundToPx(), 0) }.size(26.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape))
    }
}

@Composable
fun RowBody(text: String) {
    Spacer(Modifier.height(2.dp))
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)
    )
}

@Composable
fun WarmMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 16.dp,
        offset = DpOffset(0.dp, 6.dp),
        content = content
    )
}

@Composable
fun MenuItem(text: String, icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.labelLarge, color = if (tint == MaterialTheme.colorScheme.primary) MaterialTheme.colorScheme.onSurface else tint) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        onClick = onClick
    )
}

/** The app's standard dialog: icon bubble, bold title, content, then two equal buttons. It springs in. */
@Composable
fun WarmDialog(
    icon: ImageVector,
    accent: Color,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    busy: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pop = remember { Animatable(0.86f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, Motion.bouncy()) }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 22.dp)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .fillMaxWidth()
                .background(scheme.surface, RoundedCornerShape(22.dp))
                .border(0.5.dp, scheme.outline, RoundedCornerShape(22.dp))
                .padding(22.dp)
        ) {
            IconBubble(icon, size = 60.dp, iconSize = 28.dp, background = accent.copy(alpha = 0.12f), tint = accent)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            content()
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(dismissLabel, null, onDismiss, Modifier.weight(1f), tint = scheme.onSurface)
                // While [busy], the confirm button holds a spinner and ignores taps.
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    PrimaryButton(if (busy) " " else confirmLabel, null, { if (!busy) onConfirm() }, Modifier.fillMaxWidth(), enabled = confirmEnabled, color = accent)
                    if (busy) androidx.compose.material3.CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun DialogText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

/** Round chip for a one-of-many choice (account type, document kind). */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .bounceClick(0.92f, onClick = onClick)
            .then(if (selected) Modifier.clip(Pill).background(AccentBrush).gloss(Pill) else Modifier.glass(Pill))
            .padding(horizontal = 15.dp, vertical = 9.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = if (selected) Color.White else scheme.onSurfaceVariant)
    }
}

/**
 * A solid little button that lives inside a card. [filled] is the accent gradient
 * with a white icon (the main action); otherwise a quiet block with a dark icon.
 */
@Composable
fun CardAction(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null, filled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val content = if (filled) Color.White else scheme.onSurface
    Row(
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .bounceClick(0.9f, onClick = onClick)
            .then(if (filled) Modifier.clip(shape).background(AccentBrush).gloss(shape) else Modifier.glass(shape))
            .height(38.dp)
            .padding(horizontal = 10.dp)
    ) {
        Icon(icon, description, tint = content, modifier = Modifier.size(18.dp))
        if (label != null) {
            Spacer(Modifier.width(7.dp))
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
