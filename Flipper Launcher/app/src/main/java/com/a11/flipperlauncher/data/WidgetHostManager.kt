package com.a11.flipperlauncher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context

/** One installable widget type, flattened for the terminal engine. */
data class WidgetProvider(
    val label: String,
    val packageName: String,
    /** Flattened ComponentName — stable handle passed through TermActions. */
    val component: String,
    val hasConfig: Boolean,
)

/**
 * Wraps [AppWidgetHost]/[AppWidgetManager] so the launcher can embed real
 * app widgets (Termux:Widget, clocks, …) inside the terminal page.
 */
class WidgetHostManager(private val context: Context) {

    val host = AppWidgetHost(context, HOST_ID)
    private val manager = AppWidgetManager.getInstance(context)

    fun providers(): List<WidgetProvider> = runCatching {
        manager.installedProviders.map { info ->
            WidgetProvider(
                label = runCatching { info.loadLabel(context.packageManager) }.getOrNull()
                    ?: info.provider.shortClassName.trimStart('.'),
                packageName = info.provider.packageName,
                component = info.provider.flattenToString(),
                hasConfig = info.configure != null,
            )
        }.sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())

    fun info(id: Int): AppWidgetProviderInfo? =
        runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun allocate(): Int = host.allocateAppWidgetId()

    /** True if bound without needing the system permission dialog. */
    fun bind(id: Int, component: String): Boolean {
        val cn = ComponentName.unflattenFromString(component) ?: return false
        return runCatching { manager.bindAppWidgetIdIfAllowed(id, cn) }.getOrDefault(false)
    }

    fun delete(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
    }

    fun createView(uiContext: Context, id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        return runCatching { host.createView(uiContext, id, info) }.getOrNull()
    }

    fun startListening() {
        runCatching { host.startListening() }
    }

    fun stopListening() {
        runCatching { host.stopListening() }
    }

    companion object {
        private const val HOST_ID = 0xFA11
    }
}
