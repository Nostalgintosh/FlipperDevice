package com.a11.flipperlauncher.terminal

import android.provider.Settings
import com.a11.flipperlauncher.data.AppInfo

/**
 * `fzsh` — the Flipper Zero shell. Pure logic: takes a raw command + a snapshot
 * of the world ([TermEnv]) and returns lines to print plus side effects to run.
 */
class TerminalEngine {

    fun execute(raw: String, env: TermEnv): EngineResult {
        val input = raw.trim()
        if (input.isEmpty()) return EngineResult(emptyList())

        val out = ArrayList<TermLine>()
        val actions = ArrayList<TermAction>()
        out += TermLine("fz> $input", LineKind.INPUT)

        // ./app shorthand for "open app"
        if (input.startsWith("./")) {
            openApp(input.removePrefix("./").trim(), env, out, actions)
            return EngineResult(out, actions)
        }

        val expanded = expandAlias(input, env.aliases)
        val tokens = expanded.split(WS).filter { it.isNotEmpty() }
        val cmd = tokens.firstOrNull()?.lowercase() ?: return EngineResult(out, actions)
        val rest = expanded.drop(tokens.first().length).trim()

        when (cmd) {
            "help", "h", "?", "commands" -> help(out)
            "common", "cmds" -> common(out)
            "cmd" -> if (rest.isBlank()) common(out) else shellBridge(cmd, rest, env, out, actions)
            "man", "usage" -> man(rest, out)

            "ls", "apps", "list", "ll", "dir" -> list(rest, env, out, actions)
            "find", "search", "grep", "f" -> findApps(rest, env, out)
            "open", "run", "start", "launch", "o" -> openApp(rest, env, out, actions)
            "info", "which" -> appInfo(rest, env, out)
            "uninstall", "rm", "remove", "del" -> uninstall(rest, env, out, actions)

            "create", "new", "mk" -> create(rest, env, out, actions)
            "pin" -> pin(rest, env, out, actions)
            "unpin" -> unpin(rest, env, out, actions)
            "fav", "favs", "pinned", "dock" -> listFavorites(env, out)
            "alias" -> alias(rest, env, out, actions)
            "unalias" -> { actions += TermAction.RemoveAlias(rest.trim().lowercase()); out += ok("unalias ${rest.trim()}") }
            "note", "notes" -> note(rest, env, out, actions)

            "theme", "accent" -> theme(rest, out, actions)
            "clear", "cls", "c" -> { actions += TermAction.ClearScreen }
            "reboot", "reset" -> { actions += TermAction.Reboot }

            "battery", "bat", "power" -> battery(env, out)
            "sysinfo", "neofetch", "fastfetch", "uname" -> sysinfo(env, out)
            "date", "time", "clock" -> { out += line("${env.device.date}  ${env.device.time}", LineKind.ACCENT) }
            "whoami", "id" -> whoami(env, out)
            "history", "hist" -> history(env, out)
            "echo" -> out += line(rest)
            "pwd" -> pwd(out)
            "cd" -> cd(rest, env, out, actions)
            "cat", "less", "more", "head", "tail" -> readPseudoFile(cmd, rest, env, out, actions)
            "ps", "top", "kill", "jobs" -> androidProcessHelp(cmd, out)

            "settings", "config" -> if (rest.isBlank()) settings(out, actions) else shellBridge(cmd, rest, env, out, actions)
            "wifi", "wlan" -> { actions += TermAction.OpenSettings(Settings.ACTION_WIFI_SETTINGS); out += ok("opening wifi settings") }
            "bt", "bluetooth" -> { actions += TermAction.OpenSettings(Settings.ACTION_BLUETOOTH_SETTINGS); out += ok("opening bluetooth settings") }
            "loc", "gps", "location" -> { actions += TermAction.OpenSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS); out += ok("opening location settings") }
            "dev", "developer" -> { actions += TermAction.OpenSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS); out += ok("opening developer options") }
            "degoogle", "privacy" -> degoogle(out)

            "wgt", "widget", "widgets" -> wgt(rest, env, out, actions)

            "edit", "code", "nano", "vim", "vi", "ed" -> editFile(rest, env, out, actions)

            "flipper", "fz", "zero" -> flipper(env, out)
            "tools", "links", "kit" -> tools(out)
            "termux", "shell", "sh", "bash" -> shellBridge(cmd, rest, env, out, actions, launchImmediately = true)
            "sudo", "su", "pkg", "apt", "apt-get", "python", "python3", "pip", "pip3",
            "node", "npm", "git", "ssh", "scp", "curl", "wget", "nmap", "nc", "netcat",
            "ping", "ip", "ifconfig", "adb", "fastboot", "lsusb", "chmod", "chown",
            "mkdir", "touch", "cp", "mv", "tar", "zip", "unzip", "make", "pm", "am",
            "logcat", "dumpsys", "getprop", "setprop" ->
                shellBridge(cmd, rest, env, out, actions)
            "-apps", "drawer", "az", "a-z" -> { actions += TermAction.GoToApps; out += ok("opening apps") }

            "about", "version", "ver" -> about(out)
            "exit", "quit", "logout" -> out += line("nice try. you are home.", LineKind.MUTED)

            else -> unknown(cmd, rest, env, out, actions)
        }
        return EngineResult(out, actions)
    }

    // ---- commands ---------------------------------------------------------

    private fun help(out: MutableList<TermLine>) {
        out += head("fzsh // command index")
        for ((name, desc) in COMMANDS) {
            out += TermLine("  %-15s %s".format(name, desc), LineKind.OUT)
        }
        out += line("type 'man <cmd>' for detail · tap any result to act", LineKind.MUTED)
    }

    private fun common(out: MutableList<TermLine>) {
        out += head("common commands")
        out += line("  apps    : -apps (A–Z drawer), ls, find <q>, open <q>, pin <q>")
        out += line("  launch  : camera, phone, messages, chrome  (unique app names)")
        out += line("  shell   : pwd, cd apps, ls notes, cat history, termux")
        out += line("  system  : battery, sysinfo, settings, wifi, bt, loc, dev")
        out += line("  flipper : flipper, tools, degoogle, wgt -termux")
        out += line("  code    : edit <file>, code  (▶ run in editor → output here)")
        out += line("  notes   : note <text>, note ls, note rm <i>, note clear")
        out += line("real Unix commands open Termux; fzsh stays a launcher shell.", LineKind.MUTED)
    }

    private fun man(arg: String, out: MutableList<TermLine>) {
        val key = arg.trim().lowercase()
        if (key.isEmpty()) { out += err("usage: man <command>"); return }
        val page = MAN[key]
        if (page == null) {
            val brief = COMMANDS.entries.firstOrNull { it.key.startsWith(key) }?.value
            if (brief != null) out += line("$key — $brief") else out += err("no manual entry for '$key'")
            return
        }
        out += head("man $key")
        page.split("\n").forEach { out += line("  $it") }
    }

    private fun listApps(env: TermEnv, out: MutableList<TermLine>) {
        if (env.apps.isEmpty()) { out += err("no apps visible"); return }
        out += head("${env.apps.size} apps")
        env.apps.forEach { out += appLine(it) }
        out += line("› open full A–Z drawer", LineKind.MUTED, TermAction.GoToApps)
    }

    private fun list(rest: String, env: TermEnv, out: MutableList<TermLine>, actions: MutableList<TermAction>) {
        when (val target = rest.trim().lowercase().removePrefix("./")) {
            "", ".", "/", "~", "/apps", "apps", "-a", "-l", "-la", "-al" -> listApps(env, out)
            "/notes", "notes", "notes.txt" -> note("ls", env, out, actions)
            "/widgets", "widgets", "wgt" -> wgt("ls", env, out, actions)
            "/settings", "settings" -> settings(out, actions)
            "/tools", "tools", "kit" -> tools(out)
            "/help", "help", "commands" -> help(out)
            else -> {
                out += err("no such launcher path: $target")
                out += line("try: ls apps · ls notes · ls widgets · ls tools", LineKind.MUTED)
            }
        }
    }

    private fun findApps(q: String, env: TermEnv, out: MutableList<TermLine>) {
        if (q.isBlank()) { out += err("usage: find <query>"); return }
        val matches = search(q, env.apps)
        if (matches.isEmpty()) { out += err("no match for '$q'"); return }
        out += head("${matches.size} match" + if (matches.size == 1) "" else "es")
        matches.take(40).forEach { out += appLine(it) }
    }

    private fun openApp(q: String, env: TermEnv, out: MutableList<TermLine>, actions: MutableList<TermAction>) {
        if (q.isBlank()) { out += err("usage: open <query>"); return }
        val exact = env.apps.firstOrNull {
            it.label.equals(q, true) || it.packageName.equals(q, true)
        }
        val target = exact ?: run {
            val matches = search(q, env.apps)
            when {
                matches.isEmpty() -> { out += err("not found: '$q'"); suggestApp(q, env, out); return }
                matches.size == 1 -> matches.first()
                else -> {
                    out += line("multiple matches — tap one:", LineKind.MUTED)
                    matches.take(12).forEach { out += appLine(it) }
                    return
                }
            }
        }
        actions += TermAction.Launch(target.packageName, target.label)
        out += ok("launching ${target.label}")
    }

    private fun appInfo(q: String, env: TermEnv, out: MutableList<TermLine>) {
        if (q.isBlank()) { out += err("usage: info <query>"); return }
        val app = resolve(q, env.apps) ?: run { out += err("not found: '$q'"); return }
        out += head(app.label)
        out += line("  package : ${app.packageName}")
        out += line("  version : ${app.versionName}")
        out += line("  pinned  : ${if (app.packageName in env.favorites) "yes" else "no"}")
        out += line("› launch", LineKind.MUTED, TermAction.Launch(app.packageName, app.label))
        out += line("› app details", LineKind.MUTED, TermAction.AppDetails(app.packageName))
    }

    private fun uninstall(q: String, env: TermEnv, out: MutableList<TermLine>, actions: MutableList<TermAction>) {
        if (q.isBlank()) { out += err("usage: uninstall <query>"); return }
        val app = resolve(q, env.apps) ?: run { out += err("not found: '$q'"); return }
        actions += TermAction.Uninstall(app.packageName)
        out += line("requesting removal of ${app.label} …", LineKind.ACCENT)
    }

    private fun create(rest: String, env: TermEnv, out: MutableList<TermLine>, actions: MutableList<TermAction>) {
        val sub = rest.split(WS, limit = 2)
        val kind = sub.getOrElse(0) { "" }.lowercase()
        val body = sub.getOrElse(1) { "" }.trim()
        when (kind) {
            "alias" -> setAlias(body, out, actions)
            "note" -> {
                if (body.isEmpty()) { out += err("usage: create note <text>"); return }
                actions += TermAction.AddNote(body); out += ok("note saved")
            }
            "pin", "shortcut", "fav" -> pin(body, env, out, actions)
            "" -> {
                out += head("create")
                out += line("  create alias <name>=<command>")
                out += line("  create note  <text>")
                out += line("  create pin   <app>")
            }
            else -> out += err("create: unknown type '$kind' (alias|note|pin)")
        }
    }

    private fun pin(q: String, env: TermEnv, out: MutableList<TermLine>, actions: MutableList<TermAction>) {
        if (q.isBlank()) { out += err("usage: pin <query>"); return }
        val app = resolve(q, env.apps) ?: run { out += err("not found: '$q'"); return }
        actions += TermAction.Pin(app.packageName)
        out += ok("pinned ${app.label} to dock")
    }

    private fun unpin(q: String, env: TermEnv, out: MutableList<TermLine>, actions: MutableList<TermAction>) {
        if (q.isBlank()) { out += err("usage: unpin <query>"); return }
        val app = resolve(q, env.apps) ?: run { out += err("not found: '$q'"); return }
        actions += TermAction.Unpin(app.packageName)
        out += ok("unpinned ${app.label}")
    }

    private fun listFavorites(env: TermEnv, out: MutableList<TermLine>) {
        val pinned = env.favorites.mapNotNull { p -> env.apps.firstOrNull { it.packageName == p } }
        if (pinned.isEmpty()) { out += line("dock is empty — try: pin <app>", LineKind.MUTED); return }
        out += head("dock // ${pinned.size}")
        pinned.forEach { out += appLine(it) }
    }

    private fun alias(rest: String, env: TermEnv, out: MutableList<TermLine>, actions: MutableList<TermAction>) {
        val trimmed = rest.trim()
        when {
            trimmed.isEmpty() -> {
                if (env.aliases.isEmpty()) { out += line("no aliases", LineKind.MUTED); return }
                out += head("aliases")
                env.aliases.forEach { (k, v) -> out += line("  $k = $v") }
            }
            trimmed.startsWith("rm ") -> {
                val name = trimmed.removePrefix("rm ").trim().lowercase()
                actions += TermAction.RemoveAlias(name); out += ok("removed alias '$name'")
            }
            else -> setAlias(trimmed, out, actions)
        }
    }

    private fun setAlias(body: String, out: MutableList<TermLine>, actions: MutableList<TermAction>) {
        if (!body.contains('=')) { out += err("usage: alias <name>=<command>"); return }
        val name = body.substringBefore('=').trim().lowercase()
        val value = body.substringAfter('=').trim()
        if (name.isEmpty() || value.isEmpty()) { out += err("alias needs a name and a command"); return }
        actions += TermAction.AddAlias(name, value)
        out += ok("alias $name → $value")
    }

    private fun note(rest: String, env: TermEnv, out: MutableList<TermLine>, actions: MutableList<TermAction>) {
        val trimmed = rest.trim()
        when {
            trimmed.isEmpty() || trimmed == "ls" -> {
                if (env.notes.isEmpty()) { out += line("no notes — try: note <text>", LineKind.MUTED); return }
                out += head("notes // ${env.notes.size}")
                env.notes.forEachIndexed { i, n -> out += line("  [$i] $n") }
            }
            trimmed == "clear" -> { actions += TermAction.RemoveNote(-1); out += ok("notes cleared") }
            trimmed.startsWith("rm ") -> {
                val idx = trimmed.removePrefix("rm ").trim().toIntOrNull()
                if (idx == null || idx < 0) { out += err("usage: note rm <index>  ·  note clear wipes all"); return }
                if (idx >= env.notes.size) { out += err("no note at [$idx] — see 'note ls'"); return }
                actions += TermAction.RemoveNote(idx); out += ok("removed note [$idx]")
            }
            else -> { actions += TermAction.AddNote(trimmed); out += ok("note saved") }
        }
    }

    private fun wgt(rest: String, env: TermEnv, out: MutableList<TermLine>, actions: MutableList<TermAction>) {
        val arg = rest.trim()
        when {
            arg.isEmpty() || arg.equals("ls", true) -> {
                if (env.widgets.isEmpty()) {
                    out += line("no widgets embedded yet", LineKind.MUTED)
                } else {
                    out += head("widgets // ${env.widgets.size}")
                    env.widgets.forEachIndexed { i, w -> out += line("  [$i] $w") }
                }
                out += line("usage: wgt -<app> · wgt - (all providers) · wgt rm <i>", LineKind.MUTED)
            }
            arg == "-" || arg.equals("providers", true) -> {
                val provs = env.widgetProviders
                if (provs.isEmpty()) { out += err("no widget providers on this device"); return }
                out += head("${provs.size} widget providers — tap to embed")
                provs.take(60).forEach { p ->
                    out += line("  ${p.label} · ${p.packageName}", LineKind.OUT, TermAction.AddWidget(p.component))
                }
                if (provs.size > 60) out += line("  …${provs.size - 60} more — narrow with wgt -<q>", LineKind.MUTED)
            }
            arg == "rm" || arg.startsWith("rm ") -> {
                val idx = arg.removePrefix("rm").trim().toIntOrNull()
                if (idx == null) { out += err("usage: wgt rm <index>"); return }
                actions += TermAction.RemoveWidget(idx)
            }
            else -> {
                val q = arg.removePrefix("-").removePrefix("add ").trim()
                if (q.isEmpty()) { out += err("usage: wgt -<app>"); return }
                val matches = env.widgetProviders.filter {
                    it.label.contains(q, true) || it.packageName.contains(q, true)
                }
                when {
                    matches.isEmpty() -> {
                        out += err("no widget provider matches '$q'")
                        if (q.contains("termux", true)) {
                            out += line("Termux:Widget runs your ~/.shortcuts scripts in one tap.", LineKind.MUTED)
                            out += line("› get Termux:Widget (F-Droid)", LineKind.MUTED, TermAction.OpenUrl("https://f-droid.org/packages/com.termux.widget/"))
                            out += line("› get Termux (F-Droid)", LineKind.MUTED, TermAction.OpenUrl("https://f-droid.org/packages/com.termux/"))
                            out += line("then run 'wgt -termux' again — it will embed here.", LineKind.MUTED)
                        } else {
                            out += line("see everything: wgt -", LineKind.MUTED)
                        }
                    }
                    matches.size == 1 -> {
                        actions += TermAction.AddWidget(matches[0].component)
                        out += line("binding ${matches[0].label} …", LineKind.ACCENT)
                    }
                    else -> {
                        out += line("multiple providers — tap one:", LineKind.MUTED)
                        matches.take(12).forEach { p ->
                            out += line("  ${p.label} · ${p.packageName}", LineKind.OUT, TermAction.AddWidget(p.component))
                        }
                    }
                }
            }
        }
    }

    private fun editFile(rest: String, env: TermEnv, out: MutableList<TermLine>, actions: MutableList<TermAction>) {
        val name = rest.trim()
        if (name.isEmpty()) {
            // bare `edit` / `code`: list the workspace (tap to open) and jump to the editor.
            if (env.codeFiles.isEmpty()) {
                out += line("workspace empty — create one:  edit <name.ext>", LineKind.MUTED)
            } else {
                out += head("code // ${env.codeFiles.size} files")
                env.codeFiles.forEach { f -> out += line("  $f", LineKind.OUT, TermAction.OpenInEditor(f)) }
            }
            actions += TermAction.GoToCode
            out += ok("opening code editor")
            return
        }
        actions += TermAction.OpenInEditor(name)
        out += ok("editing $name")
    }

    private fun theme(arg: String, out: MutableList<TermLine>, actions: MutableList<TermAction>) {
        val name = arg.trim().lowercase()
        if (name.isEmpty() || name !in ACCENTS) {
            out += line("accent options: ${ACCENTS.joinToString(" ")}", LineKind.MUTED)
            return
        }
        actions += TermAction.SetAccent(name)
        out += ok("accent → $name")
    }

    private fun settings(out: MutableList<TermLine>, actions: MutableList<TermAction>) {
        actions += TermAction.OpenSettings(Settings.ACTION_SETTINGS)
        out += ok("opening settings")
    }

    private fun pwd(out: MutableList<TermLine>) {
        out += line("fzsh:/home", LineKind.ACCENT)
        out += line("virtual paths: /apps /notes /widgets /settings /tools", LineKind.MUTED)
    }

    private fun cd(rest: String, env: TermEnv, out: MutableList<TermLine>, actions: MutableList<TermAction>) {
        when (val dest = rest.trim().lowercase().ifEmpty { "~" }) {
            "~", ".", "..", "/", "/home", "home", "term", "terminal" -> out += ok("home -> terminal")
            "apps", "/apps", "drawer", "/drawer" -> {
                actions += TermAction.GoToApps
                out += ok("cd /apps")
            }
            "settings", "/settings" -> {
                actions += TermAction.OpenSettings(Settings.ACTION_SETTINGS)
                out += ok("cd /settings")
            }
            "tools", "/tools", "kit", "/kit" -> tools(out)
            "notes", "/notes" -> note("ls", env, out, actions)
            "widgets", "/widgets", "wgt", "/wgt" -> wgt("ls", env, out, actions)
            "termux", "/termux", "shell", "/shell" -> shellBridge("termux", "", env, out, actions, launchImmediately = true)
            else -> {
                out += err("no such launcher path: $dest")
                out += line("try: cd apps · cd notes · cd settings · cd termux", LineKind.MUTED)
            }
        }
    }

    private fun readPseudoFile(
        cmd: String,
        rest: String,
        env: TermEnv,
        out: MutableList<TermLine>,
        actions: MutableList<TermAction>,
    ) {
        when (rest.trim().lowercase()) {
            "" -> out += err("usage: $cmd <notes|history|apps|help|widgets>")
            "notes", "/notes", "~/notes", "notes.txt" -> note("ls", env, out, actions)
            "history", "/history", "~/.history" -> history(env, out)
            "apps", "/apps" -> listApps(env, out)
            "widgets", "/widgets", "wgt" -> wgt("ls", env, out, actions)
            "help", "/help", "commands" -> help(out)
            else -> shellBridge(cmd, rest, env, out, actions)
        }
    }

    private fun androidProcessHelp(cmd: String, out: MutableList<TermLine>) {
        out += head("$cmd // Android")
        out += line("launcher apps cannot inspect or kill system processes directly.")
        out += line("use Termux for userland tools, or Android settings for app control.", LineKind.MUTED)
        out += line("› app settings", LineKind.MUTED, TermAction.OpenSettings(Settings.ACTION_APPLICATION_SETTINGS))
        out += line("› developer options", LineKind.MUTED, TermAction.OpenSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    }

    private fun shellBridge(
        cmd: String,
        rest: String,
        env: TermEnv,
        out: MutableList<TermLine>,
        actions: MutableList<TermAction>,
        launchImmediately: Boolean = false,
    ) {
        val typed = listOf(cmd, rest).joinToString(" ").trim()
        val termux = env.apps.firstOrNull {
            it.packageName == TERMUX_PACKAGE || it.label.equals("Termux", true)
        }
        out += head("$cmd // Termux")
        out += line("fzsh is the launcher command layer; POSIX commands run in Termux.", LineKind.MUTED)
        if (termux != null) {
            val action = TermAction.Launch(termux.packageName, termux.label)
            if (launchImmediately) {
                actions += action
                out += ok("opening ${termux.label}")
            } else {
                out += line("› open ${termux.label} to run: $typed", LineKind.OK, action)
            }
            out += line("tip: install Termux:Widget, then use 'wgt -termux' for one-tap scripts.", LineKind.MUTED)
        } else {
            out += line("› install Termux (F-Droid)", LineKind.MUTED, TermAction.OpenUrl("https://f-droid.org/packages/com.termux/"))
            out += line("› install Termux:Widget", LineKind.MUTED, TermAction.OpenUrl("https://f-droid.org/packages/com.termux.widget/"))
            out += line("after installing, '$cmd' will offer a Termux handoff here.", LineKind.MUTED)
        }
    }

    private fun degoogle(out: MutableList<TermLine>) {
        out += head("de-google // safe mode")
        out += line("  keep core : Play services, Play Store, GSF, WebView, NetworkStack")
        out += line("  remove app: YouTube, Photos, Drive/Docs, Gmail, Meet, Google app")
        out += line("  optional  : Chrome, Maps, Calendar, Messages, Contacts, backup/sync")
        out += line("  method    : disable-user first; uninstall only after testing")
        out += line("› installed apps", LineKind.MUTED, TermAction.OpenSettings(Settings.ACTION_APPLICATION_SETTINGS))
        out += line("› default apps", LineKind.MUTED, TermAction.OpenSettings(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        out += line("› account sync", LineKind.MUTED, TermAction.OpenSettings(Settings.ACTION_SYNC_SETTINGS))
        out += line("› F-Droid", LineKind.MUTED, TermAction.OpenUrl("https://f-droid.org/"))
    }

    private fun battery(env: TermEnv, out: MutableList<TermLine>) {
        val d = env.device
        out += head("power")
        out += line("  ${batteryBar(d.batteryPct, d.charging)}", LineKind.ACCENT)
        out += line("  state : ${if (d.charging) "charging" else "discharging"}")
    }

    private fun sysinfo(env: TermEnv, out: MutableList<TermLine>) {
        val d = env.device
        DOLPHIN.forEach { out += line(it, LineKind.ASCII) }
        out += head("system")
        out += line("  os     : Android ${d.androidRelease} (sdk ${d.sdkInt})")
        out += line("  device : ${d.model}")
        out += line("  apps   : ${env.apps.size} installed")
        out += line("  power  : ${batteryBar(d.batteryPct, d.charging)}")
        out += line("  uptime : ${d.uptime}")
        out += line("  accent : ${env.accent}")
        out += line("  shell  : fzsh 1.0 // a11")
    }

    private fun whoami(env: TermEnv, out: MutableList<TermLine>) {
        out += line("operator@${env.device.model.replace(' ', '-').lowercase()}", LineKind.ACCENT)
        out += line("uid=0(root) groups=ethical — touch only what you own.", LineKind.MUTED)
    }

    private fun history(env: TermEnv, out: MutableList<TermLine>) {
        if (env.history.isEmpty()) { out += line("no history", LineKind.MUTED); return }
        out += head("history")
        env.history.takeLast(20).forEachIndexed { i, c -> out += line("  ${"%3d".format(i)}  $c") }
    }

    private fun flipper(env: TermEnv, out: MutableList<TermLine>) {
        DOLPHIN.forEach { out += line(it, LineKind.ASCII) }
        out += head("Flipper Zero")
        out += line("  multi-tool for pentesters & geeks — sub-GHz, NFC, RFID,")
        out += line("  Infrared, GPIO, iButton, BadUSB. Pair it from your phone.")
        val companion = env.apps.firstOrNull { it.packageName.contains("flipperdevices") || it.packageName.contains("flipperzero") }
        if (companion != null) {
            out += line("› open ${companion.label}", LineKind.OK, TermAction.Launch(companion.packageName, companion.label))
        } else {
            out += line("› install Flipper Mobile (Play)", LineKind.MUTED, TermAction.OpenUrl("https://play.google.com/store/apps/details?id=com.flipperdevices.app"))
        }
        out += line("› flipperzero.one", LineKind.MUTED, TermAction.OpenUrl("https://flipperzero.one"))
        out += line("› docs.flipper.net", LineKind.MUTED, TermAction.OpenUrl("https://docs.flipper.net"))
        out += line("› lab.flipper.net (apps)", LineKind.MUTED, TermAction.OpenUrl("https://lab.flipper.net"))
    }

    private fun tools(out: MutableList<TermLine>) {
        out += head("kit // quick links")
        LINKS.forEach { (label, url) -> out += line("› $label", LineKind.MUTED, TermAction.OpenUrl(url)) }
    }

    private fun about(out: MutableList<TermLine>) {
        out += head("about")
        out += line("  Flipper Launcher — fzsh 1.0")
        out += line("  a minimalist, terminal-first home screen")
        out += line("  built for coders, hackers & tinkerers")
        out += line("  // The A11 Project")
    }

    private fun unknown(
        cmd: String,
        rest: String,
        env: TermEnv,
        out: MutableList<TermLine>,
        actions: MutableList<TermAction>,
    ) {
        val typed = listOf(cmd, rest).joinToString(" ").trim()
        val exact = env.apps.firstOrNull {
            it.label.equals(typed, true) || it.packageName.equals(typed, true)
        }
        val matches = if (exact != null) listOf(exact) else search(typed, env.apps)
        when {
            matches.size == 1 -> {
                val app = matches.first()
                actions += TermAction.Launch(app.packageName, app.label)
                out += ok("launching ${app.label}")
                return
            }
            matches.size > 1 -> {
                out += line("multiple app matches — tap one:", LineKind.MUTED)
                matches.take(12).forEach { out += appLine(it) }
                return
            }
        }

        out += err("command not found: $cmd")
        val near = KNOWN.minByOrNull { levenshtein(cmd, it) }
        if (near != null && levenshtein(cmd, near) <= 2) out += line("did you mean '$near'?  ·  type 'help'", LineKind.MUTED)
        else out += line("type 'help' for commands", LineKind.MUTED)
    }

    private fun suggestApp(q: String, env: TermEnv, out: MutableList<TermLine>) {
        val near = env.apps.minByOrNull { levenshtein(q.lowercase(), it.label.lowercase()) } ?: return
        if (levenshtein(q.lowercase(), near.label.lowercase()) <= 3) {
            out += line("closest: ${near.label}", LineKind.MUTED, TermAction.Launch(near.packageName, near.label))
        }
    }

    // ---- helpers ----------------------------------------------------------

    private fun appLine(a: AppInfo) =
        TermLine("  ${a.label}", LineKind.OUT, TermAction.Launch(a.packageName, a.label))

    private fun line(text: String, kind: LineKind = LineKind.OUT, action: TermAction? = null) =
        TermLine(text, kind, action)

    private fun ok(text: String) = TermLine("✓ $text", LineKind.OK)
    private fun err(text: String) = TermLine("✗ $text", LineKind.ERR)
    private fun head(text: String) = TermLine("── $text ──", LineKind.HEAD)

    private fun expandAlias(input: String, aliases: Map<String, String>): String {
        val parts = input.split(WS, limit = 2)
        val head = parts[0].lowercase()
        val tail = parts.getOrElse(1) { "" }
        val sub = aliases[head] ?: return input
        return "$sub $tail".trim()
    }

    private fun resolve(q: String, apps: List<AppInfo>): AppInfo? {
        val query = q.trim()
        apps.firstOrNull { it.label.equals(query, true) || it.packageName.equals(query, true) }?.let { return it }
        return search(query, apps).firstOrNull()
    }

    private fun search(q: String, apps: List<AppInfo>): List<AppInfo> {
        val query = q.trim().lowercase()
        if (query.isEmpty()) return emptyList()
        return apps.filter {
            it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }.sortedWith(compareBy({ !it.label.lowercase().startsWith(query) }, { it.sortKey }))
    }

    private fun batteryBar(pct: Int, charging: Boolean): String {
        val cells = 10
        val p = pct.coerceIn(0, 100)
        val filled = p * cells / 100
        val bar = "█".repeat(filled) + "░".repeat(cells - filled)
        return "[$bar] $p%" + if (charging) " +chg" else ""
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1
                )
                prev = tmp
            }
        }
        return dp[b.length]
    }

    companion object {
        private val WS = Regex("\\s+")
        private const val TERMUX_PACKAGE = "com.termux"
        val ACCENTS = listOf("orange", "green", "amber", "red", "cyan", "mono")

        private val DOLPHIN = listOf(
            "      __",
            "    <(o )___   fz // launcher",
            "     ( ._> /   a11 project",
            "      `---'"
        )

        private val COMMANDS = linkedMapOf(
            "help" to "this command index",
            "common" to "common command cheat sheet",
            "ls" to "list every app (A–Z)",
            "-apps" to "pop up the A–Z app drawer  [drawer]",
            "dir" to "list every app (A–Z)",
            "find <q>" to "search apps  [search grep f]",
            "open <q>" to "launch app  [run ./<q>]",
            "info <q>" to "package + version",
            "create …" to "make alias / note / pin",
            "pin <q>" to "add app to thumb dock  [unpin]",
            "fav" to "show pinned dock apps",
            "alias" to "list / set / rm aliases",
            "note" to "scratch notes  [note ls|rm i]",
            "wgt -<q>" to "embed an app widget (wgt -termux)",
            "edit <f>" to "open a file in the code editor  [code]",
            "sh <cmd>" to "native android shell (pm, am, getprop)",
            "lx <cmd>" to "run in Termux (ping, curl, nmap, ssh)",
            "win <cmd>" to "ssh relay to a remote windows host",
            "battery" to "battery readout",
            "sysinfo" to "device info  [neofetch]",
            "flipper" to "Flipper Zero panel + companion",
            "degoogle" to "safe Google removal checklist",
            "tools" to "quick hacker/dev links",
            "settings" to "android settings  [wifi bt loc dev]",
            "uninstall <q>" to "remove an app  [rm]",
            "theme <n>" to "accent: ${ACCENTS.joinToString(" ")}",
            "-pp" to "PowerShell mode — blue shell + win relay  [-fz exits]",
            "clear" to "wipe screen  [cls]",
            "date" to "date + time",
            "history" to "recent commands",
            "pwd / cd / cat" to "launcher pseudo-shell basics",
            "termux" to "open real shell for Unix commands",
            "<app name>" to "launch a unique app directly",
        )

        private val MAN = mapOf(
            "common" to """quick launcher command map:
  ls / dir / find / info        app control
  camera / phone / messages     launch unique app matches
  pin / fav / unpin             bottom dock
  note / cat notes              scratch notes
  pwd / cd apps / cd settings   pseudo-shell navigation
  termux / bash / pkg / ssh     hand off to Termux""",
            "create" to """make things from the prompt:
  create alias <name>=<command>   shorthand, e.g.  create alias g=open chrome
  create note  <text>             save a quick scratch note
  create pin   <app>              add an app to the bottom dock
the same effects exist as bare commands: alias / note / pin.""",
            "find" to """search installed apps by name or package.
  find <query>         e.g.  find term
results are tappable — tap a row to launch it.""",
            "open" to """launch an app by fuzzy name or package.
  open <query>   ·   run <query>   ·   ./<query>
exact label/package wins; otherwise the best match launches,
and ambiguous queries list candidates to tap.""",
            "pin" to """pin / unpin an app to the thumb dock at the bottom:
  pin <app>      add      unpin <app>   remove
pinned apps sit where your thumb rests for one-tap launch.""",
            "alias" to """command shortcuts:
  alias                list all
  alias g=open chrome  define
  alias rm g           remove  (also: unalias g)
running an alias expands it before execution.""",
            "note" to """tiny scratchpad for payloads, IPs, snippets:
  note <text>    add        note ls       list
  note rm <i>    delete i    note clear    wipe all""",
            "edit" to """open the built-in code editor from the terminal:
  edit <file>    open <file> (creating it if new), e.g.  edit main.c
  edit / code    list the workspace and jump to the editor
also: nano / vim / vi <file>. write code, then ▶ run in the editor
compiles/runs it back here via the sh / lx bridges.""",
            "wgt" to """embed real Android app-widgets in the terminal:
  wgt -<app>     bind + show a widget, e.g.  wgt -termux
  wgt -          list every widget provider (tap to embed)
  wgt            list embedded widgets
  wgt rm <i>     remove widget i
widgets render live at the top of the terminal scrollback.
Android asks once for widget-bind permission — allow it.
Termux:Widget runs your ~/.shortcuts scripts in one tap.""",
            "degoogle" to """safe de-Google checklist for this phone:
  keep Play services, Play Store, GSF, WebView, NetworkStack
  disable consumer Google apps first
  test calls, SMS, camera, WebView screens, push notifications, maps, and updates
run the desktop manager script for actual package changes.""",
            "termux" to """real shell handoff:
  termux / shell / sh / bash      open Termux
  pkg / apt / python / git / ssh  show an open-Termux action
fzsh does not execute binaries directly because it is the home launcher.""",
            "sh" to """native Android shell — runs in this app via `sh -c`.
  sh <command>     e.g.  sh pm list packages -3
  sh getprop ro.build.version.release
user-space only: no root, so privileged cmds are denied.""",
            "lx" to """Linux via the Termux environment (real binaries):
  lx <command>     e.g.  lx nmap -sn 192.168.1.0/24
needs Termux (F-Droid) with 'allow-external-apps=true'
in ~/.termux/termux.properties. stdout returns here async.""",
            "win" to """Windows via SSH relay to a remote OpenSSH host:
  win connect <user>@<host>[:port]   then type password (hidden)
  win <command>      e.g.  win Get-Process   ·   win ipconfig
  win status | win disconnect
credentials stay in memory only; host-key check is disabled,
so only target hosts you control.""",
            "theme" to """switch the accent color:
  theme <name>   one of: ${ACCENTS.joinToString(" ")}
'mono' is pure greyscale; 'green' is classic matrix.""",
        )

        private val LINKS = listOf(
            "flipperzero.one" to "https://flipperzero.one",
            "docs.flipper.net" to "https://docs.flipper.net",
            "lab.flipper.net — apps" to "https://lab.flipper.net",
            "firmware (github)" to "https://github.com/flipperdevices/flipperzero-firmware",
            "Awesome Flipper" to "https://github.com/djsime1/awesome-flipperzero",
        )

        private val KNOWN = listOf(
            "help", "man", "ls", "apps", "find", "search", "grep", "open", "run", "info",
            "dir", "common", "cmd", "cmds", "create", "pin", "unpin", "fav", "alias", "unalias",
            "note", "theme", "clear", "reboot", "battery", "sysinfo", "neofetch", "date",
            "whoami", "history", "echo", "pwd", "cd", "cat", "less", "more", "head", "tail",
            "ps", "top", "kill", "jobs", "termux", "shell", "sh", "bash", "sudo", "su",
            "pkg", "apt", "python", "python3", "git", "ssh", "curl", "wget", "nmap", "nc",
            "pm", "am", "logcat", "dumpsys", "getprop", "setprop",
            "settings", "wifi", "bt", "loc", "dev", "flipper", "degoogle", "privacy", "tools",
            "uninstall", "about", "wgt", "widget", "sh", "lx", "win",
            "edit", "code", "nano", "vim", "vi",
            "-pp", "-fz", "pwsh", "powershell", "fzsh",
        )
    }
}
