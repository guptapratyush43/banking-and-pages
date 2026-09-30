package com.bankingpages.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bankingpages.ui.motion.LocalReduceMotion
import com.bankingpages.ui.motion.Motion
import com.bankingpages.ui.motion.bounceClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

// --- WelcomePage pages: a little vault character and a colourful scene per page ---

private class WelcomePage(val title: String, val body: String, val light: Color, val deep: Color)

private val WELCOME = listOf(
    WelcomePage(
        "Every bank, one tidy place",
        "Account numbers, IFSC codes, net banking logins and security answers for each bank you use, kept neatly under its logo.",
        Color(0xFFF58A55), Color(0xFFD13F2B)
    ),
    WelcomePage(
        "Cheques, cards and passbooks",
        "Snap your cancelled cheque, passbook and cards, and keep your Aadhaar and PAN PDFs too. Open, share or save any of them in a tap.",
        Color(0xFF7AA7F5), Color(0xFF3B5FD0)
    ),
    WelcomePage(
        "Locked and private",
        "Everything is encrypted on this phone and opens only with your PIN. Back it up to your own Google Drive whenever you like.",
        Color(0xFF45C9AE), Color(0xFF14806E)
    )
)

private val ART_TOP = 28.dp
private val ART_HEIGHT = 300.dp
private val MASCOT = 136.dp
private val INK = Color(0xFF2A1B14)
private val GOLD = Color(0xFFF2B53A)

private fun PagerState.position() = currentPage + currentPageOffsetFraction

/** The page colour at any point of a swipe, blended between neighbours. */
private fun blend(position: Float, pick: (WelcomePage) -> Color): Color {
    val p = position.coerceIn(0f, WELCOME.lastIndex.toFloat())
    val i = floor(p).toInt().coerceAtMost(WELCOME.lastIndex - 1)
    return lerp(pick(WELCOME[i]), pick(WELCOME[i + 1]), p - i)
}

/** Seconds since first shown, for idle loops. Read it only in draw/layer lambdas. Frozen when motion is reduced. */
@Composable
private fun rememberClock(): State<Float> {
    val reduce = LocalReduceMotion.current
    return produceState(0f, reduce) {
        if (reduce) return@produceState
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1_000_000_000f }
    }
}

/** 0 → 1 with a springy overshoot once its page is on screen (after [delayMs]); back to 0 when it leaves. */
@Composable
private fun rememberPop(shown: Boolean, delayMs: Long): Animatable<Float, AnimationVector1D> {
    val reduce = LocalReduceMotion.current
    val a = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(shown, reduce) {
        when {
            reduce -> a.snapTo(1f)
            shown -> { delay(delayMs); a.animateTo(1f, spring(0.5f, 320f)) }
            else -> a.animateTo(0f, tween(160))
        }
    }
    return a
}

@Composable
fun WelcomePager(onFinish: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val pager = rememberPagerState { WELCOME.size }
    val clock = rememberClock()
    var pokes by remember { mutableIntStateOf(0) }
    val dark = scheme.background.luminance() < 0.5f

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .drawBehind {
                // Two soft colour clouds drift behind everything, tinted by the page.
                val t = clock.value
                val pos = pager.position()
                val a = blend(pos) { it.light }.copy(alpha = if (dark) 0.30f else 0.28f)
                val b = blend(pos) { it.deep }.copy(alpha = if (dark) 0.24f else 0.14f)
                val c1 = Offset(size.width * (0.2f + 0.08f * sin(t * 0.5f)), size.height * (0.16f + 0.05f * cos(t * 0.4f)))
                val c2 = Offset(size.width * (0.86f + 0.07f * cos(t * 0.45f)), size.height * (0.5f + 0.06f * sin(t * 0.35f)))
                val r1 = size.width * 0.8f
                val r2 = size.width * 0.72f
                drawCircle(Brush.radialGradient(listOf(a, Color.Transparent), c1, r1), r1, c1)
                drawCircle(Brush.radialGradient(listOf(b, Color.Transparent), c2, r2), r2, c2)
            }
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val p = WELCOME[page]
                val shown = pager.currentPage == page
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    Spacer(Modifier.height(ART_TOP))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().height(ART_HEIGHT).graphicsLayer {
                            // The scene trails the swipe a little, so it has depth.
                            translationX = -(page - pager.position()) * size.width * 0.35f
                        }
                    ) {
                        when (page) {
                            0 -> BankScene(shown, clock)
                            1 -> DocumentScene(shown, clock)
                            else -> LockScene(shown, clock)
                        }
                        // The character is drawn above the pager; this catches its taps and still lets swipes through.
                        Box(Modifier.size(MASCOT).clickable(remember { MutableInteractionSource() }, indication = null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pokes++
                        })
                    }
                    Spacer(Modifier.height(26.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.graphicsLayer {
                            val off = page - pager.position()
                            alpha = (1f - abs(off) * 1.4f).coerceIn(0f, 1f)
                            translationY = abs(off) * 40.dp.toPx()
                        }
                    ) {
                        Text(p.title, style = MaterialTheme.typography.displaySmall, color = scheme.onBackground, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        Text(p.body, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
            // The character stays put while the pages slide beneath it, changing colour as you swipe.
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(ART_TOP))
                Box(Modifier.fillMaxWidth().height(ART_HEIGHT), contentAlignment = Alignment.Center) {
                    Vaulty(pager, pokes, clock, Modifier.size(MASCOT))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            repeat(WELCOME.size) { i ->
                val active = pager.currentPage == i
                val w by animateFloatAsState(if (active) 24f else 8f, Motion.bouncy(), label = "welcomeDot")
                val c by animateColorAsState(if (active) WELCOME[i].deep else scheme.outline, label = "welcomeDotColor")
                Box(Modifier.height(8.dp).width(w.dp).background(c, RoundedCornerShape(50)))
            }
        }
        Spacer(Modifier.height(28.dp))
        val last = pager.currentPage == WELCOME.lastIndex
        PrimaryButton(
            if (last) "Set up my PIN" else "Next", null,
            onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )
        Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
            if (!last) Text(
                "Skip", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
                modifier = Modifier.bounceClick(0.92f) { onFinish() }.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

// --- The character ---------------------------------------------------------

/**
 * A round little vault that floats, blinks, looks the way you swipe and jumps with a grin
 * when tapped. Its dial turns as the pages turn, and on the last page it holds a padlock
 * that clicks shut.
 */
@Composable
private fun Vaulty(pager: PagerState, pokes: Int, clock: State<Float>, modifier: Modifier) {
    val reduce = LocalReduceMotion.current
    val appear = remember { Animatable(if (reduce) 1f else 0f) }
    val blink = remember { Animatable(0f) }
    val squash = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    val happy = remember { Animatable(0f) }
    val dial = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (appear.value < 1f) { delay(180); appear.animateTo(1f, Motion.bouncy()) }
        // Says hello once it has landed.
        if (!reduce) { delay(250); happy.snapTo(1f); delay(900); happy.animateTo(0f, tween(200)) }
    }
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2200, 4200))
            repeat(if (Random.nextInt(4) == 0) 2 else 1) {
                blink.animateTo(1f, tween(70)); blink.animateTo(0f, tween(120))
            }
        }
    }
    LaunchedEffect(pokes) {
        if (pokes == 0) return@LaunchedEffect
        launch { squash.animateTo(1f, tween(90)); squash.animateTo(0f, spring(0.3f, 380f)) }
        launch { delay(60); hop.animateTo(1f, spring(1f, 700f)); hop.animateTo(0f, spring(0.45f, 420f)) }
        launch { dial.animateTo(dial.value + 360f, spring(0.7f, 90f)) }
        happy.snapTo(1f); delay(1100); happy.animateTo(0f, tween(200))
    }

    Box(modifier) {
        // Its shadow stays on the ground and shrinks as it jumps.
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width
            val k = 1f - 0.45f * hop.value
            drawOval(Color.Black.copy(alpha = 0.12f * k * appear.value), Offset(0.5f * s - 0.3f * s * k, 0.91f * s), Size(0.6f * s * k, 0.07f * s))
        }
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                val sq = squash.value
                val a = appear.value
                translationY = sin(clock.value * 2.2f) * 5.dp.toPx() - hop.value * 30.dp.toPx()
                scaleX = a * (1f + 0.16f * sq)
                scaleY = a * (1f - 0.16f * sq)
                transformOrigin = TransformOrigin(0.5f, 0.9f)
            }
        ) {
            drawVaulty(pager.position(), pager.currentPageOffsetFraction, clock.value, blink.value, happy.value, dial.value)
        }
    }
}

private fun DrawScope.drawVaulty(pos: Float, drag: Float, t: Float, blink: Float, happy: Float, dial: Float) {
    val s = size.width
    val light = blend(pos) { it.light }
    val deep = blend(pos) { it.deep }
    val lock = (pos - 1f).coerceIn(0f, 1f)
    val limb = lerp(deep, Color.Black, 0.22f)

    // Feet
    drawRoundRect(limb, Offset(0.26f * s, 0.79f * s), Size(0.16f * s, 0.1f * s), CornerRadius(0.05f * s))
    drawRoundRect(limb, Offset(0.58f * s, 0.79f * s), Size(0.16f * s, 0.1f * s), CornerRadius(0.05f * s))
    // Arms, waving while it grins
    val wave = happy * sin(t * 16f) * 22f
    rotate(-18f - wave, Offset(0.14f * s, 0.52f * s)) {
        drawRoundRect(limb, Offset(0.01f * s, 0.47f * s), Size(0.17f * s, 0.1f * s), CornerRadius(0.05f * s))
    }
    rotate(18f + wave, Offset(0.86f * s, 0.52f * s)) {
        drawRoundRect(limb, Offset(0.82f * s, 0.47f * s), Size(0.17f * s, 0.1f * s), CornerRadius(0.05f * s))
    }
    // Body with a glossy top
    val top = 0.08f * s
    val height = 0.76f * s
    drawRoundRect(Brush.verticalGradient(listOf(light, deep), startY = top, endY = top + height), Offset(0.11f * s, top), Size(0.78f * s, height), CornerRadius(0.24f * s))
    drawRoundRect(Color.White.copy(alpha = 0.24f), Offset(0.2f * s, 0.13f * s), Size(0.3f * s, 0.08f * s), CornerRadius(0.04f * s))

    // Eyes: follow the swipe, wander a little, blink; happy arcs while it grins.
    val look = (-drag * 2.4f).coerceIn(-1f, 1f) * 0.8f + 0.3f * sin(t * 0.7f)
    val eyeY = 0.34f * s
    val eyeR = 0.085f * s
    for (ex in floatArrayOf(0.36f * s, 0.64f * s)) {
        if (happy > 0.5f) {
            drawArc(INK, 200f, 140f, false, Offset(ex - eyeR * 0.9f, eyeY - eyeR * 0.5f), Size(eyeR * 1.8f, eyeR * 1.5f), style = Stroke(0.034f * s, cap = StrokeCap.Round))
        } else {
            val h = eyeR * 2f * (1f - 0.9f * blink)
            drawOval(Color.White, Offset(ex - eyeR, eyeY - h / 2f), Size(eyeR * 2f, h))
            if (blink < 0.6f) {
                val px = ex + look * 0.032f * s
                drawCircle(INK, 0.046f * s * (1f - blink), Offset(px, eyeY + 0.01f * s))
                drawCircle(Color.White, 0.015f * s, Offset(px + 0.016f * s, eyeY - 0.012f * s))
            }
        }
    }
    // Cheeks
    drawCircle(Color(0xFFFF8FA3).copy(alpha = 0.5f), 0.045f * s, Offset(0.24f * s, 0.45f * s))
    drawCircle(Color(0xFFFF8FA3).copy(alpha = 0.5f), 0.045f * s, Offset(0.76f * s, 0.45f * s))
    // Mouth: a smile, or wide open while it grins
    if (happy > 0.5f) {
        drawArc(INK, 0f, 180f, true, Offset(0.41f * s, 0.4f * s), Size(0.18f * s, 0.15f * s))
        drawCircle(Color(0xFFFF7A8A), 0.035f * s, Offset(0.5f * s, 0.505f * s))
    } else {
        drawArc(INK, 20f, 140f, false, Offset(0.42f * s, 0.4f * s), Size(0.16f * s, 0.1f * s), style = Stroke(0.03f * s, cap = StrokeCap.Round))
    }

    // Belly: a vault dial that turns with the pages, becoming a padlock on the last one.
    val bc = Offset(0.5f * s, 0.66f * s)
    if (lock < 1f) {
        val al = 1f - lock
        drawCircle(Color.White.copy(alpha = 0.92f * al), 0.12f * s, bc)
        drawCircle(deep.copy(alpha = al), 0.07f * s, bc, style = Stroke(0.018f * s))
        rotate(dial + pos * 120f, bc) {
            for (i in 0 until 8) rotate(i * 45f, bc) {
                drawLine(deep.copy(alpha = al), Offset(bc.x, bc.y - 0.108f * s), Offset(bc.x, bc.y - 0.09f * s), 0.012f * s, StrokeCap.Round)
            }
            drawLine(INK.copy(alpha = al), bc, Offset(bc.x, bc.y - 0.055f * s), 0.022f * s, StrokeCap.Round)
        }
        drawCircle(INK.copy(alpha = al), 0.02f * s, bc)
    }
    if (lock > 0f) {
        // The shackle lifts mid-swipe and clicks shut as the last page settles.
        val lift = (1f - ((pos - 1.5f) / 0.5f).coerceIn(0f, 1f)) * 0.06f * s
        val k = 0.6f + 0.4f * lock
        scale(k, k, bc) {
            val sx = 0.065f * s
            drawArc(Color(0xFFE9E4DA).copy(alpha = lock), 180f, 180f, false, Offset(bc.x - sx, bc.y - 0.13f * s - lift), Size(sx * 2f, sx * 2f), style = Stroke(0.03f * s))
            drawLine(Color(0xFFE9E4DA).copy(alpha = lock), Offset(bc.x - sx, bc.y - 0.065f * s - lift), Offset(bc.x - sx, bc.y - 0.02f * s), 0.03f * s)
            drawLine(Color(0xFFE9E4DA).copy(alpha = lock), Offset(bc.x + sx, bc.y - 0.065f * s - lift), Offset(bc.x + sx, bc.y - 0.02f * s), 0.03f * s)
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFFFD66B).copy(alpha = lock), Color(0xFFD99A1A).copy(alpha = lock)), startY = bc.y - 0.03f * s, endY = bc.y + 0.14f * s),
                Offset(bc.x - 0.12f * s, bc.y - 0.03f * s), Size(0.24f * s, 0.17f * s), CornerRadius(0.04f * s)
            )
            drawCircle(INK.copy(alpha = lock), 0.022f * s, Offset(bc.x, bc.y + 0.04f * s))
            drawRoundRect(INK.copy(alpha = lock), Offset(bc.x - 0.009f * s, bc.y + 0.045f * s), Size(0.018f * s, 0.045f * s), CornerRadius(0.009f * s))
        }
    }
}

// --- Scenes ------------------------------------------------------------------

/** Page 1: three bank cards fan out behind the character, and rupee coins bob around it. */
@Composable
private fun BankScene(shown: Boolean, clock: State<Float>) {
    val fan = rememberPop(shown, 120)
    val cards = listOf(Color(0xFFA77CF0) to Color(0xFF6A3FC4), Color(0xFF5E9BF2) to Color(0xFF2B5BC4), Color(0xFFF58A55) to Color(0xFFD13F2B))
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        cards.forEachIndexed { i, (a, b) ->
            val side = i - 1
            MiniCard(a, b, Modifier.graphicsLayer {
                val f = fan.value
                translationX = side * 80.dp.toPx() * f
                translationY = -84.dp.toPx() + abs(side) * 16.dp.toPx() * f + sin(clock.value * 1.6f + i) * 3.dp.toPx()
                rotationZ = side * 20f * f
                alpha = f.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0.5f, 1f)
            })
        }
        Coin((-112).dp, 64.dp, 0f, shown, 300, clock)
        Coin(114.dp, 30.dp, 2f, shown, 380, clock)
        Coin(96.dp, 104.dp, 4f, shown, 460, clock, 28.dp)
    }
}

@Composable
private fun MiniCard(a: Color, b: Color, modifier: Modifier) {
    Box(
        modifier
            .bounceClick(0.9f) {}
            .size(116.dp, 74.dp)
            .background(Brush.linearGradient(listOf(a, b)), RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Box(Modifier.size(22.dp, 16.dp).background(Brush.linearGradient(listOf(Color(0xFFFFE08A), GOLD)), RoundedCornerShape(4.dp)))
        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(64.dp, 6.dp).background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(3.dp)))
            Box(Modifier.size(36.dp, 6.dp).background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(3.dp)))
        }
    }
}

/** A gold rupee coin that bobs, and spins when tapped. */
@Composable
private fun Coin(x: Dp, y: Dp, phase: Float, shown: Boolean, delayMs: Long, clock: State<Float>, size: Dp = 34.dp) {
    val pop = rememberPop(shown, delayMs)
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer {
                translationX = x.toPx()
                translationY = y.toPx() + sin(clock.value * 2.4f + phase) * 6.dp.toPx()
                scaleX = pop.value; scaleY = pop.value
                rotationY = spin.value
                cameraDistance = 12f * density
            }
            .bounceClick(0.85f) { scope.launch { spin.animateTo(spin.value + 360f, spring(0.8f, 120f)) } }
            .size(size)
            .background(Brush.linearGradient(listOf(Color(0xFFFFD66B), Color(0xFFE0A21C))), CircleShape)
            .border(2.dp, Color(0xFFFFEBB0), CircleShape)
    ) { Text("₹", color = Color(0xFF8A5A00), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.47f).sp) }
}

private class IntroTile(val icon: ImageVector, val label: String, val color: Color, val x: Dp, val y: Dp)

private val TILES = listOf(
    IntroTile(Icons.Rounded.CreditCard, "Debit Card", Color(0xFF3B7BE0), (-104).dp, (-88).dp),
    IntroTile(Icons.Rounded.Payments, "Credit Card", Color(0xFF8A55E0), 104.dp, (-88).dp),
    IntroTile(Icons.AutoMirrored.Rounded.MenuBook, "Passbook", Color(0xFFD9932B), (-104).dp, 76.dp),
    IntroTile(Icons.Rounded.ReceiptLong, "Cheque", Color(0xFF1E9E8A), 104.dp, 76.dp)
)

/** Page 2: the four photo types fly out of the character in their own colours and float; tap one to wiggle it. */
@Composable
private fun DocumentScene(shown: Boolean, clock: State<Float>) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TILES.forEachIndexed { i, tile ->
            val pop = rememberPop(shown, 120L + i * 90L)
            val wiggle = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .graphicsLayer {
                        val p = pop.value
                        translationX = tile.x.toPx() * p
                        translationY = tile.y.toPx() * p + sin(clock.value * 1.8f + i * 1.3f) * 5.dp.toPx()
                        rotationZ = sin(clock.value * 1.3f + i) * 4f + wiggle.value
                        scaleX = p; scaleY = p
                    }
                    .bounceClick(0.88f) {
                        scope.launch {
                            for (r in floatArrayOf(-14f, 12f, -8f, 5f)) wiggle.animateTo(r, tween(60))
                            wiggle.animateTo(0f, spring(0.4f, 400f))
                        }
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(58.dp)
                        .background(Brush.linearGradient(listOf(lerp(tile.color, Color.White, 0.28f), tile.color)), RoundedCornerShape(18.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                ) { Icon(tile.icon, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.height(5.dp))
                Text(tile.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
        }
    }
}

private class IntroBadge(val icon: ImageVector, val label: String, val x: Dp, val y: Dp)

private val BADGES = listOf(
    IntroBadge(Icons.Rounded.Lock, "PIN only", (-96).dp, (-104).dp),
    IntroBadge(Icons.Rounded.VisibilityOff, "Private", 100.dp, (-60).dp),
    IntroBadge(Icons.Rounded.CloudDone, "Your Drive", (-92).dp, 96.dp)
)

private val SPARKS = listOf(-128f to -40f, 128f to 20f, -60f to -128f, 70f to -118f, 120f to 110f, -130f to 60f)

/** Page 3: safety rings pulse out of the character, sparkles twinkle, and badges float by. */
@Composable
private fun LockScene(shown: Boolean, clock: State<Float>) {
    val scheme = MaterialTheme.colorScheme
    val on = rememberPop(shown, 150)
    val green = Color(0xFF1E9E8A)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value
            val o = on.value.coerceIn(0f, 1f)
            val base = 74.dp.toPx()
            for (i in 0 until 3) {
                val k = (t * 0.45f + i / 3f) % 1f
                drawCircle(green.copy(alpha = (1f - k) * 0.4f * o), base + k * 72.dp.toPx(), center, style = Stroke(2.dp.toPx()))
            }
            SPARKS.forEachIndexed { i, (fx, fy) ->
                val tw = 0.55f + 0.45f * sin(t * 2.6f + i * 1.7f)
                sparkle(Offset(center.x + fx.dp.toPx(), center.y + fy.dp.toPx()), (6 + (i % 3) * 3).dp.toPx() * tw * o, if (i % 2 == 0) GOLD else green)
            }
        }
        BADGES.forEachIndexed { i, b ->
            val pop = rememberPop(shown, 250L + i * 110L)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = b.x.toPx()
                        translationY = b.y.toPx() + sin(clock.value * 1.7f + i * 2f) * 5.dp.toPx()
                        scaleX = pop.value; scaleY = pop.value
                    }
                    .bounceClick(0.9f) {}
                    .background(scheme.surface, RoundedCornerShape(14.dp))
                    .border(1.dp, green.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Icon(b.icon, null, tint = green, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(b.label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurface, maxLines = 1)
            }
        }
    }
}

/** A four-pointed twinkle. */
private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    if (r <= 0.5f) return
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, color)
}
