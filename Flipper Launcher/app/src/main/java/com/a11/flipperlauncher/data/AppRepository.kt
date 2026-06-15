package com.a11.flipperlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the set of launchable apps and performs launcher-level intents. */
class AppRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val main = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val self = context.packageName
        val resolved = runCatching { pm.queryIntentActivities(main, 0) }.getOrDefault(emptyList())

        resolved.asSequence()
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == self) return@mapNotNull null
                val label = runCatching { ri.loadLabel(pm).toString() }
                    .getOrNull()?.takeIf { it.isNotBlank() } ?: pkg
                val version = runCatching {
                    @Suppress("DEPRECATION") pm.getPackageInfo(pkg, 0).versionName
                }.getOrNull() ?: "?"
                val icon = runCatching { ri.loadIcon(pm) }.getOrNull()
                AppInfo(label, pkg, version, icon.toImageBitmapOrNull(ICON_PX))
            }
            .distinctBy { it.packageName }
            .sortedBy { it.sortKey }
            .toList()
    }

    fun launch(pkg: String): Boolean = runCatching {
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    fun openAppDetails(pkg: String) = startSafely(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
    )

    fun uninstall(pkg: String) = startSafely(
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))
    )

    fun openSettings(action: String) = startSafely(Intent(action))

    fun openUrl(url: String) = startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun startSafely(intent: Intent): Boolean = runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    companion object {
        private const val ICON_PX = 132
    }
}

private fun Drawable?.toImageBitmapOrNull(px: Int): ImageBitmap? = runCatching {
    this?.toBitmap(px, px)?.asImageBitmap()
}.getOrNull()
