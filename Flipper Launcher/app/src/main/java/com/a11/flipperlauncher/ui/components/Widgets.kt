package com.a11.flipperlauncher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a11.flipperlauncher.data.AppInfo
import com.a11.flipperlauncher.data.SystemStats
import com.a11.flipperlauncher.ui.theme.Fz
import java.util.Locale
import com.a11.flipperlauncher.ui.theme.LocalAccent
import com.a11.flipperlauncher.ui.theme.Mono
import com.a11.flipperlauncher.ui.theme.Pixel
import androidx.compose.material3.Text

@Composable
fun AppIcon(app: AppInfo, size: Dp) {
    val icon = app.icon
    if (icon != null) {
        Image(bitmap = icon, contentDescription = app.label, modifier = Modifier.size(size))
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(Fz.SurfaceHi),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                app.initial.toString(),
                color = LocalAccent.current,
                fontFamily = Pixel,
                fontSize = (size.value * 0.44f).sp,
            )
        }
    }
}

@Composable
fun StatusBar(
    time: String,
    batteryPct: Int,
    charging: Boolean,
    page: Int,
    stats: SystemStats,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("fz", color = accent, fontFamily = Pixel, fontSize = 13.sp)
        Spacer(Modifier.width(5.dp))
        Text(if (page == 0) "term" else "code", color = Fz.Muted, fontFamily = Pixel, fontSize = 9.sp)

        // top-middle: live device vitals
        Spacer(Modifier.weight(1f))
        SystemReadout(stats)
        Spacer(Modifier.weight(1f))

        Text(time, color = Fz.Text, fontFamily = Mono, fontSize = 12.sp)
        Spacer(Modifier.width(7.dp))
        BatteryReadout(batteryPct, charging)
    }
}

/** Centered cluster of device vitals — storage, RAM, CPU, network. */
@Composable
private fun SystemReadout(s: SystemStats, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Stat("disk", "${s.storageUsedPct}%", warn = s.storageUsedPct >= 90)
        Stat("mem", "${s.ramUsedPct}%", warn = s.ramUsedPct >= 90)
        Stat("cpu", cpuValue(s), warn = s.cpuPct >= 90)
        Stat("net", s.netLabel, warn = !s.online)
    }
}

private fun cpuValue(s: SystemStats): String = when {
    s.cpuPct in 0..100 -> "${s.cpuPct}%"
    s.cpuMhz > 0 -> String.format(Locale.US, "%.1fG", s.cpuMhz / 1000f)
    s.cpuCores > 0 -> "${s.cpuCores}c"
    else -> "—"
}

@Composable
private fun Stat(label: String, value: String, warn: Boolean = false) {
    val accent = LocalAccent.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Fz.Muted, fontFamily = Mono, fontSize = 9.sp)
        Spacer(Modifier.width(3.dp))
        Text(value, color = if (warn) Fz.Red else accent, fontFamily = Mono, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun BatteryReadout(pct: Int, charging: Boolean) {
    val accent = LocalAccent.current
    val cells = 5
    val p = pct.coerceIn(0, 100)
    val filled = p * cells / 100
    val bar = buildString {
        append('[')
        repeat(cells) { append(if (it < filled) '█' else '░') }
        append(']')
    }
    val color = if (p <= 15) Fz.Red else accent
    Text(bar, color = color, fontFamily = Mono, fontSize = 13.sp)
    Text(" ${p}%" + if (charging) "+" else "", color = Fz.Text, fontFamily = Mono, fontSize = 13.sp)
}

/** Thumb dock of pinned apps — the most-reached zone on a phone. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Dock(apps: List<AppInfo>, onLaunch: (AppInfo) -> Unit, onUnpin: (AppInfo) -> Unit, modifier: Modifier = Modifier) {
    if (apps.isEmpty()) {
        Row(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("dock empty · long-press an app or run 'pin <app>'", color = Fz.Muted, fontFamily = Mono, fontSize = 11.sp)
        }
        return
    }
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        apps.forEach { app ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onUnpin(app) })
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(app, 38.dp)
                Spacer(Modifier.height(3.dp))
                Text(
                    app.label.take(8),
                    color = Fz.Muted,
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/** The persistent bottom input — command on the terminal page, filter on the apps page. */
@Composable
fun CommandInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    prompt: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    mask: Boolean = false,
    promptColor: Color? = null,
) {
    val accent = LocalAccent.current
    val promptTint = promptColor ?: accent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Fz.SurfaceHi)
            .border(1.dp, accent, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prompt, color = promptTint, fontFamily = Mono, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = Fz.Muted, fontFamily = Mono, fontSize = 14.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Fz.Text, fontFamily = Mono, fontSize = 14.sp),
                cursorBrush = SolidColor(accent),
                visualTransformation = if (mask) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = if (mask) KeyboardType.Password else KeyboardType.Ascii,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = { onSubmit() },
                    onDone = { onSubmit() },
                    onSearch = { onSubmit() },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            onSubmit()
                            true
                        } else {
                            false
                        }
                    },
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("⏎", color = accent, fontFamily = Mono, fontSize = 16.sp, modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .combinedClickableSimple { onSubmit() }
            .padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** Inverted-selection segmented control: TERMINAL | CODE. (Apps pop up via `-apps`.) */
@Composable
fun TabBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Tab(">_ TERMINAL", selected == 0, Modifier.weight(1f)) { onSelect(0) }
        Spacer(Modifier.width(8.dp))
        Tab("</> CODE", selected == 1, Modifier.weight(1f)) { onSelect(1) }
    }
}

@Composable
private fun Tab(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) accent else Fz.Surface)
            .border(1.dp, if (active) accent else Fz.Divider, RoundedCornerShape(8.dp))
            .combinedClickableSimple(onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Fz.Black else Fz.Muted,
            fontFamily = Pixel,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
fun SectionHeader(letter: Char, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Row(
        modifier = modifier.fillMaxWidth().background(Fz.Black).padding(start = 14.dp, end = 28.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(letter.toString(), color = accent, fontFamily = Pixel, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Fz.Divider))
    }
}

/** Small helper so we can attach a click without pulling Material ripple everywhere. */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableSimple(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)
