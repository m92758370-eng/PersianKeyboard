package com.customkeyboard.app

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Toast

class MyInputMethodService : InputMethodService(), CustomKeyboardView.Listener {

    private lateinit var keyboardView: CustomKeyboardView
    private val handler = Handler(Looper.getMainLooper())

    private var autoTypeRunning = false
    private var autoTypeIndex = 0
    private var autoTypeChars: List<String> = emptyList()

    override fun onCreateInputView(): View {
        keyboardView = CustomKeyboardView(this)
        keyboardView.listener = this
        return keyboardView
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopAutoType()
    }

    override fun onCommitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun onBackspace() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    override fun onEnter() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    override fun onSpace() {
        currentInputConnection?.commitText(" ", 1)
    }

    override fun onAutoTypeButton() {
        if (autoTypeRunning) {
            stopAutoType()
            Toast.makeText(this, "تایپ خودکار متوقف شد", Toast.LENGTH_SHORT).show()
        } else {
            startAutoType()
        }
    }

    private fun startAutoType() {
        val text = PrefsHelper.getAutoTypeText(this)
        if (text.isEmpty()) {
            Toast.makeText(this, "اول از تنظیمات یه متن برای تایپ خودکار ذخیره کن", Toast.LENGTH_LONG).show()
            return
        }
        autoTypeChars = text.map { it.toString() }
        autoTypeIndex = 0
        autoTypeRunning = true
        Toast.makeText(this, "تایپ خودکار شروع شد", Toast.LENGTH_SHORT).show()
        scheduleNextChar()
    }

    private fun scheduleNextChar() {
        if (!autoTypeRunning) return
        if (autoTypeIndex >= autoTypeChars.size) {
            autoTypeRunning = false
            Toast.makeText(this, "تایپ خودکار تمام شد", Toast.LENGTH_SHORT).show()
            return
        }
        val delay = PrefsHelper.getAutoTypeDelayMs(this)
        handler.postDelayed({
            if (!autoTypeRunning) return@postDelayed
            val ch = autoTypeChars[autoTypeIndex]
            currentInputConnection?.commitText(ch, 1)
            if (ch.isNotBlank()) {
                keyboardView.highlightKey(ch)
            }
            autoTypeIndex++
            scheduleNextChar()
        }, delay)
    }

    private fun stopAutoType() {
        autoTypeRunning = false
        handler.removeCallbacksAndMessages(null)
    }
}
