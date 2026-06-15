package com.a11.flipperlauncher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Multi-language syntax highlighter. Picks a [LangSpec] from the file extension,
 * then only recolors the text (length unchanged) so [OffsetMapping.Identity] holds.
 */
class CodeHighlightTransformation(
    private val accent: Color,
    private val fileName: String?,
) : VisualTransformation {
    private val spec = langFor(fileName)
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlight(text.text, accent, spec), OffsetMapping.Identity)
}

private val COMMENT = Color(0xFF6A9955)
private val STRING = Color(0xFFCE9178)
private val NUMBER = Color(0xFF7FD1C0)
private val TYPE = Color(0xFF4EC9B0)
private val PREPROC = Color(0xFFC586C0)
private val VARCOL = Color(0xFF9CDCFE)

data class LangSpec(
    val keywords: Set<String>,
    val types: Set<String>,
    val lineComment: String?,   // "//", "--", or null
    val hashComment: Boolean,   // "# …" is a comment (py / sh / rb / …)
    val preproc: Boolean,       // leading "#…" is a C preprocessor line
    val block: Boolean,         // "/* … */"
    val tripleStrings: Boolean, // python-style """ … """
    val shellVars: Boolean,     // $VAR / ${ … }
)

/** Choose a language profile from the filename's extension. */
fun langFor(name: String?): LangSpec {
    val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return when (ext) {
        "py", "pyw" -> PYTHON
        "sh", "bash", "zsh", "ksh" -> SHELL
        "rb" -> RUBY
        "lua" -> LUA
        "c", "cpp", "cc", "cxx", "h", "hpp", "hh", "hc", "ino",
        "java", "kt", "kts", "js", "jsx", "ts", "tsx", "go", "rs",
        "cs", "swift", "dart", "php", "scala", "groovy" -> CFAMILY
        else -> GENERIC
    }
}

private fun highlight(code: String, accent: Color, spec: LangSpec): AnnotatedString = buildAnnotatedString {
    append(code)
    fun paint(regex: Regex, color: Color) =
        regex.findAll(code).forEach { addStyle(SpanStyle(color = color), it.range.first, it.range.last + 1) }

    // numbers + words first; strings/comments painted last so they win on overlap.
    paint(NUMBER_RE, NUMBER)
    if (spec.keywords.isNotEmpty() || spec.types.isNotEmpty()) {
        IDENT_RE.findAll(code).forEach {
            val color = when (it.value) {
                in spec.types -> TYPE
                in spec.keywords -> accent
                else -> return@forEach
            }
            addStyle(SpanStyle(color = color), it.range.first, it.range.last + 1)
        }
    }
    if (spec.shellVars) paint(SHELLVAR_RE, VARCOL)
    if (spec.preproc) paint(PREPROC_RE, PREPROC)
    if (spec.tripleStrings) { paint(TRIPLE_D_RE, STRING); paint(TRIPLE_S_RE, STRING) }
    paint(STRING_RE, STRING)
    paint(CHAR_RE, STRING)
    when (spec.lineComment) {
        "//" -> paint(SLASH_COMMENT_RE, COMMENT)
        "--" -> paint(DASH_COMMENT_RE, COMMENT)
    }
    if (spec.hashComment) paint(HASH_COMMENT_RE, COMMENT)
    if (spec.block) paint(BLOCK_COMMENT_RE, COMMENT)
}

private val IDENT_RE = Regex("[A-Za-z_]\\w*")
private val NUMBER_RE = Regex("\\b\\d[\\d.xXa-fA-F_]*\\b")
private val STRING_RE = Regex("\"(\\\\.|[^\"\\\\\\n])*\"")
private val CHAR_RE = Regex("'(\\\\.|[^'\\\\\\n])*'")
private val TRIPLE_D_RE = Regex("\"\"\"[\\s\\S]*?\"\"\"")
private val TRIPLE_S_RE = Regex("'''[\\s\\S]*?'''")
private val SLASH_COMMENT_RE = Regex("//[^\\n]*")
private val DASH_COMMENT_RE = Regex("--[^\\n]*")
private val HASH_COMMENT_RE = Regex("#[^\\n]*")
private val PREPROC_RE = Regex("(?m)^[ \\t]*#[^\\n]*")
private val BLOCK_COMMENT_RE = Regex("/\\*[\\s\\S]*?\\*/")
private val SHELLVAR_RE = Regex("\\$\\w+|\\$\\{[^}]*\\}")

private val CFAMILY = LangSpec(
    keywords = setOf(
        "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue",
        "return", "goto", "sizeof", "typedef", "struct", "union", "enum", "class", "namespace",
        "new", "delete", "public", "private", "protected", "template", "typename", "using",
        "virtual", "override", "final", "this", "operator", "try", "catch", "throw", "throws",
        "extern", "inline", "asm", "import", "package", "func", "fn", "def", "fun", "val", "var",
        "let", "const", "static", "interface", "extends", "implements", "instanceof", "is", "as",
        "in", "out", "where", "when", "match", "loop", "mut", "pub", "use", "mod", "trait", "impl",
        "go", "defer", "chan", "select", "range", "map", "async", "await", "yield", "suspend",
        "true", "false", "null", "nil", "nullptr", "none", "object", "companion", "data", "sealed",
    ),
    types = setOf(
        "int", "char", "void", "float", "double", "long", "short", "unsigned", "signed", "bool",
        "boolean", "byte", "string", "String", "Int", "Long", "Float", "Double", "Boolean", "Char",
        "auto", "size_t", "uint", "usize", "isize", "i8", "i16", "i32", "i64", "u8", "u16", "u32",
        "u64", "f32", "f64", "str", "vec", "Vec", "Any", "Unit", "var", "let",
        "U0", "U8", "U16", "U32", "U64", "I8", "I16", "I32", "I64", "F64",
    ),
    lineComment = "//", hashComment = false, preproc = true, block = true,
    tripleStrings = false, shellVars = false,
)

private val PYTHON = LangSpec(
    keywords = setOf(
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
        "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is",
        "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with",
        "yield", "match", "case", "True", "False", "None", "self", "cls",
    ),
    types = setOf(
        "int", "str", "float", "bool", "list", "dict", "tuple", "set", "bytes", "object",
        "complex", "frozenset", "bytearray",
    ),
    lineComment = null, hashComment = true, preproc = false, block = false,
    tripleStrings = true, shellVars = false,
)

private val SHELL = LangSpec(
    keywords = setOf(
        "if", "then", "elif", "else", "fi", "for", "while", "until", "do", "done", "case", "esac",
        "function", "in", "select", "time", "return", "local", "export", "readonly", "declare",
        "set", "unset", "shift", "source", "exit", "echo", "alias", "test", "trap", "eval",
    ),
    types = emptySet(),
    lineComment = null, hashComment = true, preproc = false, block = false,
    tripleStrings = false, shellVars = true,
)

private val LUA = LangSpec(
    keywords = setOf(
        "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if",
        "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while",
    ),
    types = emptySet(),
    lineComment = "--", hashComment = false, preproc = false, block = false,
    tripleStrings = false, shellVars = false,
)

private val RUBY = LangSpec(
    keywords = setOf(
        "def", "end", "class", "module", "if", "elsif", "else", "unless", "while", "until", "for",
        "do", "begin", "rescue", "ensure", "return", "yield", "then", "case", "when", "break",
        "next", "redo", "retry", "self", "nil", "true", "false", "and", "or", "not", "in",
        "require", "require_relative", "attr_accessor", "attr_reader", "attr_writer", "puts", "raise",
    ),
    types = emptySet(),
    lineComment = null, hashComment = true, preproc = false, block = false,
    tripleStrings = false, shellVars = false,
)

private val GENERIC = LangSpec(
    keywords = emptySet(), types = emptySet(),
    lineComment = null, hashComment = false, preproc = false, block = false,
    tripleStrings = false, shellVars = false,
)
