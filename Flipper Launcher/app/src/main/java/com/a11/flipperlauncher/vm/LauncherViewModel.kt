package com.a11.flipperlauncher.vm

import android.app.Application
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.a11.flipperlauncher.R
import com.a11.flipperlauncher.data.AppInfo
import com.a11.flipperlauncher.data.AppRepository
import com.a11.flipperlauncher.data.CodeRepository
import com.a11.flipperlauncher.data.Prefs
import com.a11.flipperlauncher.data.SystemMonitor
import com.a11.flipperlauncher.data.SystemStats
import com.a11.flipperlauncher.data.WidgetHostManager
import com.a11.flipperlauncher.exec.ShellBridge
import com.a11.flipperlauncher.exec.SshBridge
import com.a11.flipperlauncher.exec.TermuxBridge
import com.a11.flipperlauncher.terminal.DeviceSnapshot
import com.a11.flipperlauncher.terminal.LineKind
import com.a11.flipperlauncher.terminal.TermAction
import com.a11.flipperlauncher.terminal.TermEnv
import com.a11.flipperlauncher.terminal.TermLine
import com.a11.flipperlauncher.terminal.TerminalEngine
import com.a11.flipperlauncher.terminal.TerminalMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A widget allocation waiting on the system bind-permission dialog. */
data class PendingWidget(val id: Int, val component: String, val needsConfig: Boolean)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)
    private val prefs = Prefs(app)
    private val engine = TerminalEngine()
    val widgetHost = WidgetHostManager(app)

    // execution bridges: native android shell, Termux (linux), SSH (windows)
    private val shell = ShellBridge()
    private val termux = TermuxBridge(app)
    private val ssh = SshBridge(app)

    private val code = CodeRepository(app)
    private val monitor = SystemMonitor(app)

    // ---- observable state -------------------------------------------------
    var apps by mutableStateOf<List<AppInfo>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var query by mutableStateOf(""); private set
    var accent by mutableStateOf(prefs.accent); private set
    var favorites by mutableStateOf(prefs.favorites); private set
    var notes by mutableStateOf(prefs.notes); private set

    var batteryPct by mutableIntStateOf(100); private set
    var charging by mutableStateOf(false); private set

    /** Live device vitals (storage / RAM / CPU / network) for the status bar. */
    var stats by mutableStateOf(SystemStats()); private set

    /** Set by the engine when a command wants to jump the pager; UI consumes it. */
    var requestedPage by mutableStateOf<Int?>(null); private set

    /** True while the APPS A–Z drawer is shown as a pop-up over the terminal. */
    var showApps by mutableStateOf(false); private set

    /** Widgets embedded in the terminal tray, in display order. */
    var widgetIds by mutableStateOf<List<Int>>(emptyList()); private set
    /** Non-null while waiting for the system widget-bind dialog. */
    var pendingBind by mutableStateOf<PendingWidget?>(null); private set
    /** Non-null while a widget's configure activity should be launched. */
    var pendingConfig by mutableStateOf<Int?>(null); private set

    /** True while the next submitted line is captured as an SSH password (hidden, not stored). */
    var awaitingWinPassword by mutableStateOf(false); private set
    private var pendingWinTarget: Triple<String, String, Int>? = null

    /** Active terminal skin — fzsh (orange/black) or PowerShell (blue, routes to the win relay). */
    var terminalMode by mutableStateOf(
        if (prefs.terminalMode == "powershell") TerminalMode.PowerShell else TerminalMode.Fzsh
    ); private set

    // ---- code editor state ----
    var codeFiles by mutableStateOf<List<String>>(emptyList()); private set
    var codeName by mutableStateOf<String?>(null); private set
    var codeText by mutableStateOf(""); private set
    var codeDirty by mutableStateOf(false); private set
    val codeDirPath: String get() = code.dirPath

    /** Non-false while MainActivity should drive the shared-storage access request. */
    var pendingStorageRequest by mutableStateOf(false); private set

    val lines = mutableStateListOf<TermLine>()

    private var aliases: Map<String, String> = prefs.aliases
    private var history: List<String> = prefs.history

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
    private val dateFmt = SimpleDateFormat("EEE dd MMM yyyy", Locale.US)

    /** Flipper dolphin boot art (res/raw/splash.txt) — loaded once, kept exact. */
    private val splash: List<String> = runCatching {
        app.resources.openRawResource(R.raw.splash).bufferedReader().use { it.readLines() }
    }.getOrDefault(emptyList())

    init {
        // Drop widget ids whose provider was uninstalled while we were gone.
        val stored = prefs.widgetIds
        widgetIds = stored.filter { widgetHost.info(it) != null }
        if (widgetIds.size != stored.size) {
            (stored - widgetIds.toSet()).forEach { widgetHost.delete(it) }
            prefs.widgetIds = widgetIds
        }
        seedBanner()
        refreshApps()
        loadCodeWorkspace()
    }

    private fun loadCodeWorkspace() = viewModelScope.launch {
        val files = withContext(Dispatchers.IO) { code.list() }
        codeFiles = files
        prefs.lastCodeFile?.takeIf { it in files }?.let { last ->
            codeText = code.read(last)
            codeName = last
            codeDirty = false
        }
    }

    // ---- inputs from UI / system -----------------------------------------
    fun refreshApps() = viewModelScope.launch {
        loading = true
        apps = repo.loadApps()
        loading = false
    }

    fun updateQuery(q: String) { query = q }

    fun updateBattery(pct: Int, isCharging: Boolean) {
        batteryPct = pct; charging = isCharging
    }

    /** Sample device vitals; driven on a timer by the UI while it's visible. */
    suspend fun sampleStats() { stats = monitor.sample() }

    fun consumePageRequest() { requestedPage = null }

    /** Dismiss the APPS pop-up overlay. */
    fun hideApps() { showApps = false }

    /** HOME pressed while already foreground: close apps, snap back to the terminal, clear filter. */
    fun requestHome() { query = ""; requestedPage = 0; showApps = false }

    /** Run a typed command line — routes to fzsh, or the sh / lx / win bridges. */
    fun exec(raw: String) {
        val input = raw.trim()
        if (input.isEmpty()) return

        // Secure capture: the line after `win connect` is the password — never echoed or stored.
        if (awaitingWinPassword) {
            awaitingWinPassword = false
            val t = pendingWinTarget
            pendingWinTarget = null
            if (t != null) connectWindows(t.first, t.second, t.third, input)
            return
        }

        history = (history + input).takeLast(200)
        prefs.history = history

        // Shell-mode switches work from either skin, before normal dispatch.
        val lower = input.lowercase()
        if (lower == "-pp" || lower == "pwsh" || lower == "powershell") {
            echo(input); enterPowerShell(); trimLines(); return
        }
        if (lower == "-fz" || lower == "fzsh" ||
            (terminalMode == TerminalMode.PowerShell && (lower == "exit" || lower == "quit" || lower == "logout"))
        ) {
            echo(input); enterFlipper(); trimLines(); return
        }

        if (terminalMode == TerminalMode.PowerShell) {
            execPowerShell(input); trimLines(); return
        }

        when {
            input == "sh" || input.startsWith("sh ") -> {
                echo(input)
                val cmd = input.removePrefix("sh").trim()
                if (cmd.isEmpty()) out("usage: sh <command>   e.g.  sh getprop ro.product.model", LineKind.MUTED)
                else runShell(cmd)
            }
            input == "lx" || input.startsWith("lx ") -> { echo(input); runLinux(input.removePrefix("lx").trim()) }
            input == "win" || input.startsWith("win ") || input.startsWith("win>") -> {
                echo(input)
                handleWindows(input.removePrefix("win>").removePrefix("win").trim())
            }
            else -> {
                val result = engine.execute(input, buildEnv())
                lines.addAll(result.lines)
                result.actions.forEach { applyAction(it) }
            }
        }
        trimLines()
    }

    /** Run the action attached to a tapped terminal line. */
    fun onAction(action: TermAction) = applyAction(action)

    fun launchApp(pkg: String) { repo.launch(pkg) }
    fun openAppDetails(pkg: String) { repo.openAppDetails(pkg) }
    fun uninstallApp(pkg: String) { repo.uninstall(pkg) }

    fun togglePin(pkg: String) {
        favorites = if (pkg in favorites) favorites - pkg else favorites + pkg
        prefs.favorites = favorites
    }

    fun favoriteApps(): List<AppInfo> =
        favorites.mapNotNull { p -> apps.firstOrNull { it.packageName == p } }

    // ---- sh : native android shell ---------------------------------------
    private fun runShell(cmd: String) {
        out("$ $cmd", LineKind.MUTED)
        viewModelScope.launch {
            val code = shell.run(cmd) { line -> withContext(Dispatchers.Main) { out(line) } }
            out("[exit $code]", if (code == 0) LineKind.MUTED else LineKind.ERR)
        }
    }

    // ---- lx : termux (linux) ---------------------------------------------
    private fun runLinux(cmd: String) {
        if (cmd.isEmpty()) {
            out("usage: lx <command>   routes to Termux (ping curl nmap ssh …)", LineKind.MUTED)
            return
        }
        if (!termux.isInstalled()) {
            out("✗ Termux not installed", LineKind.ERR)
            out("› get Termux (F-Droid)", LineKind.MUTED, TermAction.OpenUrl("https://f-droid.org/packages/com.termux/"))
            out("then once: echo 'allow-external-apps=true' >> ~/.termux/termux.properties", LineKind.MUTED)
            return
        }
        out("→ termux: $cmd", LineKind.MUTED)
        termux.run(cmd, background = true).onFailure {
            out("✗ termux dispatch failed: ${it.message}", LineKind.ERR)
            out("need 'allow-external-apps=true' in ~/.termux/termux.properties", LineKind.MUTED)
        }
    }

    /** Called by MainActivity's receiver when Termux returns a command's result. */
    fun onLinuxResult(stdout: String, stderr: String, exit: Int, err: Int, errmsg: String) {
        if (err != 0 && errmsg.isNotBlank()) { out("✗ termux error($err): $errmsg", LineKind.ERR); return }
        stdout.trimEnd().takeIf { it.isNotEmpty() }?.lineSequence()?.forEach { out(it) }
        stderr.trimEnd().takeIf { it.isNotEmpty() }?.lineSequence()?.forEach { out(it, LineKind.ERR) }
        out("[lx exit $exit]", if (exit == 0) LineKind.MUTED else LineKind.ERR)
    }

    // ---- -pp : PowerShell skin (cosmetic blue + routes input to the win relay) ----
    private fun enterPowerShell() {
        terminalMode = TerminalMode.PowerShell
        prefs.terminalMode = "powershell"
        out("── PowerShell ──", LineKind.HEAD)
        out("blue chassis, flipper soul — commands relay to a Windows host.", LineKind.MUTED)
        if (ssh.connected) out("● attached: ${ssh.target}", LineKind.OK)
        else out("○ no host yet — win connect <user>@<host>[:port]", LineKind.MUTED)
        out("-fz (or exit) returns to fzsh.", LineKind.MUTED)
    }

    private fun enterFlipper() {
        terminalMode = TerminalMode.Fzsh
        prefs.terminalMode = "fzsh"
        out("✓ fzsh — back on the Flipper shell", LineKind.OK)
    }

    /** In PowerShell mode the prompt routes to the win relay; a few locals still work. */
    private fun execPowerShell(input: String) {
        echo(input)
        val lower = input.lowercase()
        when {
            lower == "clear" || lower == "cls" || lower == "c" -> lines.clear()
            lower == "win" || lower.startsWith("win ") || input.startsWith("win>") ->
                handleWindows(input.removePrefix("win>").removePrefix("win").trim())
            lower == "sh" || lower.startsWith("sh ") -> {
                val cmd = input.removePrefix("sh").trim()
                if (cmd.isEmpty()) out("usage: sh <command>", LineKind.MUTED) else runShell(cmd)
            }
            lower == "lx" || lower.startsWith("lx ") -> runLinux(input.removePrefix("lx").trim())
            else -> handleWindows(input) // bare line → PowerShell over the win relay
        }
    }

    // ---- win : ssh to a remote windows host ------------------------------
    private fun handleWindows(rest: String) {
        val parts = rest.split(Regex("\\s+"), limit = 2)
        val sub = parts.getOrElse(0) { "" }.lowercase()
        val arg = parts.getOrElse(1) { "" }.trim()
        when (sub) {
            "", "status" -> {
                if (ssh.connected) out("● connected: ${ssh.target}", LineKind.OK)
                else out("○ not connected — win connect <user>@<host>[:port]", LineKind.MUTED)
            }
            "connect" -> startConnect(arg)
            "disconnect", "close", "logout" -> { ssh.disconnect(); out("✓ ssh disconnected", LineKind.OK) }
            else -> runWindows(rest)
        }
    }

    private fun startConnect(arg: String) {
        val m = Regex("^([^@\\s]+)@([^:\\s]+)(?::(\\d+))?$").find(arg.trim())
        if (m == null) { out("usage: win connect <user>@<host>[:port]", LineKind.ERR); return }
        val port = m.groupValues[3].toIntOrNull() ?: 22
        pendingWinTarget = Triple(m.groupValues[1], m.groupValues[2], port)
        awaitingWinPassword = true
        out("→ ${m.groupValues[1]}@${m.groupValues[2]}:$port — enter password (hidden, memory only)", LineKind.ACCENT)
    }

    private fun connectWindows(user: String, host: String, port: Int, password: String) {
        out("→ connecting $user@$host:$port …", LineKind.MUTED)
        viewModelScope.launch {
            ssh.connect(user, host, port, password)
                .onSuccess {
                    out("✓ ssh connected: ${ssh.target}", LineKind.OK)
                    if (ssh.pinnedNewHost)
                        out("⚠ new host key pinned (TOFU) — verify it's really $host", LineKind.MUTED)
                    out("run remote: win <command>  ·  win disconnect", LineKind.MUTED)
                }
                .onFailure {
                    out("✗ ssh failed: ${it.message}", LineKind.ERR)
                    if (it.message?.contains("HostKey", true) == true ||
                        it.message?.contains("changed", true) == true
                    ) out("host key changed since first use — possible MITM, or the host was rebuilt", LineKind.ERR)
                }
        }
    }

    private fun runWindows(cmd: String) {
        if (cmd.isBlank()) { out("usage: win <command>  (or: win connect <user>@<host>)", LineKind.MUTED); return }
        if (!ssh.connected) { out("✗ not connected — win connect <user>@<host>[:port]", LineKind.ERR); return }
        out("$ $cmd", LineKind.MUTED)
        viewModelScope.launch {
            ssh.exec(cmd)
                .onSuccess { r ->
                    r.stdout.trimEnd().takeIf { it.isNotEmpty() }?.lineSequence()?.forEach { out(it) }
                    r.stderr.trimEnd().takeIf { it.isNotEmpty() }?.lineSequence()?.forEach { out(it, LineKind.ERR) }
                    out("[win exit ${r.exit}]", if (r.exit == 0) LineKind.MUTED else LineKind.ERR)
                }
                .onFailure { out("✗ ${it.message}", LineKind.ERR) }
        }
    }

    // ---- code editor ------------------------------------------------------
    fun refreshCodeFiles() = viewModelScope.launch {
        codeFiles = withContext(Dispatchers.IO) { code.list() }
    }

    fun updateCodeText(text: String) {
        codeText = text
        codeDirty = true
    }

    fun newCodeFile(rawName: String) {
        val name = code.sanitize(rawName)
        viewModelScope.launch {
            if (!code.exists(name)) code.create(name)
            codeText = code.read(name)
            codeName = name
            codeDirty = false
            prefs.lastCodeFile = name
            codeFiles = withContext(Dispatchers.IO) { code.list() }
        }
    }

    fun openCodeFile(name: String) = viewModelScope.launch {
        codeText = code.read(name)
        codeName = name
        codeDirty = false
        prefs.lastCodeFile = name
    }

    /** Terminal → editor: open (creating if absent) a file, then jump to the editor page. */
    private fun openOrCreateInEditor(rawName: String) {
        val name = code.sanitize(rawName)
        viewModelScope.launch {
            if (!code.exists(name)) code.create(name)
            codeText = code.read(name)
            codeName = name
            codeDirty = false
            prefs.lastCodeFile = name
            codeFiles = withContext(Dispatchers.IO) { code.list() }
            requestedPage = 1
        }
    }

    fun saveCode() {
        val name = codeName ?: return
        viewModelScope.launch {
            if (code.write(name, codeText)) {
                codeDirty = false
                codeFiles = withContext(Dispatchers.IO) { code.list() }
                toast("saved $name")
            } else {
                toast("save failed")
            }
        }
    }

    /** Save the open file, jump to the terminal, and run/compile it via the bridges. */
    fun runOpenFile() {
        val name = codeName ?: return
        viewModelScope.launch {
            code.write(name, codeText)
            codeDirty = false
            codeFiles = withContext(Dispatchers.IO) { code.list() }
            val cmd = runCommandFor(name, code.absolutePath(name))
            requestedPage = 0
            when {
                cmd == null -> {
                    lines.add(TermLine("fz> # run ${name}", LineKind.INPUT))
                    lines.add(TermLine("no built-in runner for .${name.substringAfterLast('.', "")} — compile manually (sh/lx/win)", LineKind.MUTED))
                    trimLines()
                }
                // Termux-routed builds need the file on shared storage to reach it.
                cmd.startsWith("lx ") && !code.hasSharedAccess() -> {
                    lines.add(TermLine("fz> # run ${name}", LineKind.INPUT))
                    out("✗ Termux can't reach this file — the workspace is app-private on Android ${Build.VERSION.RELEASE}.", LineKind.ERR)
                    out("› grant storage access so lx can compile", LineKind.ACCENT, TermAction.RequestStorage)
                }
                else -> exec(cmd)
            }
        }
    }

    private fun runCommandFor(name: String, path: String): String? {
        val p = "\"$path\""
        val out = "\"$path.out\""
        return when (name.substringAfterLast('.', "").lowercase()) {
            "c" -> "lx gcc $p -o $out && $out"
            "cpp", "cc", "cxx" -> "lx g++ $p -o $out && $out"
            "rs" -> "lx rustc $p -o $out && $out"
            "go" -> "lx go run $p"
            "py", "pyw" -> "lx python $p"
            "js" -> "lx node $p"
            "ts" -> "lx ts-node $p"
            "rb" -> "lx ruby $p"
            "lua" -> "lx lua $p"
            "php" -> "lx php $p"
            "sh", "bash" -> "sh sh $p"        // native shell — no Termux needed
            else -> null
        }
    }

    // ---- shared-storage access (lets Termux reach the code workspace) ----
    private fun requestStorageAccess() {
        if (code.hasSharedAccess()) { out("✓ storage access already granted", LineKind.OK); return }
        out("→ opening storage-access settings — allow, then run the file again", LineKind.MUTED)
        pendingStorageRequest = true
    }

    /** MainActivity reports the access dialog closed — re-check and migrate work. */
    fun onStorageGrantResult() {
        pendingStorageRequest = false
        viewModelScope.launch {
            if (code.hasSharedAccess()) {
                val moved = code.migrateToShared()
                codeFiles = withContext(Dispatchers.IO) { code.list() }
                prefs.lastCodeFile?.let { openCodeFile(it) }
                out("✓ storage access granted — workspace now at ${code.dirPath}", LineKind.OK)
                if (moved > 0) out("✓ moved $moved file(s) into the shared workspace", LineKind.MUTED)
                out("Termux can compile here now — ▶ run the file again.", LineKind.MUTED)
            } else {
                out("✗ access not granted — Termux still can't reach the workspace", LineKind.ERR)
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()

    fun deleteCodeFile(name: String) = viewModelScope.launch {
        code.delete(name)
        if (codeName == name) { codeName = null; codeText = ""; codeDirty = false; prefs.lastCodeFile = null }
        codeFiles = withContext(Dispatchers.IO) { code.list() }
    }

    override fun onCleared() {
        super.onCleared()
        ssh.disconnect()
    }

    // ---- internals --------------------------------------------------------
    /** Prompt shown before echoed input — flips to a PS prompt in PowerShell mode. */
    private fun promptPrefix(): String =
        if (terminalMode == TerminalMode.PowerShell) "PS C:\\>" else "fz>"

    private fun echo(input: String) { lines.add(TermLine("${promptPrefix()} $input", LineKind.INPUT)) }

    private fun out(text: String, kind: LineKind = LineKind.OUT, action: TermAction? = null) {
        lines.add(TermLine(text, kind, action)); trimLines()
    }
    private fun applyAction(a: TermAction) {
        when (a) {
            is TermAction.Launch -> if (!repo.launch(a.pkg)) lines.add(TermLine("✗ cannot launch ${a.label}", LineKind.ERR))
            is TermAction.AppDetails -> repo.openAppDetails(a.pkg)
            is TermAction.Uninstall -> repo.uninstall(a.pkg)
            is TermAction.OpenSettings -> repo.openSettings(a.settingsAction)
            is TermAction.OpenUrl -> repo.openUrl(a.url)
            is TermAction.Pin -> { if (a.pkg !in favorites) { favorites = favorites + a.pkg; prefs.favorites = favorites } }
            is TermAction.Unpin -> { favorites = favorites - a.pkg; prefs.favorites = favorites }
            is TermAction.SetAccent -> { accent = a.name; prefs.accent = a.name }
            is TermAction.AddAlias -> { aliases = aliases + (a.name to a.value); prefs.aliases = aliases }
            is TermAction.RemoveAlias -> { aliases = aliases - a.name; prefs.aliases = aliases }
            is TermAction.AddNote -> { notes = notes + a.text; prefs.notes = notes }
            is TermAction.RemoveNote -> {
                notes = if (a.index < 0) emptyList() else notes.filterIndexed { i, _ -> i != a.index }
                prefs.notes = notes
            }
            is TermAction.AddWidget -> addWidget(a.query)
            is TermAction.RemoveWidget -> removeWidget(a.index)
            is TermAction.OpenInEditor -> openOrCreateInEditor(a.name)
            TermAction.RequestStorage -> requestStorageAccess()
            TermAction.ClearScreen -> lines.clear()
            TermAction.GoToApps -> { query = ""; showApps = true }
            TermAction.GoToCode -> requestedPage = 1
            TermAction.Reboot -> { lines.clear(); seedBanner() }
        }
    }

    private fun buildEnv(): TermEnv {
        val now = Date()
        val device = DeviceSnapshot(
            time = timeFmt.format(now),
            date = dateFmt.format(now),
            batteryPct = batteryPct,
            charging = charging,
            model = (Build.MODEL ?: "device"),
            androidRelease = (Build.VERSION.RELEASE ?: "?"),
            sdkInt = Build.VERSION.SDK_INT,
            uptime = formatUptime(SystemClock.elapsedRealtime()),
        )
        val pm = getApplication<Application>().packageManager
        val widgetLabels = widgetIds.map { id ->
            widgetHost.info(id)?.loadLabel(pm) ?: "widget#$id"
        }
        return TermEnv(
            apps, favorites, aliases, notes, history, accent, device,
            widgetProviders = widgetHost.providers(),
            widgets = widgetLabels,
            codeFiles = codeFiles,
        )
    }

    // ---- widget hosting ---------------------------------------------------
    private fun addWidget(query: String) {
        val provs = widgetHost.providers()
        val target = provs.firstOrNull { it.component == query }
            ?: provs.firstOrNull { it.label.contains(query, true) || it.packageName.contains(query, true) }
        if (target == null) {
            lines.add(TermLine("✗ widget provider gone: $query", LineKind.ERR)); return
        }
        val id = widgetHost.allocate()
        if (widgetHost.bind(id, target.component)) {
            afterBind(id, target.hasConfig)
        } else {
            pendingBind = PendingWidget(id, target.component, target.hasConfig)
        }
    }

    private fun afterBind(id: Int, needsConfig: Boolean) {
        if (needsConfig) pendingConfig = id else commitWidget(id)
    }

    /** Result of the system ACTION_APPWIDGET_BIND dialog. */
    fun onBindResult(granted: Boolean) {
        val p = pendingBind ?: return
        pendingBind = null
        if (granted) afterBind(p.id, p.needsConfig)
        else {
            widgetHost.delete(p.id)
            lines.add(TermLine("✗ widget bind denied", LineKind.ERR))
        }
    }

    /** Result of the widget's own configure activity. */
    fun onConfigResult(ok: Boolean) {
        val id = pendingConfig ?: return
        pendingConfig = null
        if (ok) commitWidget(id)
        else {
            widgetHost.delete(id)
            lines.add(TermLine("✗ widget setup cancelled", LineKind.ERR))
        }
    }

    private fun commitWidget(id: Int) {
        widgetIds = widgetIds + id
        prefs.widgetIds = widgetIds
        lines.add(TermLine("✓ widget online — live at the top of the terminal", LineKind.OK))
    }

    private fun removeWidget(index: Int) {
        val id = widgetIds.getOrNull(index) ?: run {
            lines.add(TermLine("✗ no widget at [$index] — see 'wgt'", LineKind.ERR)); return
        }
        widgetHost.delete(id)
        widgetIds = widgetIds - id
        prefs.widgetIds = widgetIds
        lines.add(TermLine("✓ widget [$index] removed", LineKind.OK))
    }

    private fun formatUptime(ms: Long): String {
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun seedBanner() {
        splash.forEach { lines.add(TermLine(it, LineKind.ASCII)) }
        lines.add(TermLine("  >_ fzsh 1.0  //  a11 project", LineKind.ACCENT))
        lines.add(TermLine("  home online · type 'help', 'flipper' or 'ls'.", LineKind.MUTED))
    }

    private fun trimLines() {
        val max = 500
        if (lines.size > max) repeat(lines.size - max) { lines.removeAt(0) }
    }
}
