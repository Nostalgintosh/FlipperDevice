package com.a11.flipperlauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.a11.flipperlauncher.R
import com.a11.flipperlauncher.terminal.LineKind

/** Flipper Zero palette — black chassis, orange backlight. */
object Fz {
    val Black = Color(0xFF0A0A0A)
    val Surface = Color(0xFF121212)
    val SurfaceHi = Color(0xFF1B1B1B)
    val Divider = Color(0xFF272727)
    val Text = Color(0xFFE6E6E6)
    val Muted = Color(0xFF7C7C7C)

    val Orange = Color(0xFFFF8200)
    val Green = Color(0xFF35FF6A)
    val Amber = Color(0xFFFFB000)
    val Red = Color(0xFFFF5C5C)
    val Cyan = Color(0xFF35E0FF)
    val White = Color(0xFFEDEDED)

    // PowerShell mode skin (`-pp`) — classic navy console, light text, PS yellow prompt.
    // The Flipper orange accent + pixel chrome stay on top, so it reads as
    // "Flipper Zero wearing a Windows skin."
    val PsBlue = Color(0xFF012456)
    val PsBlueHi = Color(0xFF06224D)
    val PsText = Color(0xFFEEEDF0)
    val PsPrompt = Color(0xFFF3F99D)
}

/** Terminal body + anything needing column alignment / box-drawing glyphs. */
val Mono: FontFamily = FontFamily.Monospace

/**
 * Silkscreen pixel font (OFL) used for "chrome" only — status bar, tabs, section
 * letters, the A–Z bubble. Pixel fonts lack box-drawing/block glyphs, so the
 * terminal body deliberately stays on [Mono].
 */
val Pixel: FontFamily = FontFamily(Font(R.font.silkscreen))

val LocalAccent = staticCompositionLocalOf { Fz.Orange }

fun accentColor(name: String): Color = when (name) {
    "green" -> Fz.Green
    "amber" -> Fz.Amber
    "red" -> Fz.Red
    "cyan" -> Fz.Cyan
    "mono" -> Fz.White
    else -> Fz.Orange
}

fun kindColor(kind: LineKind, accent: Color): Color = when (kind) {
    LineKind.INPUT -> accent
    LineKind.OK -> Fz.Green
    LineKind.ERR -> Fz.Red
    LineKind.MUTED -> Fz.Muted
    LineKind.ACCENT -> accent
    LineKind.HEAD -> accent
    LineKind.ASCII -> accent
    LineKind.OUT -> Fz.Text
}

@Composable
fun FlipperTheme(accentName: String, content: @Composable () -> Unit) {
    val accent = accentColor(accentName)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Fz.Black,
        background = Fz.Black,
        onBackground = Fz.Text,
        surface = Fz.Surface,
        onSurface = Fz.Text,
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
