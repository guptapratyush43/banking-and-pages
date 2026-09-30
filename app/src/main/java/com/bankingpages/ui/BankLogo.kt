package com.bankingpages.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bankingpages.logo.LogoStore

/**
 * A bank's logo sitting whole inside a white circle. The logo is inset by about
 * 15% on each side, which is exactly what a square needs to fit inside a circle
 * with its corners untouched, so nothing is ever cropped. No logo yet? The
 * bank's initials stand in, and the real logo fades in when it arrives.
 */
@Composable
fun BankLogo(key: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    val version by LogoStore.version.collectAsStateWithLifecycle()
    val logo by produceState(LogoStore.cachedOrNull(key), key, version) { value = LogoStore.load(key) }
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (logo != null) (if (dark) Color(0xFFF7F5EF) else Color.White) else scheme.primaryContainer, CircleShape)
            .border(0.5.dp, scheme.outline, CircleShape)
    ) {
        Crossfade(logo, animationSpec = tween(260), label = "logo") { img ->
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = "$name logo",
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize().padding(size * 0.16f)
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(initials(name), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = (size.value * 0.34f).sp, color = scheme.primary, maxLines = 1)
                }
            }
        }
    }
}

fun initials(name: String): String {
    val skip = setOf("bank", "of", "the", "and", "&", "co-operative", "ltd", "limited")
    val words = name.split(Regex("\\s+")).filter { it.isNotEmpty() && it.lowercase() !in skip }
    return (words.take(2).joinToString("") { it.take(1) }.ifEmpty { name.take(2) }).uppercase()
}
