package com.customkeyboard.app

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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
        RemoteStatusHelper.refreshStatusAsync(this)
        return keyboardView
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.refreshBackground()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        pauseAutoType()
    }

    private fun isAllowed(): Boolean {
        return RemoteStatusHelper.isEnabledCached(this)
    }

    override fun onCommitText(text: String) {
        if (!isAllowed()) return
        currentInputConnection?.commitText(text, 1)
    }

    override fun onBackspace() {
        if (!isAllowed()) return
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    override fun onEnter() {
        if (!isAllowed()) return
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    override fun onSpace() {
        if (!isAllowed()) return
        currentInputConnection?.commitText(" ", 1)
    }

    override fun onSpaceLongPress() {
        if (!isAllowed()) return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    override fun onAutoTypeButton() {
        if (!isAllowed()) return
        if (autoTypeRunning) {
            pauseAutoType()
            Toast.makeText(this, "تایپ خودکار متوقف شد", Toast.LENGTH_SHORT).show()
        } else {
            startAutoType()
        }
    }

    override fun onPauseResumeButton() {
        if (!isAllowed()) return
        if (autoTypeRunning) {
            pauseAutoType()
            Toast.makeText(this, "تایپ خودکار متوقف شد", Toast.LENGTH_SHORT).show()
        } else if (autoTypeChars.isNotEmpty() && autoTypeIndex < autoTypeChars.size) {
            resumeAutoType()
            Toast.makeText(this, "تایپ خودکار ادامه یافت", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "چیزی برای ادامه دادن نیست", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onWordShuffleButton() {
        if (!isAllowed()) return
        val intent = Intent(this, WordShuffleActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
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

    private fun resumeAutoType() {
        autoTypeRunning = true
        scheduleNextChar()
    }

    private fun scheduleNextChar() {
        if (!autoTypeRunning) return
        if (!isAllowed()) {
            pauseAutoType()
            return
        }
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

    private fun pauseAutoType() {
        autoTypeRunning = false
        handler.removeCallbacksAndMessages(null)
    }
}
