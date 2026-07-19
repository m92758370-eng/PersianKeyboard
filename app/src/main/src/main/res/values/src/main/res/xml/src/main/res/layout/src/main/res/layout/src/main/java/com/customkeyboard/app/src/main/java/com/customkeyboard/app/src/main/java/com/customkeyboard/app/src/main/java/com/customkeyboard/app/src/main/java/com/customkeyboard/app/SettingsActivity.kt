package com.customkeyboard.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var speedLabel: TextView
    private lateinit var speedSeekBar: SeekBar
    private lateinit var autoTypeEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<Button>(R.id.btnEnableKeyboard).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        findViewById<Button>(R.id.btnSwitchKeyboard).setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        buildLetterRows()

        autoTypeEditText = findViewById(R.id.autoTypeEditText)
        autoTypeEditText.setText(PrefsHelper.getAutoTypeText(this))

        speedLabel = findViewById(R.id.speedLabel)
        speedSeekBar = findViewById(R.id.speedSeekBar)
        val currentDelay = PrefsHelper.getAutoTypeDelayMs(this)
        speedSeekBar.progress = (currentDelay - PrefsHelper.MIN_DELAY_MS).toInt()
        updateSpeedLabel(currentDelay)
        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateSpeedLabel(PrefsHelper.MIN_DELAY_MS + progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        findViewById<Button>(R.id.btnSaveAutoType).setOnClickListener {
            PrefsHelper.setAutoTypeText(this, autoTypeEditText.text.toString())
            val delay = PrefsHelper.MIN_DELAY_MS + speedSeekBar.progress
            PrefsHelper.setAutoTypeDelayMs(this, delay)
            Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSpeedLabel(delayMs: Long) {
        val charsPerSec = 1000.0 / delayMs
        speedLabel.text = "سرعت تایپ: هر حرف %dms (%.1f حرف در ثانیه)".format(delayMs, charsPerSec)
    }

    private fun buildLetterRows() {
        val container = findViewById<LinearLayout>(R.id.lettersContainer)
        val inflater = LayoutInflater.from(this)
        for (letter in KeyboardLayouts.allLetters()) {
            val row = inflater.inflate(R.layout.item_letter_mapping, container, false)
            val label = row.findViewById<TextView>(R.id.letterLabel)
            val editText = row.findViewById<EditText>(R.id.replacementEditText)
            label.text = letter
            editText.setText(PrefsHelper.getReplacement(this, letter))
            editText.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    PrefsHelper.setReplacement(this, letter, editText.text.toString())
                }
            }
            container.addView(row)
        }
    }

    override fun onPause() {
        super.onPause()
        val container = findViewById<LinearLayout>(R.id.lettersContainer)
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i)
            val label = row.findViewById<TextView>(R.id.letterLabel)?.text?.toString() ?: continue
            val editText = row.findViewById<EditText>(R.id.replacementEditText) ?: continue
            PrefsHelper.setReplacement(this, label, editText.text.toString())
        }
        if (::autoTypeEditText.isInitialized) {
            PrefsHelper.setAutoTypeText(this, autoTypeEditText.text.toString())
        }
    }
}
