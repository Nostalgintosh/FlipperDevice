package com.a11.hackerboard

/**
 * Keyboard model + layouts.
 *
 * A [Key] either emits [output] text or triggers a special [code]. Long-press
 * emits the secondary [hintOutput] (text) or [hintCode] (action). Layouts are
 * just rows of keys; the view sizes each key by its [weight].
 */

/** Sentinel inside an [Key.output] meaning "leave the caret here after insert". */
const val CURSOR = '\u0001'

enum class KeyCode {
    NONE,

    // Modifiers (one-shot: tap = armed, tap again = locked, tap again = off)
    SHIFT, CTRL, ALT,

    // Editing / whitespace
    BACKSPACE, ENTER, TAB, ESC, SPACE,

    // Navigation (the cluster phones usually hide)
    LEFT, RIGHT, UP, DOWN, HOME, END, PAGE_UP, PAGE_DOWN, FORWARD_DEL, INSERT,

    // Function row
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,

    // Layer switches
    LAYER_ABC, LAYER_SYM, LAYER_FUNC,

    // Utilities
    SWITCH_IME, SETTINGS, PASTE,
}

enum class KeyStyle { NORMAL, MODIFIER, ACTION, ACCENT }

/** A modifier's latch state. */
enum class ModState { OFF, ON, LOCK }

data class Key(
    val label: String,
    val output: String = label,
    val code: KeyCode = KeyCode.NONE,
    val hintLabel: String? = null,
    val hintOutput: String? = null,
    val hintCode: KeyCode = KeyCode.NONE,
    val weight: Float = 1f,
    val style: KeyStyle = KeyStyle.NORMAL,
    val repeatable: Boolean = false,
    // Unexpected-Keyboard-style corner symbols, reached by a diagonal swipe.
    val nw: String? = null,
    val ne: String? = null,
    val sw: String? = null,
    val se: String? = null,
) {
    val hasHint: Boolean get() = hintCode != KeyCode.NONE || hintOutput != null
    val hasCorners: Boolean get() = nw != null || ne != null || sw != null || se != null
}

typealias Row = List<Key>
typealias Layout = List<Row>

enum class Layer { ABC, SYM, FUNC }

// --- terse builders -------------------------------------------------------

private fun ch(
    label: String,
    hint: String? = null,
    weight: Float = 1f,
    nw: String? = null,
    ne: String? = null,
    sw: String? = null,
    se: String? = null,
) = Key(
    label = label, hintLabel = hint, hintOutput = hint, weight = weight,
    nw = nw, ne = ne, sw = sw, se = se,
)

private fun txt(label: String, output: String, weight: Float = 1f, style: KeyStyle = KeyStyle.NORMAL) =
    Key(label = label, output = output, weight = weight, style = style)

/** A bracket/quote pair that drops the caret between the two characters. */
private fun pair(label: String, open: String, close: String) =
    Key(label = label, output = "$open$CURSOR$close")

private fun act(
    label: String,
    code: KeyCode,
    weight: Float = 1f,
    style: KeyStyle = KeyStyle.ACTION,
    repeatable: Boolean = false,
    hint: String? = null,
    hintCode: KeyCode = KeyCode.NONE,
) = Key(
    label = label, code = code, weight = weight, style = style,
    repeatable = repeatable, hintLabel = hint, hintCode = hintCode,
)

// --- layouts --------------------------------------------------------------

object Layouts {

    /** Primary letters — with a permanent number row, Tab, Esc and a home-row Ctrl. */
    val ABC: Layout = listOf(
        listOf(
            act("esc", KeyCode.ESC, weight = 1.3f, style = KeyStyle.MODIFIER),
            ch("1", ne = "!"), ch("2", ne = "@"), ch("3", ne = "#"), ch("4", ne = "$"),
            ch("5", ne = "%"), ch("6", ne = "^"), ch("7", ne = "&"), ch("8", ne = "*"),
            ch("9", ne = "("), ch("0", ne = ")"),
        ),
        // Top row: swipe ↗ for the digit, ↖ for its shifted symbol.
        listOf(
            act("tab", KeyCode.TAB, weight = 1.3f, style = KeyStyle.MODIFIER),
            ch("q", nw = "!", ne = "1"), ch("w", nw = "@", ne = "2"), ch("e", nw = "#", ne = "3"),
            ch("r", nw = "$", ne = "4"), ch("t", nw = "%", ne = "5"), ch("y", nw = "^", ne = "6"),
            ch("u", nw = "&", ne = "7"), ch("i", nw = "*", ne = "8"), ch("o", nw = "(", ne = "9"),
            ch("p", nw = ")", ne = "0"),
        ),
        // Home row: every bracket and operator a diagonal swipe away.
        listOf(
            act("ctrl", KeyCode.CTRL, weight = 1.5f, style = KeyStyle.MODIFIER,
                hint = "alt", hintCode = KeyCode.ALT),
            ch("a", ne = "`", se = "~"), ch("s", ne = "(", se = ")"), ch("d", ne = "[", se = "]"),
            ch("f", ne = "{", se = "}"), ch("g", ne = "<", se = ">"), ch("h", ne = "-", se = "_"),
            ch("j", ne = "=", se = "+"), ch("k", ne = "/", se = "\\"), ch("l", ne = "|", se = "&"),
        ),
        // Bottom letter row ends with Backspace (like Unexpected Keyboard).
        listOf(
            act("⇧", KeyCode.SHIFT, weight = 1.5f, style = KeyStyle.MODIFIER),
            ch("z", se = "\""), ch("x", se = "'"), ch("c", se = "`"), ch("v", se = ":"),
            ch("b", se = ";"), ch("n", se = "!"), ch("m", se = "?"),
            ch(",", ne = "<", se = ";"), ch(".", ne = ">", se = ":"), ch("/", ne = "?", se = "\\"),
            act("⌫", KeyCode.BACKSPACE, weight = 1.5f, repeatable = true),
        ),
        // Bottom row ends with Enter (the "Done" position).
        listOf(
            act("SYM", KeyCode.LAYER_SYM, weight = 1.5f, style = KeyStyle.MODIFIER),
            act("Fn", KeyCode.LAYER_FUNC, weight = 1.2f, style = KeyStyle.MODIFIER),
            act("space", KeyCode.SPACE, weight = 3f, style = KeyStyle.NORMAL),
            act("←", KeyCode.LEFT, repeatable = true),
            act("↑", KeyCode.UP, repeatable = true),
            act("↓", KeyCode.DOWN, repeatable = true),
            act("→", KeyCode.RIGHT, repeatable = true),
            act("⏎", KeyCode.ENTER, weight = 1.7f, style = KeyStyle.ACCENT),
        ),
    )

    /** Symbols / programming — every glyph reachable without a modifier. */
    val SYM: Layout = listOf(
        listOf(
            ch("!"), ch("@"), ch("#"), ch("$"), ch("%"),
            ch("^"), ch("&"), ch("*"), ch("("), ch(")"),
            act("⌫", KeyCode.BACKSPACE, weight = 1.3f, repeatable = true),
        ),
        listOf(
            ch("~"), ch("`"), ch("|"), ch("\\"), ch("/"),
            ch("-"), ch("_"), ch("="), ch("+"), ch("?"),
        ),
        listOf(
            ch("{"), ch("}"), ch("["), ch("]"), ch("<"),
            ch(">"), ch(":"), ch(";"), ch("\""), ch("'"),
        ),
        // The coder row: auto-paired brackets/quotes (caret lands inside) + operators.
        listOf(
            pair("()", "(", ")"), pair("[]", "[", "]"), pair("{}", "{", "}"),
            pair("\"\"", "\"", "\""), pair("''", "'", "'"), pair("<>", "<", ">"),
            txt("->", "->"), txt("=>", "=>"), txt("&&", "&&"), txt("||", "||"),
        ),
        listOf(
            act("ABC", KeyCode.LAYER_ABC, weight = 1.6f, style = KeyStyle.MODIFIER),
            act("ctrl", KeyCode.CTRL, weight = 1.3f, style = KeyStyle.MODIFIER,
                hint = "alt", hintCode = KeyCode.ALT),
            act("space", KeyCode.SPACE, weight = 3.5f, style = KeyStyle.NORMAL),
            act("←", KeyCode.LEFT, repeatable = true),
            act("→", KeyCode.RIGHT, repeatable = true),
            act("⏎", KeyCode.ENTER, weight = 1.5f, style = KeyStyle.ACCENT),
        ),
    )

    /** Function keys + a full navigation cluster + modifiers for combos. */
    val FUNC: Layout = listOf(
        listOf(
            act("F1", KeyCode.F1), act("F2", KeyCode.F2), act("F3", KeyCode.F3),
            act("F4", KeyCode.F4), act("F5", KeyCode.F5), act("F6", KeyCode.F6),
            act("F7", KeyCode.F7), act("F8", KeyCode.F8), act("F9", KeyCode.F9),
            act("F10", KeyCode.F10), act("F11", KeyCode.F11), act("F12", KeyCode.F12),
        ),
        listOf(
            act("esc", KeyCode.ESC, style = KeyStyle.MODIFIER),
            act("home", KeyCode.HOME), act("↑", KeyCode.UP, repeatable = true),
            act("end", KeyCode.END), act("pgUp", KeyCode.PAGE_UP),
            act("ins", KeyCode.INSERT),
            act("⌫", KeyCode.BACKSPACE, weight = 1.2f, repeatable = true),
        ),
        listOf(
            act("tab", KeyCode.TAB, style = KeyStyle.MODIFIER),
            act("←", KeyCode.LEFT, repeatable = true),
            act("↓", KeyCode.DOWN, repeatable = true),
            act("→", KeyCode.RIGHT, repeatable = true),
            act("pgDn", KeyCode.PAGE_DOWN), act("del", KeyCode.FORWARD_DEL),
            act("⏎", KeyCode.ENTER, weight = 1.2f, style = KeyStyle.ACCENT),
        ),
        listOf(
            act("ABC", KeyCode.LAYER_ABC, weight = 1.6f, style = KeyStyle.MODIFIER),
            act("ctrl", KeyCode.CTRL, weight = 1.3f, style = KeyStyle.MODIFIER,
                hint = "alt", hintCode = KeyCode.ALT),
            act("⇧", KeyCode.SHIFT, weight = 1.3f, style = KeyStyle.MODIFIER),
            act("space", KeyCode.SPACE, weight = 3.5f, style = KeyStyle.NORMAL),
            act("home", KeyCode.HOME), act("end", KeyCode.END),
        ),
    )

    fun forLayer(layer: Layer): Layout = when (layer) {
        Layer.ABC -> ABC
        Layer.SYM -> SYM
        Layer.FUNC -> FUNC
    }
}
