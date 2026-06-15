package com.a11.flipperlauncher.data

import androidx.compose.ui.graphics.ImageBitmap

/** One launchable app, with its icon pre-rasterized for Compose. */
data class AppInfo(
    val label: String,
    val packageName: String,
    val versionName: String,
    val icon: ImageBitmap?,
) {
    val sortKey: String = label.trim().lowercase()

    /** Section letter for the A–Z drawer. Non A–Z starts group under '#'. */
    val initial: Char = label.trim().firstOrNull()?.uppercaseChar()
        ?.let { if (it in 'A'..'Z') it else '#' } ?: '#'
}
