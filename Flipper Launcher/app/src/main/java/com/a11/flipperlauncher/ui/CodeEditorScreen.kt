package com.a11.flipperlauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a11.flipperlauncher.ui.theme.Fz
import com.a11.flipperlauncher.ui.theme.LocalAccent
import com.a11.flipperlauncher.ui.theme.Mono
import com.a11.flipperlauncher.ui.theme.Pixel
import androidx.compose.foundation.text.KeyboardOptions

private val EXTENSIONS = listOf(
    ".c", ".cpp", ".h", ".hc", ".hpp", ".cc", ".py", ".sh", ".js", ".ts",
    ".java", ".kt", ".go", ".rs", ".rb", ".lua", ".html", ".css", ".json", ".md", ".txt",
)

/** One indent step. Used by Tab (→) on the symbol row and by auto-indent on Enter. */
private const val INDENT = "    "

@Composable
fun CodeEditorScreen(
    fileName: String?,
    value: TextFieldValue,
    dirty: Boolean,
    files: List<String>,
    dirPath: String,
    showFiles: Boolean,
    showNewDialog: Boolean,
    onValueChange: (TextFieldValue) -> Unit,
    onOpen: (String) -> Unit,
    onNewFile: (String) -> Unit,
    onRequestNew: () -> Unit,
    onDismissNew: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current

    Column(modifier.fillMaxSize().background(Fz.Black)) {
        // ---- slim header: file name + save state (run/new/save now live in the thumb zone) ----
        Row(
            Modifier.fillMaxWidth().background(Fz.Surface).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    showFiles -> "EXPLORER"
                    fileName != null -> fileName
                    else -> "no file open"
                },
                color = when {
                    showFiles -> accent
                    fileName == null -> Fz.Muted
                    else -> Fz.Text
                },
                fontFamily = if (showFiles) Pixel else Mono,
                fontSize = if (showFiles) 12.sp else 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (!showFiles && fileName != null) {
                Text(
                    if (dirty) "● unsaved" else "● saved",
                    color = if (dirty) Fz.Red else Fz.Green,
                    fontFamily = Mono, fontSize = 11.sp,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Fz.Divider))

        when {
            showFiles -> FileExplorer(
                files = files,
                dirPath = dirPath,
                current = fileName,
                onOpen = onOpen,
                onNew = onRequestNew,
                onDelete = onDelete,
            )
            fileName == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("create or open a file  →  [=]", color = Fz.Muted, fontFamily = Mono, fontSize = 14.sp)
            }
            else -> {
                Editor(value = value, fileName = fileName, dirty = dirty, onValueChange = onValueChange, modifier = Modifier.weight(1f))
                EditorStatusBar(value = value, fileName = fileName)
            }
        }
    }

    if (showNewDialog) {
        NewFileDialog(onDismiss = onDismissNew, onCreate = onNewFile)
    }
}

@Composable
private fun Editor(
    value: TextFieldValue,
    fileName: String,
    dirty: Boolean,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val lineCount = remember(value.text) { value.text.count { it == '\n' } + 1 }
    val gutter = remember(lineCount) { (1..lineCount).joinToString("\n") }
    val highlighter = remember(fileName, accent) { CodeHighlightTransformation(accent, fileName) }
    // Save-state marker, Notepad++ style: a bar in the margin on the last line only —
    // green when saved, red while there are unsaved edits. A parallel column with the
    // same font metrics keeps it aligned to the line and scrolling with the text.
    val stripe = if (dirty) Fz.Red else Fz.Green
    val marker = remember(lineCount) { "\n".repeat((lineCount - 1).coerceAtLeast(0)) + "▌" }

    Row(
        modifier
            .fillMaxSize()
            .verticalScroll(vScroll)
            .padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 6.dp),
    ) {
        Text(
            text = marker,
            color = stripe,
            fontFamily = Mono,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.width(9.dp),
        )
        Text(
            text = gutter,
            color = Fz.Muted,
            fontFamily = Mono,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(autoIndent(value, it)) },
            textStyle = TextStyle(color = Fz.Text, fontFamily = Mono, fontSize = 13.sp, lineHeight = 19.sp),
            cursorBrush = SolidColor(accent),
            visualTransformation = highlighter,
            keyboardOptions = KeyboardOptions(autoCorrect = false, capitalization = KeyboardCapitalization.None),
            modifier = Modifier.weight(1f).horizontalScroll(hScroll),
        )
    }
}

/** Notepad++-style status bar: caret position, length, encoding/EOL and language. */
@Composable
private fun EditorStatusBar(value: TextFieldValue, fileName: String) {
    val accent = LocalAccent.current
    val caret = value.selection.start.coerceIn(0, value.text.length)
    val before = value.text.substring(0, caret)
    val ln = before.count { it == '\n' } + 1
    val col = caret - (before.lastIndexOf('\n') + 1) + 1
    val eol = if (value.text.contains("\r\n")) "CRLF" else "LF"
    val lang = languageOf(fileName)
    Row(
        Modifier.fillMaxWidth().background(Fz.Surface).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Ln $ln, Col $col", color = accent, fontFamily = Mono, fontSize = 10.sp)
        Spacer(Modifier.width(12.dp))
        Text("${value.text.length} chars", color = Fz.Muted, fontFamily = Mono, fontSize = 10.sp)
        Spacer(Modifier.weight(1f))
        Text("UTF-8 · $eol · $lang", color = Fz.Muted, fontFamily = Mono, fontSize = 10.sp)
    }
}

private val SYMBOL_KEYS =
    listOf("{", "}", "(", ")", "[", "]", ";", ":", "=", "\"", "'", "<", ">", "/", "\\", "|", "&", "*", "+", "-", "_", ".", "#", "→")

/** Quick-symbol strip above the keyboard — the thing every mobile code editor adds. */
@Composable
fun SymbolKeyRow(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SYMBOL_KEYS.forEach { key ->
            Text(
                text = key,
                color = Fz.Text,
                fontFamily = Mono,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Fz.SurfaceHi)
                    .clickable { onValueChange(insertSymbol(value, key)) }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
    }
}

/** The relocated editor controls: explorer toggle, run, new, save — sits above the tab bar. */
@Composable
fun EditorActionBar(
    showFiles: Boolean,
    fileOpen: Boolean,
    dirty: Boolean,
    onToggleFiles: () -> Unit,
    onRun: () -> Unit,
    onNew: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton(if (showFiles) "[x]" else "[=]", onClick = onToggleFiles)
        Spacer(Modifier.weight(1f))
        ToolButton(text = "▶ run", enabled = fileOpen, onClick = onRun)
        Spacer(Modifier.width(6.dp))
        ToolButton("+ new", onClick = onNew)
        Spacer(Modifier.width(6.dp))
        ToolButton(text = "save", enabled = fileOpen && dirty, onClick = onSave)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileExplorer(
    files: List<String>,
    dirPath: String,
    current: String?,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val accent = LocalAccent.current
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(dirPath, color = Fz.Muted, fontFamily = Mono, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, accent, RoundedCornerShape(8.dp))
                .clickable { onNew() }.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("+ new file", color = accent, fontFamily = Mono, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))

        if (files.isEmpty()) {
            Text("no files yet — create one", color = Fz.Muted, fontFamily = Mono, fontSize = 13.sp)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(files) { name ->
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(onClick = { onOpen(name) }, onLongClick = { confirmDelete = name })
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(extTag(name), color = accent, fontFamily = Mono, fontSize = 12.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            name,
                            color = if (name == current) accent else Fz.Text,
                            fontFamily = Mono, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "x",
                            color = Fz.Muted, fontFamily = Mono, fontSize = 13.sp,
                            modifier = Modifier.clickable { confirmDelete = name }.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { name ->
        AlertDialog(
            containerColor = Fz.SurfaceHi,
            onDismissRequest = { confirmDelete = null },
            title = { Text("delete?", color = Fz.Text, fontFamily = Mono) },
            text = { Text(name, color = Fz.Muted, fontFamily = Mono, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { onDelete(name); confirmDelete = null }) {
                    Text("delete", color = Fz.Red, fontFamily = Mono)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text("cancel", color = Fz.Muted, fontFamily = Mono)
                }
            },
        )
    }
}

@Composable
private fun NewFileDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val accent = LocalAccent.current
    var name by remember { mutableStateOf("") }
    AlertDialog(
        containerColor = Fz.SurfaceHi,
        onDismissRequest = onDismiss,
        title = { Text("new file", color = Fz.Text, fontFamily = Mono) },
        text = {
            Column {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Fz.Black)
                        .border(1.dp, accent, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    if (name.isEmpty()) Text("main.c", color = Fz.Muted, fontFamily = Mono, fontSize = 14.sp)
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.trim() },
                        singleLine = true,
                        textStyle = TextStyle(color = Fz.Text, fontFamily = Mono, fontSize = 14.sp),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(autoCorrect = false, capitalization = KeyboardCapitalization.None),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    EXTENSIONS.forEach { ext ->
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Fz.Divider, RoundedCornerShape(6.dp))
                                .clickable {
                                    val base = name.substringBeforeLast('.', name).ifBlank { "untitled" }
                                    name = base + ext
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(ext, color = Fz.Muted, fontFamily = Mono, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name) }) {
                Text("create", color = if (name.isNotBlank()) accent else Fz.Muted, fontFamily = Mono)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("cancel", color = Fz.Muted, fontFamily = Mono) }
        },
    )
}

@Composable
private fun ToolButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Text(
        text = text,
        color = if (enabled) accent else Fz.Muted,
        fontFamily = Mono,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun extTag(name: String): String {
    val ext = name.substringAfterLast('.', "")
    return if (ext.isEmpty()) "[ ]" else "[${ext.take(3)}]"
}

private fun languageOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "c", "h", "hc" -> "C"
    "cpp", "cc", "cxx", "hpp" -> "C++"
    "py", "pyw" -> "Python"
    "js" -> "JavaScript"
    "ts" -> "TypeScript"
    "java" -> "Java"
    "kt" -> "Kotlin"
    "go" -> "Go"
    "rs" -> "Rust"
    "rb" -> "Ruby"
    "lua" -> "Lua"
    "sh", "bash" -> "Shell"
    "html" -> "HTML"
    "css" -> "CSS"
    "json" -> "JSON"
    "md" -> "Markdown"
    else -> "Text"
}

/** Insert a symbol at the caret. Openers and quotes auto-close with the caret between. */
private fun insertSymbol(value: TextFieldValue, key: String): TextFieldValue {
    val start = minOf(value.selection.start, value.selection.end)
    val end = maxOf(value.selection.start, value.selection.end)
    val (insert, caretOffset) = when (key) {
        "{" -> "{}" to 1
        "(" -> "()" to 1
        "[" -> "[]" to 1
        "\"" -> "\"\"" to 1
        "'" -> "''" to 1
        "→" -> INDENT to INDENT.length
        else -> key to key.length
    }
    val text = value.text.substring(0, start) + insert + value.text.substring(end)
    return TextFieldValue(text = text, selection = TextRange(start + caretOffset))
}

/**
 * When a newline was just typed, carry the previous line's leading whitespace (plus one
 * extra level after a `{` or `:`). Anything else passes through untouched.
 */
private fun autoIndent(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    val caret = new.selection.start
    val insertedNewline = new.text.length == old.text.length + 1 &&
        caret in 1..new.text.length && new.text[caret - 1] == '\n'
    if (!insertedNewline) return new

    val prevStart = new.text.lastIndexOf('\n', caret - 2).let { if (it < 0) 0 else it + 1 }
    val prevLine = new.text.substring(prevStart, caret - 1)
    val lead = prevLine.takeWhile { it == ' ' || it == '\t' }
    val extra = if (prevLine.trimEnd().endsWith('{') || prevLine.trimEnd().endsWith(':')) INDENT else ""
    val ins = lead + extra
    if (ins.isEmpty()) return new

    val text = new.text.substring(0, caret) + ins + new.text.substring(caret)
    return TextFieldValue(text = text, selection = TextRange(caret + ins.length))
}
