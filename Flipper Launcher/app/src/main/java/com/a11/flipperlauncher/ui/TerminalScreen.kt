package com.a11.flipperlauncher.ui

import android.view.ViewGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.a11.flipperlauncher.data.WidgetHostManager
import com.a11.flipperlauncher.terminal.LineKind
import com.a11.flipperlauncher.terminal.TermAction
import com.a11.flipperlauncher.terminal.TermLine
import com.a11.flipperlauncher.ui.theme.Fz
import com.a11.flipperlauncher.ui.theme.LocalAccent
import com.a11.flipperlauncher.ui.theme.Mono
import com.a11.flipperlauncher.ui.theme.kindColor

@Composable
fun TerminalScreen(
    lines: List<TermLine>,
    onAction: (TermAction) -> Unit,
    widgetIds: List<Int>,
    widgetHost: WidgetHostManager,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size, widgetIds.size) {
        val last = widgetIds.size + lines.size - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp),
    ) {
        // live widget tray — pinned at the top of the scrollback
        itemsIndexed(widgetIds, key = { _, id -> "wgt$id" }) { index, id ->
            WidgetItem(index, id, widgetHost, onAction)
        }
        items(lines.size) { i -> TermLineView(lines[i], onAction) }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun WidgetItem(
    index: Int,
    id: Int,
    host: WidgetHostManager,
    onAction: (TermAction) -> Unit,
) {
    val accent = LocalAccent.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val info = remember(id) { host.info(id) }
    val label = remember(id) {
        runCatching { info?.loadLabel(context.packageManager) }.getOrNull() ?: "widget#$id"
    }
    val height = remember(id) {
        val dp = with(density) { (info?.minHeight ?: 0).toDp() }
        if (dp < 90.dp) 200.dp else dp.coerceAtMost(340.dp)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "wgt[$index] $label",
                color = Fz.Muted,
                fontFamily = Mono,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "✕",
                color = accent,
                fontFamily = Mono,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable { onAction(TermAction.RemoveWidget(index)) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        val view = remember(id) { host.createView(context, id) }
        if (view != null) {
            AndroidView(
                factory = {
                    (view.parent as? ViewGroup)?.removeView(view)
                    view
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Fz.Divider, RoundedCornerShape(10.dp)),
            )
        } else {
            Text("✗ widget unavailable — wgt rm $index", color = Fz.Red, fontFamily = Mono, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TermLineView(l: TermLine, onAction: (TermAction) -> Unit) {
    val accent = LocalAccent.current
    val color = if (l.action != null) accent else kindColor(l.kind, accent)
    val isArt = l.kind == LineKind.ASCII
    val base = Modifier.fillMaxWidth()
    val mod = if (l.action != null) base.clickable { onAction(l.action) } else base
    Text(
        text = l.text,
        color = color,
        fontFamily = Mono,
        fontSize = if (isArt) 12.sp else 13.sp,
        lineHeight = if (isArt) 12.sp else 18.sp, // art: 1.0x so strokes connect
        softWrap = !isArt, // keep ASCII art on one line
        overflow = TextOverflow.Clip,
        modifier = mod.padding(vertical = 1.dp),
    )
}
