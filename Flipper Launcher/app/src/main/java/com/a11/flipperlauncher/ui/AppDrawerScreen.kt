package com.a11.flipperlauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a11.flipperlauncher.data.AppInfo
import com.a11.flipperlauncher.ui.components.AppIcon
import com.a11.flipperlauncher.ui.components.CommandInput
import com.a11.flipperlauncher.ui.components.SectionHeader
import com.a11.flipperlauncher.ui.theme.Fz
import com.a11.flipperlauncher.ui.theme.LocalAccent
import com.a11.flipperlauncher.ui.theme.Mono
import com.a11.flipperlauncher.ui.theme.Pixel
import kotlinx.coroutines.launch

/**
 * The A–Z drawer shown as a pop-up over the terminal (triggered by `-apps`).
 * A header with a close affordance sits on top, the list fills the middle, and a
 * filter input rides the bottom thumb zone. Dismiss via ✕ or the back gesture.
 */
@Composable
fun AppsOverlay(
    apps: List<AppInfo>,
    favorites: List<String>,
    query: String,
    onQuery: (String) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onPinToggle: (AppInfo) -> Unit,
    onInfo: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    onClose: () -> Unit,
) {
    val accent = LocalAccent.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Fz.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(":: APPS", color = accent, fontFamily = Pixel, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text("// ${apps.size}", color = Fz.Muted, fontFamily = Pixel, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "✕ close",
                color = accent,
                fontFamily = Mono,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(accent))
        AppDrawerScreen(
            apps = apps,
            favorites = favorites,
            onLaunch = onLaunch,
            onPinToggle = onPinToggle,
            onInfo = onInfo,
            onUninstall = onUninstall,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        CommandInput(
            value = query,
            onValueChange = onQuery,
            onSubmit = { apps.firstOrNull()?.let { onLaunch(it) } },
            prompt = "/",
            placeholder = "filter apps · type to narrow A–Z",
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerScreen(
    apps: List<AppInfo>,
    favorites: List<String>,
    onLaunch: (AppInfo) -> Unit,
    onPinToggle: (AppInfo) -> Unit,
    onInfo: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("no apps match", color = Fz.Muted, fontFamily = Mono, fontSize = 14.sp)
        }
        return
    }

    val groups = remember(apps) {
        apps.groupBy { it.initial }.toList().sortedBy { if (it.first == '#') '[' else it.first }
    }
    val headerIndex = remember(groups) {
        var idx = 0
        val m = LinkedHashMap<Char, Int>()
        groups.forEach { (c, list) -> m[c] = idx; idx += 1 + list.size }
        m
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activeLetter by remember { mutableStateOf<Char?>(null) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 2.dp, bottom = 14.dp, end = 26.dp),
        ) {
            groups.forEach { (letter, list) ->
                stickyHeader(key = "h_$letter") { SectionHeader(letter) }
                items(list, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        pinned = app.packageName in favorites,
                        onLaunch = onLaunch,
                        onPinToggle = onPinToggle,
                        onInfo = onInfo,
                        onUninstall = onUninstall,
                    )
                }
            }
        }

        AzRail(
            present = headerIndex.keys,
            onSelect = { letter ->
                val target = headerIndex[letter] ?: nearestIndex(letter, groups.map { it.first }, headerIndex)
                target?.let { scope.launch { listState.scrollToItem(it) } }
            },
            onActiveChange = { activeLetter = it },
            modifier = Modifier.align(Alignment.CenterEnd),
        )

        activeLetter?.let { c ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Fz.SurfaceHi),
                contentAlignment = Alignment.Center,
            ) {
                Text(c.toString(), color = LocalAccent.current, fontFamily = Pixel, fontSize = 40.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppInfo,
    pinned: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onPinToggle: (AppInfo) -> Unit,
    onInfo: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
) {
    val accent = LocalAccent.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { menu = true })
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app, 30.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                app.label,
                color = Fz.Text,
                fontFamily = Mono,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (pinned) Text("•", color = accent, fontFamily = Mono, fontSize = 16.sp)
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            modifier = Modifier.background(Fz.SurfaceHi),
        ) {
            DropdownMenuItem(text = { MenuText("launch") }, onClick = { menu = false; onLaunch(app) })
            DropdownMenuItem(
                text = { MenuText(if (pinned) "unpin from dock" else "pin to dock") },
                onClick = { menu = false; onPinToggle(app) },
            )
            DropdownMenuItem(text = { MenuText("app info") }, onClick = { menu = false; onInfo(app) })
            DropdownMenuItem(text = { MenuText("uninstall") }, onClick = { menu = false; onUninstall(app) })
        }
    }
}

@Composable
private fun MenuText(s: String) = Text(s, color = Fz.Text, fontFamily = Mono, fontSize = 13.sp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AzRail(
    present: Set<Char>,
    onSelect: (Char) -> Unit,
    onActiveChange: (Char?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    val letters = remember { ('A'..'Z').toList() + '#' }
    var heightPx by remember { mutableIntStateOf(1) }
    val select by rememberUpdatedState(onSelect)
    val active by rememberUpdatedState(onActiveChange)

    fun pick(y: Float) {
        val frac = (y / heightPx).coerceIn(0f, 0.9999f)
        val c = letters[(frac * letters.size).toInt().coerceIn(0, letters.size - 1)]
        active(c)
        select(c)
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { pick(it.y) },
                    onDragEnd = { active(null) },
                    onDragCancel = { active(null) },
                    onVerticalDrag = { change, _ -> pick(change.position.y) },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { pick(it.y); active(null) })
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { c ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    c.toString(),
                    color = if (c in present) accent else Color(0x55FFFFFF),
                    fontFamily = Mono,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

private fun nearestIndex(letter: Char, present: List<Char>, headerIndex: Map<Char, Int>): Int? {
    val next = present.firstOrNull { it >= letter } ?: present.lastOrNull()
    return next?.let { headerIndex[it] }
}
