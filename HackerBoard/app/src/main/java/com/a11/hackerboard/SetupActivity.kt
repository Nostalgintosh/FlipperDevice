package com.a11.hackerboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.view.inputmethod.InputMethodManager

/**
 * Launcher screen: walks the user through enabling/selecting HackerBoard,
 * gives a field to try it, and exposes the handful of preferences the keyboard
 * reads (accent, haptics, sound, key height).
 */
class SetupActivity : Activity() {

    private val swatches = ArrayList<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btnSwitch).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        setupAccentSwatches()

        findViewById<CheckBox>(R.id.cbHaptics).apply {
            isChecked = Prefs.haptics(this@SetupActivity)
            setOnCheckedChangeListener { _, v ->
                Prefs.of(this@SetupActivity).edit().putBoolean(Prefs.KEY_HAPTICS, v).apply()
            }
        }
        findViewById<CheckBox>(R.id.cbSound).apply {
            isChecked = Prefs.sound(this@SetupActivity)
            setOnCheckedChangeListener { _, v ->
                Prefs.of(this@SetupActivity).edit().putBoolean(Prefs.KEY_SOUND, v).apply()
            }
        }

        findViewById<SeekBar>(R.id.seekHeight).apply {
            // SeekBar 0..32 maps to 40..72 dp.
            progress = Prefs.keyHeightDp(this@SetupActivity) - 40
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    Prefs.of(this@SetupActivity).edit().putInt(Prefs.KEY_HEIGHT, 40 + p).apply()
                }

                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val status = findViewById<TextView>(R.id.status)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.id.contains("HackerBoardService") }
        val active = Settings.Secure.getString(
            contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD,
        )?.contains("HackerBoardService") == true

        when {
            active -> {
                status.text = getString(R.string.status_active)
                status.setTextColor(getColor(R.color.accent))
            }
            enabled -> {
                status.text = getString(R.string.status_enabled)
                status.setTextColor(getColor(R.color.text))
            }
            else -> {
                status.text = getString(R.string.status_disabled)
                status.setTextColor(getColor(R.color.text_dim))
            }
        }
    }

    private fun setupAccentSwatches() {
        val row = findViewById<LinearLayout>(R.id.accentRow)
        val size = dp(46)
        Themes.list.forEachIndexed { index, _ ->
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(0, 0, dp(10), 0)
            }
            v.setOnClickListener {
                Prefs.of(this).edit().putInt(Prefs.KEY_THEME, index).apply()
                paintSwatches()
            }
            row.addView(v)
            swatches.add(v)
        }
        paintSwatches()
    }

    private fun paintSwatches() {
        val selected = Prefs.themeIndex(this)
        swatches.forEachIndexed { index, v ->
            val color = Themes.list[index].accent
            v.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(color)
                if (index == selected) {
                    setStroke(dp(3), Color.WHITE)
                } else {
                    setStroke(dp(1), getColor(R.color.border))
                }
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
