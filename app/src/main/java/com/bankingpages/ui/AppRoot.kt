package com.bankingpages.ui

import androidx.compose.animation.core.VisibilityThreshold
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.snap
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import android.os.Build
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bankingpages.ui.motion.bounceClick
import com.bankingpages.ui.theme.AccentBrush
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bankingpages.data.Account
import com.bankingpages.data.AppSettings
import com.bankingpages.data.BankCatalog
import com.bankingpages.data.Pin
import com.bankingpages.data.Vault
import com.bankingpages.logo.LogoStore
import com.bankingpages.ui.motion.LocalReduceMotion
import com.bankingpages.ui.motion.Motion
import com.bankingpages.ui.motion.rememberReduceMotion
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

sealed class Screen(val depth: Int) {
    data object Home : Screen(0)
    data object Picker : Screen(1)
    data class Detail(val id: String) : Screen(1)
    data class DocView(val id: String) : Screen(1)
    data class Editor(val id: String?, val bank: PickedBank?) : Screen(2)
    data class Photo(val id: String, val blobId: String, val label: String) : Screen(2)
}

class NavViewModel : ViewModel() {
    val screen = MutableStateFlow<Screen>(Screen.Home)
    /** 0 is the vault, 1 the profile. */
    val tab = MutableStateFlow(0)
    /** Items that have already made their entrance; kept so returning never replays it. */
    val seen = mutableSetOf<Any>()
    /** Where each open bank page was scrolled to, so coming back from a photo lands in the same place. */
    val detailScroll = mutableMapOf<String, androidx.compose.foundation.ScrollState>()
    /** What has already risen into place on an open bank page, so coming back shows it at once with nothing replayed. */
    val detailSeen = mutableMapOf<String, MutableSet<Any>>()
}

@Composable
fun AppRoot(nav: NavViewModel) {
    val settings by AppSettings.state.collectAsStateWithLifecycle()
    val unlocked by Pin.unlocked.collectAsStateWithLifecycle()
    val context = LocalContext.current

    CompositionLocalProvider(LocalReduceMotion provides rememberReduceMotion()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            if (!settings.onboarded || !Pin.isSet) {
                OnboardingScreen(onDone = { AppSettings.setOnboarded() })
                return@Box
            }
            // The app underneath stays composed while locked (so an edit in progress survives),
            // but only starts after the first unlock, so its entrance plays where you can see it.
            var everUnlocked by rememberSaveable { mutableStateOf(unlocked) }
            LaunchedEffect(unlocked) { if (unlocked) everUnlocked = true }
            if (everUnlocked) MainNav(nav)
            // The update pop-up waits until the app is unlocked.
            val update by com.bankingpages.update.UpdateManager.offer.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { runCatching { com.bankingpages.update.UpdateManager.checkOnLaunch() } }
            if (unlocked) update?.let { UpdateDialog(it) }

            val lockState = remember { MutableTransitionState(!unlocked) }
            lockState.targetState = !unlocked
            AnimatedVisibility(
                visibleState = lockState,
                enter = EnterTransition.None, // locking is instant: nothing private flashes by
                exit = fadeOut(tween(220)) + scaleOut(targetScale = 1.04f, animationSpec = tween(220))
            ) {
                LockScreen(
                    fingerprintOn = settings.fingerprint && fingerprintStatus(context) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS,
                    onFingerprint = { (context as? FragmentActivity)?.let { askFingerprint(it, "Unlock Banking and Pages") { Pin.unlockWithFingerprint() } } },
                    onForgot = { eraseEverything(context) }
                )
            }
        }
    }
}

@Composable
private fun MainNav(nav: NavViewModel) {
    val screen by nav.screen.collectAsStateWithLifecycle()
    val tab by nav.tab.collectAsStateWithLifecycle()
    val accounts by Vault.accounts.collectAsStateWithLifecycle()
    val docs by Vault.docs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun go(s: Screen) { nav.screen.value = s }
    val home = { go(Screen.Home) }
    val adder = rememberDocAdder { doc -> nav.tab.value = 0; toast(context, "${doc.title} added") }

    BackHandler(enabled = screen != Screen.Home || tab != 0) {
        when (val s = screen) {
            Screen.Home -> nav.tab.value = 0
            is Screen.Photo -> go(Screen.Detail(s.id))
            is Screen.Editor -> go(s.id?.let { Screen.Detail(it) } ?: Screen.Home)
            else -> home()
        }
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            // iOS-style push: the new screen slides over, the old one drifts a quarter behind.
            // Screens are opaque and never faded, and z-order follows direction, so nothing bleeds through.
            val spec = Motion.push(IntOffset.VisibilityThreshold)
            val forward = targetState.depth >= initialState.depth
            if (forward) (slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 4 }).apply { targetContentZIndex = 1f }
            else (slideInHorizontally(spec) { -it / 4 } togetherWith slideOutHorizontally(spec) { it }).apply { targetContentZIndex = -1f }
        },
        contentKey = { it::class to ((it as? Screen.Detail)?.id ?: (it as? Screen.Editor)?.id ?: (it as? Screen.Photo)?.blobId ?: (it as? Screen.DocView)?.id) },
        label = "screens"
    ) { s ->
        when (s) {
            Screen.Home -> HomeShell(
                tab = tab, onTab = { nav.tab.value = it },
                vault = {
                    HomeScreen(
                        accounts = accounts,
                        docs = docs,
                        seen = remember { mutableSetOf() },
                        onAddBank = { go(Screen.Picker) },
                        onOpenAccount = { nav.detailScroll.remove(it.id); nav.detailSeen.remove(it.id); go(Screen.Detail(it.id)) },
                        onScanDoc = adder.scan,
                        onImportDoc = adder.import,
                        onOpenDoc = { go(Screen.DocView(it.id)) }
                    )
                }
            )
            Screen.Picker -> BankPickerScreen(onBack = home, onPick = { go(Screen.Editor(null, it)) })
            is Screen.Editor -> {
                val existing = s.id?.let(Vault::account)
                val initial = remember(s) {
                    existing ?: Account(
                        id = Vault.newId(), bankId = s.bank?.id ?: "other", bankName = s.bank?.name ?: "Bank",
                        bankDomain = s.bank?.takeIf { it.id.startsWith("c_") }?.domain
                    )
                }
                EditorScreen(
                    initial = initial,
                    isNew = existing == null,
                    onBack = { go(s.id?.let { Screen.Detail(it) } ?: Screen.Home) },
                    onSave = { a ->
                        Vault.saveAccount(a)
                        LogoStore.ensure(a.bankId, a.bankDomain ?: BankCatalog.get(a.bankId)?.domain)
                        if (existing == null) { nav.tab.value = 0; toast(context, "${a.bankName} saved"); home() }
                        else { toast(context, "Saved"); go(Screen.Detail(a.id)) }
                    }
                )
            }
            is Screen.Detail -> {
                val a = accounts.firstOrNull { it.id == s.id }
                if (a == null) LaunchedEffect(Unit) { home() }
                else DetailScreen(
                    a, scroll = nav.detailScroll.getOrPut(a.id) { androidx.compose.foundation.ScrollState(0) },
                    seen = nav.detailSeen.getOrPut(a.id) { mutableSetOf() }, onBack = home,
                    onEdit = { go(Screen.Editor(a.id, null)) },
                    onDelete = { Vault.deleteAccount(a.id); toast(context, "${a.bankName} deleted"); home() },
                    onOpenPhoto = { blob, label -> go(Screen.Photo(a.id, blob, label)) }
                )
            }
            is Screen.Photo -> {
                val a = accounts.firstOrNull { it.id == s.id }
                if (a == null || s.blobId !in a.allBlobs) LaunchedEffect(Unit) { home() }
                else PhotoViewer(s.label, a.bankName, s.blobId, "${a.bankName} - ${s.label}.jpg", onBack = { go(Screen.Detail(a.id)) })
            }
            is Screen.DocView -> {
                val d = docs.firstOrNull { it.id == s.id }
                if (d == null) LaunchedEffect(Unit) { home() }
                else DocViewer(d, onBack = home, onDelete = { Vault.deleteDoc(d.id); toast(context, "${d.title} deleted"); home() })
            }
        }
    }
}

/** The two tabs with the glass bar floating over them. The bar samples whatever scrolls beneath it. */
@Composable
private fun HomeShell(tab: Int, onTab: (Int) -> Unit, vault: @Composable () -> Unit) {
    val backdrop = rememberLayerBackdrop()
    var covered by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Tabs swap in place with a soft fade; each one's cards then rise in, every time you arrive.
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(220, delayMillis = 70)) togetherWith fadeOut(tween(110)) },
            modifier = Modifier.fillMaxSize().layerBackdrop(backdrop),
            label = "tabs"
        ) { t -> if (t == 0) vault() else SettingsScreen(onOverlay = { covered = it }) }

        AnimatedVisibility(
            visible = !covered || tab == 0,
            enter = slideInVertically(Motion.bouncy(IntOffset.VisibilityThreshold)) { it * 2 },
            exit = slideOutVertically(Motion.push(IntOffset.VisibilityThreshold)) { it * 2 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) { GlassBar(backdrop, tab, onTab) }
    }
}

private val TabWidth = 112.dp
private val TabHeight = 54.dp

/**
 * The floating Liquid Glass tab bar. On Android 12+ it blurs and bends what is behind
 * it; older phones get a near-solid surface so the labels stay readable.
 */
@Composable
private fun GlassBar(backdrop: LayerBackdrop, selected: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val glass = Build.VERSION.SDK_INT >= 31
    val surface = scheme.surface.copy(alpha = if (glass) 0.4f else 0.96f)
    val x by animateDpAsState(TabWidth * selected, Motion.bouncy(Dp.VisibilityThreshold), label = "tabPill")
    Box(
        Modifier
            .padding(bottom = 18.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(24.dp) },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(14.dp.toPx(), 28.dp.toPx())
                },
                onDrawSurface = { drawRect(surface) }
            )
            .padding(6.dp)
    ) {
        Box(Modifier.offset { IntOffset(x.roundToPx(), 0) }.size(TabWidth, TabHeight).clip(RoundedCornerShape(18.dp)).background(AccentBrush).gloss(RoundedCornerShape(18.dp)))
        Row {
            listOf(Icons.Rounded.AccountBalanceWallet to "Vault", Icons.Rounded.Person to "Profile").forEachIndexed { i, (icon, label) ->
                val tint by animateColorAsState(if (i == selected) Color.White else scheme.onSurfaceVariant, label = "tabTint")
                Row(
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.size(TabWidth, TabHeight).bounceClick(0.9f) { onSelect(i) }
                ) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
                }
            }
        }
    }
}

/** "Forgot PIN": wipes the vault, logos and settings on this phone, then starts fresh. */
private fun eraseEverything(context: Context) {
    val app = context.applicationContext
    File(app.filesDir, "vault").deleteRecursively()
    File(app.filesDir, "logos").deleteRecursively()
    listOf("pin", "settings", "backup", "logos").forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    val restart = app.packageManager.getLaunchIntentForPackage(app.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    app.startActivity(restart)
    (context as? Activity)?.finishAffinity()
    Runtime.getRuntime().exit(0)
}
