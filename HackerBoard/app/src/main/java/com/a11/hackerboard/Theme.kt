package com.a11.hackerboard

import android.content.Context

/** Resolved colors handed to the keyboard view. */
data class KbTheme(
    val bg: Int,
    val key: Int,
    val keyDown: Int,
    val keyMod: Int,
    val text: Int,
    val textDim: Int,
    val accent: Int,
    val border: Int,
    val onAccent: Int,
)

/** Build an opaque color from components — guarantees alpha = 0xFF without
 *  relying on how 8-digit hex literals are typed/sign-extended. */
fun rgb(r: Int, g: Int, b: Int): Int =
    (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

/** A full palette. Most themes share a GitHub-dark base; Flipper goes pure
 *  black with Flipper-Zero orange (#FF8200), the headline "hacker" look. */
data class ThemeSpec(
    val name: String,
    val bg: Int,
    val key: Int,
    val keyMod: Int,
    val border: Int,
    val text: Int,
    val textDim: Int,
    val accent: Int,
    val onAccent: Int,
)

object Themes {
    val list: List<ThemeSpec> = listOf(
        ThemeSpec(
            "Flipper",
            bg = rgb(0x00, 0x00, 0x00),
            key = rgb(0x0E, 0x0E, 0x0E),
            keyMod = rgb(0x1A, 0x12, 0x06),
            border = rgb(0x7A, 0x46, 0x08),
            text = rgb(0xFF, 0x82, 0x00),
            textDim = rgb(0x9A, 0x5A, 0x14),
            accent = rgb(0xFF, 0x82, 0x00),
            onAccent = rgb(0x00, 0x00, 0x00),
        ),
        ThemeSpec(
            "Matrix",
            bg = rgb(0x0D, 0x11, 0x17),
            key = rgb(0x16, 0x1B, 0x22),
            keyMod = rgb(0x21, 0x26, 0x2D),
            border = rgb(0x30, 0x36, 0x3D),
            text = rgb(0xE6, 0xED, 0xF3),
            textDim = rgb(0x8B, 0x94, 0x9E),
            accent = rgb(0x3F, 0xB9, 0x50),
            onAccent = rgb(0x0D, 0x11, 0x17),
        ),
        ThemeSpec(
            "Cyan",
            bg = rgb(0x0D, 0x11, 0x17),
            key = rgb(0x16, 0x1B, 0x22),
            keyMod = rgb(0x21, 0x26, 0x2D),
            border = rgb(0x30, 0x36, 0x3D),
            text = rgb(0xE6, 0xED, 0xF3),
            textDim = rgb(0x8B, 0x94, 0x9E),
            accent = rgb(0x39, 0xD0, 0xD8),
            onAccent = rgb(0x0D, 0x11, 0x17),
        ),
        ThemeSpec(
            "Amber",
            bg = rgb(0x0D, 0x11, 0x17),
            key = rgb(0x16, 0x1B, 0x22),
            keyMod = rgb(0x21, 0x26, 0x2D),
            border = rgb(0x30, 0x36, 0x3D),
            text = rgb(0xE6, 0xED, 0xF3),
            textDim = rgb(0x8B, 0x94, 0x9E),
            accent = rgb(0xF0, 0xA0, 0x30),
            onAccent = rgb(0x0D, 0x11, 0x17),
        ),
        ThemeSpec(
            "Magenta",
            bg = rgb(0x0D, 0x11, 0x17),
            key = rgb(0x16, 0x1B, 0x22),
            keyMod = rgb(0x21, 0x26, 0x2D),
            border = rgb(0x30, 0x36, 0x3D),
            text = rgb(0xE6, 0xED, 0xF3),
            textDim = rgb(0x8B, 0x94, 0x9E),
            accent = rgb(0xD2, 0x4B, 0xE0),
            onAccent = rgb(0x0D, 0x11, 0x17),
        ),
    )
}

fun buildTheme(themeIndex: Int): KbTheme {
    val t = Themes.list[themeIndex.coerceIn(0, Themes.list.lastIndex)]
    return KbTheme(
        bg = t.bg,
        key = t.key,
        keyDown = t.accent,
        keyMod = t.keyMod,
        text = t.text,
        textDim = t.textDim,
        accent = t.accent,
        border = t.border,
        onAccent = t.onAccent,
    )
}

/** Tiny typed wrapper over SharedPreferences. */
object Prefs {
    private const val FILE = "hackerboard"
    const val KEY_THEME = "theme"
    const val KEY_HAPTICS = "haptics"
    const val KEY_SOUND = "sound"
    const val KEY_HEIGHT = "key_height_dp"

    fun of(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun themeIndex(ctx: Context) = of(ctx).getInt(KEY_THEME, 0).coerceIn(0, Themes.list.lastIndex)
    fun haptics(ctx: Context) = of(ctx).getBoolean(KEY_HAPTICS, true)
    fun sound(ctx: Context) = of(ctx).getBoolean(KEY_SOUND, false)
    fun keyHeightDp(ctx: Context) = of(ctx).getInt(KEY_HEIGHT, 52).coerceIn(40, 72)
}
