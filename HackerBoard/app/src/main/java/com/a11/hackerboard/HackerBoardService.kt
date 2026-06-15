package com.a11.hackerboard

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The HackerBoard IME. Assembles the input view (snippet strip + key grid),
 * tracks latched modifiers, and translates every key into the right
 * [InputConnection] call — committing text for letters, but firing real
 * [KeyEvent]s for Tab/Esc/arrows/F-keys and Ctrl/Alt combos so things like
 * Ctrl+C in a terminal and Shift+Arrow selection actually work.
 */
class HackerBoardService : InputMethodService(), KeyboardView.Listener {

    private val density get() = resources.displayMetrics.density

    private lateinit var theme: KbTheme
    private var keyboardView: KeyboardView? = null

    private var currentLayer = Layer.ABC
    private var shift = ModState.OFF
    private var ctrl = ModState.OFF
    private var alt = ModState.OFF

    override fun onCreateInputView(): View {
        theme = buildTheme(Prefs.themeIndex(this))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.bg)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        root.addView(buildSnippetBar())

        val kv = KeyboardView(this).apply {
            listener = this@HackerBoardService
            configure(
                theme,
                Prefs.keyHeightDp(this@HackerBoardService),
                Prefs.haptics(this@HackerBoardService),
                Prefs.sound(this@HackerBoardService),
            )
            setRows(Layouts.forLayer(currentLayer))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        keyboardView = kv
        root.addView(kv)

        // The IME window uses a light default theme, so the platform's Force Dark
        // would auto-invert our dark Canvas drawing (dark fills come out white).
        // Opt the whole input view subtree out so our colors render as authored.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            root.isForceDarkAllowed = false
        }
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Re-read prefs so changes from the setup screen take effect, and reset
        // transient state for a predictable starting point.
        theme = buildTheme(Prefs.themeIndex(this))
        currentLayer = Layer.ABC
        shift = ModState.OFF
        ctrl = ModState.OFF
        alt = ModState.OFF
        keyboardView?.apply {
            configure(theme, Prefs.keyHeightDp(this@HackerBoardService),
                Prefs.haptics(this@HackerBoardService), Prefs.sound(this@HackerBoardService))
            setRows(Layouts.forLayer(currentLayer))
            setModifiers(shift, ctrl, alt)
        }
    }

    /** Keep the keyboard docked at the bottom instead of fullscreen in landscape. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    // --- snippet strip ----------------------------------------------------

    private fun buildSnippetBar(): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(theme.bg)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val p = dp(6)
            setPadding(p, dp(6), p, dp(6))
        }

        row.addView(chip("⌨", accent = true) { switchIme() })
        row.addView(chip("paste") { paste() })
        row.addView(divider())
        for (s in Snippets.list) row.addView(chip(s.label) { insertText(s.text) })
        row.addView(divider())
        row.addView(chip("⚙") { openSettings() })

        scroll.addView(row)
        return scroll
    }

    private fun chip(label: String, accent: Boolean = false, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            typeface = Typeface.MONOSPACE
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(if (accent) theme.onAccent else theme.text)
            val padH = dp(12)
            val padV = dp(7)
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(if (accent) theme.accent else theme.keyMod)
                setStroke(maxOf(1, dp(1)), theme.border)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(dp(3), 0, dp(3), 0) }
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(theme.border)
        layoutParams = LinearLayout.LayoutParams(maxOf(1, dp(1)), ViewGroup.LayoutParams.MATCH_PARENT)
            .apply { setMargins(dp(6), dp(2), dp(6), dp(2)) }
    }

    // --- key dispatch -----------------------------------------------------

    override fun onKey(key: Key, longPress: Boolean) {
        val ic = currentInputConnection ?: return
        if (longPress) {
            when {
                key.hintCode != KeyCode.NONE -> { handleCode(key.hintCode, ic); return }
                key.hintOutput != null -> { emitText(key.hintOutput, ic); return }
            }
        }
        if (key.code != KeyCode.NONE) handleCode(key.code, ic) else emitText(key.output, ic)
    }

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        emitText(text, ic)
    }

    private fun handleCode(code: KeyCode, ic: InputConnection) {
        when (code) {
            KeyCode.SHIFT -> { shift = cycle(shift); updateModifiers() }
            KeyCode.CTRL -> { ctrl = cycle(ctrl); updateModifiers() }
            KeyCode.ALT -> { alt = cycle(alt); updateModifiers() }

            KeyCode.LAYER_ABC -> switchLayer(Layer.ABC)
            KeyCode.LAYER_SYM -> switchLayer(Layer.SYM)
            KeyCode.LAYER_FUNC -> switchLayer(Layer.FUNC)

            KeyCode.BACKSPACE -> { sendKey(KeyEvent.KEYCODE_DEL); consumeOneShot() }
            KeyCode.ENTER -> { sendKey(KeyEvent.KEYCODE_ENTER); consumeOneShot() }
            KeyCode.TAB -> { sendKey(KeyEvent.KEYCODE_TAB); consumeOneShot() }
            KeyCode.ESC -> { sendKey(KeyEvent.KEYCODE_ESCAPE); consumeOneShot() }
            KeyCode.SPACE -> {
                if (active(ctrl) || active(alt)) sendKey(KeyEvent.KEYCODE_SPACE)
                else ic.commitText(" ", 1)
                consumeOneShot()
            }

            KeyCode.LEFT -> { sendKey(KeyEvent.KEYCODE_DPAD_LEFT); consumeOneShot() }
            KeyCode.RIGHT -> { sendKey(KeyEvent.KEYCODE_DPAD_RIGHT); consumeOneShot() }
            KeyCode.UP -> { sendKey(KeyEvent.KEYCODE_DPAD_UP); consumeOneShot() }
            KeyCode.DOWN -> { sendKey(KeyEvent.KEYCODE_DPAD_DOWN); consumeOneShot() }
            KeyCode.HOME -> { sendKey(KeyEvent.KEYCODE_MOVE_HOME); consumeOneShot() }
            KeyCode.END -> { sendKey(KeyEvent.KEYCODE_MOVE_END); consumeOneShot() }
            KeyCode.PAGE_UP -> { sendKey(KeyEvent.KEYCODE_PAGE_UP); consumeOneShot() }
            KeyCode.PAGE_DOWN -> { sendKey(KeyEvent.KEYCODE_PAGE_DOWN); consumeOneShot() }
            KeyCode.FORWARD_DEL -> { sendKey(KeyEvent.KEYCODE_FORWARD_DEL); consumeOneShot() }
            KeyCode.INSERT -> { sendKey(KeyEvent.KEYCODE_INSERT); consumeOneShot() }

            KeyCode.F1, KeyCode.F2, KeyCode.F3, KeyCode.F4, KeyCode.F5, KeyCode.F6,
            KeyCode.F7, KeyCode.F8, KeyCode.F9, KeyCode.F10, KeyCode.F11, KeyCode.F12 -> {
                sendKey(KeyEvent.KEYCODE_F1 + (code.ordinal - KeyCode.F1.ordinal))
                consumeOneShot()
            }

            KeyCode.SWITCH_IME -> switchIme()
            KeyCode.SETTINGS -> openSettings()
            KeyCode.PASTE -> { paste(); consumeOneShot() }

            KeyCode.NONE -> Unit
        }
    }

    private fun emitText(text: String, ic: InputConnection) {
        // Ctrl/Alt + a single letter or digit → dispatch as a real combo
        // (Ctrl+C, Ctrl+D, Alt+x, …) rather than typing the character.
        if ((active(ctrl) || active(alt)) && text.length == 1) {
            val kc = charToKeyCode(text[0])
            if (kc != 0) {
                sendKey(kc)
                consumeOneShot()
                return
            }
        }
        val out = if (active(shift) && text.length == 1 && text[0].isLetter()) {
            text.uppercase()
        } else {
            text
        }
        commitWithCursor(out, ic)
        consumeOneShot()
    }

    private fun insertText(text: String) {
        val ic = currentInputConnection ?: return
        commitWithCursor(text, ic)
    }

    /** Commit [text]; if it contains a [CURSOR] sentinel, park the caret there. */
    private fun commitWithCursor(text: String, ic: InputConnection) {
        val idx = text.indexOf(CURSOR)
        if (idx < 0) {
            ic.commitText(text, 1)
            return
        }
        val clean = text.replace(CURSOR.toString(), "")
        val tail = clean.length - idx
        ic.beginBatchEdit()
        ic.commitText(clean, 1)
        repeat(tail) {
            val t = SystemClock.uptimeMillis()
            ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0))
            ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT, 0))
        }
        ic.endBatchEdit()
    }

    // --- key event plumbing ----------------------------------------------

    private fun sendKey(keyCode: Int) {
        val ic = currentInputConnection ?: return
        val meta = metaState()
        val t = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    private fun metaState(): Int {
        var m = 0
        if (active(shift)) m = m or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (active(ctrl)) m = m or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (active(alt)) m = m or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        return m
    }

    private fun charToKeyCode(c: Char): Int = when (c) {
        in 'a'..'z' -> KeyEvent.KEYCODE_A + (c - 'a')
        in 'A'..'Z' -> KeyEvent.KEYCODE_A + (c - 'A')
        in '0'..'9' -> KeyEvent.KEYCODE_0 + (c - '0')
        else -> 0
    }

    // --- modifiers & layers ----------------------------------------------

    private fun active(s: ModState) = s != ModState.OFF

    private fun cycle(s: ModState) = when (s) {
        ModState.OFF -> ModState.ON
        ModState.ON -> ModState.LOCK
        ModState.LOCK -> ModState.OFF
    }

    /** Clear armed (one-shot) modifiers after they've been used; locks persist. */
    private fun consumeOneShot() {
        if (shift == ModState.ON) shift = ModState.OFF
        if (ctrl == ModState.ON) ctrl = ModState.OFF
        if (alt == ModState.ON) alt = ModState.OFF
        updateModifiers()
    }

    private fun updateModifiers() = keyboardView?.setModifiers(shift, ctrl, alt)

    private fun switchLayer(layer: Layer) {
        currentLayer = layer
        keyboardView?.setRows(Layouts.forLayer(layer))
    }

    // --- utilities --------------------------------------------------------

    private fun switchIme() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!switchToNextInputMethod(false)) showImePicker()
        } else {
            showImePicker()
        }
    }

    private fun showImePicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }

    private fun paste() {
        currentInputConnection?.performContextMenuAction(android.R.id.paste)
    }

    private fun openSettings() {
        val i = Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
