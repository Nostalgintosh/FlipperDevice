package com.a11.flipperlauncher.data

import android.content.Context

/** Tiny SharedPreferences-backed store for dock pins, aliases, notes, history, theme. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("fzsh", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = sp.read(K_FAV)
        set(value) = sp.write(K_FAV, value)

    var notes: List<String>
        get() = sp.read(K_NOTES)
        set(value) = sp.write(K_NOTES, value)

    var history: List<String>
        get() = sp.read(K_HIST)
        set(value) = sp.write(K_HIST, value.takeLast(200))

    var accent: String
        get() = sp.getString(K_ACCENT, "orange") ?: "orange"
        set(value) { sp.edit().putString(K_ACCENT, value).apply() }

    var lastCodeFile: String?
        get() = sp.getString(K_LASTCODE, null)
        set(value) { sp.edit().putString(K_LASTCODE, value).apply() }

    /** "fzsh" or "powershell" — the terminal skin, restored across launches. */
    var terminalMode: String
        get() = sp.getString(K_MODE, "fzsh") ?: "fzsh"
        set(value) { sp.edit().putString(K_MODE, value).apply() }

    var widgetIds: List<Int>
        get() = sp.read(K_WGT).mapNotNull { it.toIntOrNull() }
        set(value) = sp.write(K_WGT, value.map { it.toString() })

    var aliases: Map<String, String>
        get() = sp.read(K_ALIAS)
            .filter { it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        set(value) = sp.write(K_ALIAS, value.map { "${it.key}=${it.value}" })

    private fun android.content.SharedPreferences.read(key: String): List<String> =
        getString(key, "").orEmpty().split(SEP).filter { it.isNotEmpty() }

    private fun android.content.SharedPreferences.write(key: String, value: List<String>) {
        edit().putString(key, value.joinToString(SEP)).apply()
    }

    companion object {
        private const val SEP = "\u0001"
        private const val K_FAV = "favorites"
        private const val K_NOTES = "notes"
        private const val K_HIST = "history"
        private const val K_ACCENT = "accent"
        private const val K_ALIAS = "aliases"
        private const val K_WGT = "widget_ids"
        private const val K_LASTCODE = "last_code_file"
        private const val K_MODE = "terminal_mode"
    }
}
