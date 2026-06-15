package com.a11.flipperlauncher.terminal

import com.a11.flipperlauncher.data.AppInfo
import com.a11.flipperlauncher.data.WidgetProvider

/** Visual role of a printed line — drives color in the UI. */
enum class LineKind { INPUT, OUT, OK, ERR, MUTED, ACCENT, ASCII, HEAD }

/** Which shell skin the terminal is wearing. `-pp` enters PowerShell, `-fz` returns. */
enum class TerminalMode { Fzsh, PowerShell }

/** A side effect the host (ViewModel) carries out after a command runs. */
sealed interface TermAction {
    data class Launch(val pkg: String, val label: String) : TermAction
    data class AppDetails(val pkg: String) : TermAction
    data class Uninstall(val pkg: String) : TermAction
    data class OpenSettings(val settingsAction: String) : TermAction
    data class OpenUrl(val url: String) : TermAction
    data class Pin(val pkg: String) : TermAction
    data class Unpin(val pkg: String) : TermAction
    data class SetAccent(val name: String) : TermAction
    data class AddAlias(val name: String, val value: String) : TermAction
    data class RemoveAlias(val name: String) : TermAction
    data class AddNote(val text: String) : TermAction
    /** index < 0 clears every note. */
    data class RemoveNote(val index: Int) : TermAction
    /** [query] is a flattened ComponentName or a fuzzy provider query. */
    data class AddWidget(val query: String) : TermAction
    data class RemoveWidget(val index: Int) : TermAction
    /** Open (creating if absent) a file in the Code editor and jump to it. */
    data class OpenInEditor(val name: String) : TermAction
    /** Ask the host to request shared-storage access for the code workspace. */
    data object RequestStorage : TermAction
    data object ClearScreen : TermAction
    data object GoToApps : TermAction
    /** Jump the pager to the Code editor page. */
    data object GoToCode : TermAction
    data object Reboot : TermAction
}

/** A single rendered terminal line. If [action] is non-null the line is tappable. */
data class TermLine(
    val text: String,
    val kind: LineKind = LineKind.OUT,
    val action: TermAction? = null,
)

data class EngineResult(
    val lines: List<TermLine>,
    val actions: List<TermAction> = emptyList(),
)

/** Live device readout passed into the engine each command. */
data class DeviceSnapshot(
    val time: String,
    val date: String,
    val batteryPct: Int,
    val charging: Boolean,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val uptime: String,
)

/** Everything the engine reads for one command. Writes go out as [TermAction]s. */
data class TermEnv(
    val apps: List<AppInfo>,
    val favorites: List<String>,
    val aliases: Map<String, String>,
    val notes: List<String>,
    val history: List<String>,
    val accent: String,
    val device: DeviceSnapshot,
    /** Every widget type installed on the device. */
    val widgetProviders: List<WidgetProvider> = emptyList(),
    /** Labels of widgets currently embedded in the terminal, in tray order. */
    val widgets: List<String> = emptyList(),
    /** Files in the Code editor workspace, so the terminal can list/open them. */
    val codeFiles: List<String> = emptyList(),
)
