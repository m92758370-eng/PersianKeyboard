package com.customkeyboard.app

import android.app.AlertDialog
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
    private lateinit var txtBackgroundStatus: TextView
    private lateinit var txtThemeStatus: TextView

    private val backgroundPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                }
                PrefsHelper.setBackgroundImageUri(this, uri.toString())
                updateBackgroundStatus()
                Toast.makeText(this, "پس‌زمینه ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (RemoteStatusHelper.blockIfDisabled(this)) return

        findViewById<Button>(R.id.btnEnableKeyboard).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        findViewById<Button>(R.id.btnSwitchKeyboard).setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        findViewById<Button>(R.id.btnShowDeviceId).setOnClickListener {
            showDeviceIdDialog()
        }

        findViewById<Button>(R.id.btnWordShuffle).setOnClickListener {
            startActivity(Intent(this, WordShuffleActivity::class.java))
        }

        findViewById<Button>(R.id.btnTextReplace).setOnClickListener {
            startActivity(Intent(this, TextReplaceActivity::class.java))
        }

        findViewById<Button>(R.id.btnAiWriter).setOnClickListener {
            startActivity(Intent(this, AiWriterActivity::class.java))
        }

        findViewById<Button>(R.id.btnKeyLayout).setOnClickListener {
            startActivity(Intent(this, KeyLayoutActivity::class.java))
        }

        buildLetterRows()

        txtBackgroundStatus = findViewById(R.id.txtBackgroundStatus)
        updateBackgroundStatus()

        findViewById<Button>(R.id.btnPickBackground).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            backgroundPickerLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btnResetBackground).setOnClickListener {
            PrefsHelper.setBackgroundImageUri(this, null)
            updateBackgroundStatus()
            Toast.makeText(this, "پس‌زمینه پیش‌فرض برگشت", Toast.LENGTH_SHORT).show()
        }

        txtThemeStatus = findViewById(R.id.txtThemeStatus)
        updateThemeStatus()
        findViewById<Button>(R.id.btnToggleTheme).setOnClickListener {
            val newDark = !PrefsHelper.isDarkMode(this)
            PrefsHelper.setDarkMode(this, newDark)
            updateThemeStatus()
            Toast.makeText(this, "دفعه‌ی بعد که کیبورد رو باز کنی اعمال می‌شه", Toast.LENGTH_SHORT).show()
        }

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
            PrefsHelper.setAutoTypeProgress(this, 0)
            val delay = PrefsHelper.MIN_DELAY_MS + speedSeekBar.progress
            PrefsHelper.setAutoTypeDelayMs(this, delay)
            Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBackgroundStatus() {
        val uri = PrefsHelper.getBackgroundImageUri(this)
        txtBackgroundStatus.text = if (uri != null) {
            "یه عکس دلخواه انتخاب شده ✓"
        } else {
            "الان پیش‌فرضه (بدون عکس دلخواه)"
        }
    }

    private fun updateThemeStatus() {
        val dark = PrefsHelper.isDarkMode(this)
        txtThemeStatus.text = if (dark) "حالت فعلی: شب" else "حالت فعلی: روز"
        findViewById<Button>(R.id.btnToggleTheme).text = if (dark) "تغییر به حالت روز" else "تغییر به حالت شب"
    }

    private fun showDeviceIdDialog() {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        AlertDialog.Builder(this)
            .setTitle("شناسه این گوشی")
            .setMessage(deviceId)
            .setPositiveButton("کپی کن") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("device id", deviceId))
                Toast.makeText(this, "کپی شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("بستن", null)
            .show()
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
            val btnShowSaved = row.findViewById<Button>(R.id.btnShowSavedTexts)
            label.text = letter
            editText.setText(PrefsHelper.getReplacement(this, letter))
            editText.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val text = editText.text.toString()
                    PrefsHelper.setReplacement(this, letter, text)
                    if (text.isNotBlank()) {
                        PrefsHelper.addSavedText(this, text)
                    }
                }
            }
            btnShowSaved.setOnClickListener {
                showSavedTextsDialog(editText, letter)
            }
            container.addView(row)
        }
    }

    private fun showSavedTextsDialog(editText: EditText, letter: String) {
        val savedTexts = PrefsHelper.getSavedTexts(this)
        if (savedTexts.isEmpty()) {
            Toast.makeText(this, "هنوز متنی ذخیره نشده", Toast.LENGTH_SHORT).show()
            return
        }
        val items = savedTexts.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("انتخاب متن ذخیره‌شده")
            .setItems(items) { _, which ->
                val chosen = items[which]
                editText.setText(chosen)
                PrefsHelper.setReplacement(this, letter, chosen)
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        val container = findViewById<LinearLayout>(R.id.lettersContainer)
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i)
            val label = row.findViewById<TextView>(R.id.letterLabel)?.text?.toString() ?: continue
            val editText = row.findViewById<EditText>(R.id.replacementEditText) ?: continue
            val text = editText.text.toString()
            PrefsHelper.setReplacement(this, label, text)
            if (text.isNotBlank()) {
                PrefsHelper.addSavedText(this, text)
            }
        }
        if (::autoTypeEditText.isInitialized) {
            PrefsHelper.setAutoTypeText(this, autoTypeEditText.text.toString())
        }
    }
}
