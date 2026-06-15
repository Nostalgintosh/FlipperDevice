package com.a11.flipperlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.a11.flipperlauncher.terminal.TerminalMode
import com.a11.flipperlauncher.ui.components.CommandInput
import com.a11.flipperlauncher.ui.components.Dock
import com.a11.flipperlauncher.ui.components.StatusBar
import com.a11.flipperlauncher.ui.components.TabBar
import com.a11.flipperlauncher.ui.theme.Fz
import com.a11.flipperlauncher.ui.theme.LocalAccent
import com.a11.flipperlauncher.vm.LauncherViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherRoot(vm: LauncherViewModel) {
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { 2 })
    var command by remember { mutableStateOf("") }

    // PowerShell skin is a terminal-page concept; the code page stays Flipper-black.
    val psActive = vm.terminalMode == TerminalMode.PowerShell

    // ---- code editor state, hoisted so the relocated buttons + symbol row (bottom zone)
    //      and the editor body (pager) share one cursor-aware text value. ----
    var showFiles by remember { mutableStateOf(vm.codeName == null) }
    var showNewDialog by remember { mutableStateOf(false) }
    var codeTfv by remember { mutableStateOf(TextFieldValue(vm.codeText)) }
    // Resync when the open file changes (e.g. `edit <file>` from the terminal); reveal the editor.
    LaunchedEffect(vm.codeName) {
        codeTfv = TextFieldValue(vm.codeText)
        if (vm.codeName != null) showFiles = false
    }
    val onCodeChange: (TextFieldValue) -> Unit = {
        codeTfv = it
        if (it.text != vm.codeText) vm.updateCodeText(it.text)
    }
    val editing = !showFiles && vm.codeName != null

    // engine-requested page jumps (e.g. the `apps` command, or HOME pressed)
    LaunchedEffect(vm.requestedPage) {
        vm.requestedPage?.let { pager.animateScrollToPage(it); vm.consumePageRequest() }
    }

    // ticking clock for the status bar
    var clock by remember { mutableStateOf(now()) }
    LaunchedEffect(Unit) {
        while (true) { clock = now(); delay(15_000) }
    }

    // live device vitals (storage / RAM / CPU / network) — sampled while visible
    LaunchedEffect(Unit) {
        while (true) { vm.sampleStats(); delay(3_000) }
    }

    BackHandler(enabled = pager.currentPage != 0) {
        scope.launch { pager.animateScrollToPage(0) }
    }

    val favs = remember(vm.apps, vm.favorites) { vm.favoriteApps() }

    // APPS overlay: the filter input live-narrows the A–Z list (README behavior).
    val shownApps = remember(vm.apps, vm.query) {
        val q = vm.query.trim()
        if (q.isEmpty()) vm.apps
        else vm.apps.filter { it.label.contains(q, true) || it.packageName.contains(q, true) }
    }

    val rootBg = if (psActive && pager.currentPage == 0) Fz.PsBlue else Fz.Black
    Box(Modifier.fillMaxSize().background(rootBg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {

            StatusBar(clock, vm.batteryPct, vm.charging, pager.currentPage, vm.stats)

            Box(Modifier.fillMaxWidth().weight(1f)) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        0 -> TerminalScreen(
                            lines = vm.lines,
                            onAction = vm::onAction,
                            widgetIds = vm.widgetIds,
                            widgetHost = vm.widgetHost,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> CodeEditorScreen(
                            fileName = vm.codeName,
                            value = codeTfv,
                            dirty = vm.codeDirty,
                            files = vm.codeFiles,
                            dirPath = vm.codeDirPath,
                            showFiles = showFiles,
                            showNewDialog = showNewDialog,
                            onValueChange = onCodeChange,
                            onOpen = { name -> showFiles = false; vm.openCodeFile(name) },
                            onNewFile = { name -> showNewDialog = false; showFiles = false; vm.newCodeFile(name) },
                            onRequestNew = { showNewDialog = true },
                            onDismissNew = { showNewDialog = false },
                            onDelete = vm::deleteCodeFile,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // ---- bottom thumb zone: dock + input + tabs --------------------
            val barSurface = if (psActive && pager.currentPage == 0) Fz.PsBlueHi else Fz.Surface
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(barSurface)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(accent))
                // Terminal keeps the thumb dock + command line; the Code editor swaps in its
                // relocated controls (symbol row + run/new/save) so they sit under the thumb.
                // Apps live in an overlay (type `-apps`), not a swipe page.
                if (pager.currentPage == 0) {
                    Dock(
                        apps = favs,
                        onLaunch = { vm.launchApp(it.packageName) },
                        onUnpin = { vm.togglePin(it.packageName) },
                    )
                    val masking = vm.awaitingWinPassword
                    CommandInput(
                        value = command,
                        onValueChange = { command = it },
                        onSubmit = { vm.exec(command); command = "" },
                        prompt = when { masking -> "passwd>"; psActive -> "PS>"; else -> "fz>" },
                        placeholder = when {
                            masking -> "password — hidden, not stored"
                            psActive -> "PowerShell · -fz exits"
                            else -> "type a command · help"
                        },
                        mask = masking,
                        promptColor = if (psActive && !masking) Fz.PsPrompt else null,
                    )
                } else {
                    if (editing) SymbolKeyRow(value = codeTfv, onValueChange = onCodeChange)
                    EditorActionBar(
                        showFiles = showFiles,
                        fileOpen = vm.codeName != null,
                        dirty = vm.codeDirty,
                        onToggleFiles = { showFiles = !showFiles },
                        onRun = vm::runOpenFile,
                        onNew = { showNewDialog = true },
                        onSave = vm::saveCode,
                    )
                }
                TabBar(selected = pager.currentPage, onSelect = { scope.launch { pager.animateScrollToPage(it) } })
            }
        }

        // APPS A–Z drawer as a pop-up over the terminal — opened by `-apps` (or
        // drawer / az / cd apps). Back or ✕ dismisses it.
        if (vm.showApps) {
            BackHandler { vm.hideApps() }
            AppsOverlay(
                apps = shownApps,
                favorites = vm.favorites,
                query = vm.query,
                onQuery = vm::updateQuery,
                onLaunch = { vm.launchApp(it.packageName); vm.hideApps() },
                onPinToggle = { vm.togglePin(it.packageName) },
                onInfo = { vm.openAppDetails(it.packageName) },
                onUninstall = { vm.uninstallApp(it.packageName) },
                onClose = vm::hideApps,
            )
        }
    }
}

private val clockFmt = SimpleDateFormat("HH:mm", Locale.US)
private fun now(): String = clockFmt.format(Date())
