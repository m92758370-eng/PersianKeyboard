package com.customkeyboard.app

import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MyInputMethodService : InputMethodService(), CustomKeyboardView.Listener {

    private lateinit var rootContainer: LinearLayout
    private lateinit var controlRow: LinearLayout
    private lateinit var suggestionBar: LinearLayout
    private lateinit var suggestionButtons: List<TextView>
    private lateinit var keyboardOuter: FrameLayout
    private lateinit var keyboardCard: FrameLayout
    private lateinit var keyboardView: CustomKeyboardView
    private lateinit var leftHandle: View
    private lateinit var rightHandle: View
    private lateinit var topHandle: View
    private lateinit var bottomHandle: View
    private var resizeModeOn = false

    private val handler = Handler(Looper.getMainLooper())

    private var autoTypeRunning = false
    private var autoTypeIndex = 0
    private var autoTypeChars: List<String> = emptyList()

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    // همون فرمولِ ارتفاعِ CustomKeyboardView (230dp کلیدها + 42dp نوار بالا)، ضرب در مقیاسِ ذخیره‌شده
    private fun keyboardHeightPx(scale: Float): Int = (272f * resources.displayMetrics.density * scale).toInt()

    override fun onCreateInputView(): View {
        keyboardView = CustomKeyboardView(this)
        keyboardView.listener = this

        keyboardCard = FrameLayout(this)
        keyboardCard.addView(
            keyboardView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        leftHandle = buildSideHandle()
        rightHandle = buildSideHandle()
        topHandle = buildBottomHandle()
        bottomHandle = buildBottomHandle()
        keyboardCard.addView(leftHandle, FrameLayout.LayoutParams(dp(14f), ViewGroup.LayoutParams.MATCH_PARENT).also {
            it.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        })
        keyboardCard.addView(rightHandle, FrameLayout.LayoutParams(dp(14f), ViewGroup.LayoutParams.MATCH_PARENT).also {
            it.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        })
        keyboardCard.addView(topHandle, FrameLayout.LayoutParams(dp(64f), dp(16f)).also {
            it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        })
        keyboardCard.addView(bottomHandle, FrameLayout.LayoutParams(dp(64f), dp(16f)).also {
            it.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        })

        keyboardOuter = FrameLayout(this)
        keyboardOuter.addView(keyboardCard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        controlRow = buildControlRow()
        controlRow.visibility = View.GONE

        suggestionBar = buildSuggestionBar()

        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        rootContainer.addView(controlRow)
        rootContainer.addView(suggestionBar)
        rootContainer.addView(keyboardOuter)

        setResizeHandlesVisible(false)
        applyKeyboardSizing()

        RemoteStatusHelper.refreshStatusAsync(this)
        return rootContainer
    }

    // ---------- ساخت نوار کنترل بالا (تمام / جابجایی / بازنشانی)، دقیقاً شبیه حالت کیبورد کوچیک شیائومی ----------
    private fun buildControlRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#151515"))
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
        }

        val doneBtn = TextView(this).apply {
            text = "تمام"
            setTextColor(Color.parseColor("#4A90E2"))
            textSize = 15f
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            setOnClickListener { exitResizeMode() }
        }

        val moveHandle = TextView(this).apply {
            text = "✥"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(dp(16f), dp(6f), dp(16f), dp(6f))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
        }
        setupMoveHandleTouch(moveHandle)

        val resetBtn = TextView(this).apply {
            text = "بازنشانی"
            setTextColor(Color.parseColor("#4A90E2"))
            textSize = 15f
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            setOnClickListener {
                PrefsHelper.resetKeyboardSizing(this@MyInputMethodService)
                applyKeyboardSizing()
                Toast.makeText(this@MyInputMethodService, "سایز کیبورد به حالت پیش‌فرض برگشت", Toast.LENGTH_SHORT).show()
            }
        }

        row.addView(doneBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(moveHandle)
        row.addView(resetBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    // ---------- نوار کلماتِ پیشنهادی (خودآموز، بدون هیچ لیستِ از پیش آماده‌ای) ----------
    private fun buildSuggestionBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1C1C1E"))
            visibility = View.GONE
        }
        val buttons = (0 until 3).map { index ->
            TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(dp(6f), dp(11f), dp(6f), dp(11f))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                visibility = View.GONE
            }
        }
        buttons.forEachIndexed { index, tv ->
            bar.addView(tv)
            if (index < buttons.size - 1) {
                val divider = View(this).apply { setBackgroundColor(Color.parseColor("#3A3A3C")) }
                bar.addView(divider, LinearLayout.LayoutParams(dp(1f), ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
        suggestionButtons = buttons
        return bar
    }

    // نویسه‌های فارسی/عربیِ بلافاصله قبل از مکان‌نما رو به‌عنوان «کلمه‌ی در حالِ تایپ» برمی‌گردونه
    private fun currentWordPrefix(): String {
        val before = currentInputConnection?.getTextBeforeCursor(30, 0)?.toString() ?: return ""
        val match = Regex("[\\u0600-\\u06FF]+$").find(before)
        return match?.value ?: ""
    }

    private fun updateSuggestions() {
        if (!::suggestionBar.isInitialized) return
        val prefix = currentWordPrefix()
        val suggestions = if (prefix.isNotEmpty()) WordDictionary.getSuggestions(this, prefix, 3) else emptyList()
        var anyVisible = false
        suggestionButtons.forEachIndexed { index, tv ->
            val word = suggestions.getOrNull(index)
            if (word != null) {
                tv.text = word
                tv.visibility = View.VISIBLE
                tv.setOnClickListener { applySuggestion(prefix, word) }
                anyVisible = true
            } else {
                tv.visibility = View.GONE
            }
        }
        suggestionBar.visibility = if (anyVisible) View.VISIBLE else View.GONE
    }

    private fun applySuggestion(prefix: String, word: String) {
        currentInputConnection?.deleteSurroundingText(prefix.length, 0)
        currentInputConnection?.commitText("$word ", 1)
        WordDictionary.recordTyped(this, word)
        updateSuggestions()
    }

    // وقتی کاربر با فاصله/اینتر یه کلمه رو تموم می‌کنه، صداش کن تا شمارنده‌ش زیاد بشه
    private fun learnCurrentWord() {
        val prefix = currentWordPrefix()
        if (prefix.isNotEmpty()) {
            WordDictionary.recordTyped(this, prefix)
        }
    }

    private fun buildSideHandle(): View {
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#AA4A90E2"))
            visibility = View.GONE
        }
    }

    private fun buildBottomHandle(): View {
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#AA4A90E2"))
            visibility = View.GONE
        }
    }

    private fun setResizeHandlesVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        leftHandle.visibility = v
        rightHandle.visibility = v
        topHandle.visibility = v
        bottomHandle.visibility = v
    }

    // ---------- اعمالِ سایز/موقعیتِ ذخیره‌شده روی قابِ کیبورد ----------
    private fun applyKeyboardSizing() {
        val dm = resources.displayMetrics
        val widthScale = PrefsHelper.getKeyboardWidthScale(this).coerceIn(0.4f, 1f)
        val leftFraction = PrefsHelper.getKeyboardLeftMarginFraction(this).coerceIn(0f, 1f)
        val heightScale = PrefsHelper.getKeyboardHeightScale(this)
        val cardWidth = (dm.widthPixels * widthScale).toInt().coerceAtLeast(dp(160f))
        val slack = (dm.widthPixels - cardWidth).coerceAtLeast(0)
        val leftMargin = (slack * leftFraction).toInt()

        // ارتفاعِ دقیق (نه wrap_content) که اندازه‌ی دستگیره‌های MATCH_PARENT رو هم مشخص نگه می‌داره؛
        // wrap_content این‌جا باعث می‌شد کل قاب اشتباهی خیلی بزرگ اندازه‌گیری بشه
        val lp = FrameLayout.LayoutParams(cardWidth, keyboardHeightPx(heightScale))
        lp.leftMargin = leftMargin
        keyboardCard.layoutParams = lp
        keyboardCard.requestLayout()
        keyboardView.requestLayout()
    }

    // ---------- حالت تغییرِ سایزِ کلِ کیبورد (با زدنِ آیکون ▦ تو نوار بالای کیبورد باز/بسته می‌شه) ----------
    override fun onToggleResizeMode() {
        if (resizeModeOn) exitResizeMode() else enterResizeMode()
    }

    private fun enterResizeMode() {
        resizeModeOn = true
        controlRow.visibility = View.VISIBLE
        setResizeHandlesVisible(true)
    }

    private fun exitResizeMode() {
        resizeModeOn = false
        controlRow.visibility = View.GONE
        setResizeHandlesVisible(false)
    }

    // ---------- کشیدنِ دستگیره‌ی وسطِ نوار کنترل برای جابجایی کل کیبورد به چپ/راست ----------
    private fun setupMoveHandleTouch(handle: View) {
        var startRawX = 0f
        var startLeftMargin = 0
        var cardWidth = 0

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startLeftMargin = (keyboardCard.layoutParams as FrameLayout.LayoutParams).leftMargin
                    cardWidth = keyboardCard.width.takeIf { it > 0 } ?: keyboardCard.layoutParams.width
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startRawX).toInt()
                    val screenWidth = resources.displayMetrics.widthPixels
                    val maxMargin = (screenWidth - cardWidth).coerceAtLeast(0)
                    val newMargin = (startLeftMargin + dx).coerceIn(0, maxMargin)
                    val lp = keyboardCard.layoutParams as FrameLayout.LayoutParams
                    lp.leftMargin = newMargin
                    keyboardCard.layoutParams = lp
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    persistCurrentCardBounds()
                    true
                }
                else -> false
            }
        }
    }

    // ---------- کشیدنِ دستگیره‌های کناری (تغییر عرض، شبیه کراپ‌کردن از هر طرف) ----------
    private fun setupSideHandleTouch(handle: View, isLeftSide: Boolean) {
        var startRawX = 0f
        var startWidth = 0
        var startLeftMargin = 0

        handle.setOnTouchListener { _, event ->
            val screenWidth = resources.displayMetrics.widthPixels
            val minWidth = (screenWidth * 0.4f).toInt()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startWidth = keyboardCard.width.takeIf { it > 0 } ?: keyboardCard.layoutParams.width
                    startLeftMargin = (keyboardCard.layoutParams as FrameLayout.LayoutParams).leftMargin
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startRawX).toInt()
                    val lp = keyboardCard.layoutParams as FrameLayout.LayoutParams
                    if (isLeftSide) {
                        // لبه‌ی راست ثابت می‌مونه؛ کشیدنِ دستگیره‌ی چپ عرض و حاشیه‌ی چپ رو با هم عوض می‌کنه
                        val rightEdge = startLeftMargin + startWidth
                        val newWidth = (startWidth - dx).coerceIn(minWidth, screenWidth)
                        val newMargin = (rightEdge - newWidth).coerceIn(0, screenWidth - newWidth)
                        lp.width = newWidth
                        lp.leftMargin = newMargin
                    } else {
                        // لبه‌ی چپ ثابت می‌مونه؛ کشیدنِ دستگیره‌ی راست فقط عرض رو عوض می‌کنه
                        val maxWidth = screenWidth - startLeftMargin
                        val newWidth = (startWidth + dx).coerceIn(minWidth, maxWidth)
                        lp.width = newWidth
                    }
                    keyboardCard.layoutParams = lp
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    persistCurrentCardBounds()
                    true
                }
                else -> false
            }
        }
    }

    // ---------- کشیدنِ دستگیره‌ی پایین (تغییر ارتفاع کل کیبورد از پایین) ----------
    private fun setupBottomHandleTouch(handle: View) {
        var startRawY = 0f
        var startScale = 1f

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawY = event.rawY
                    startScale = PrefsHelper.getKeyboardHeightScale(this)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startRawY
                    val refHeight = dp(230f).toFloat()
                    val newScale = (startScale + dy / refHeight).coerceIn(0.6f, 1.6f)
                    applyHeightScale(newScale)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    // ---------- کشیدنِ دستگیره‌ی بالا (تغییر ارتفاع کل کیبورد از بالا) ----------
    private fun setupTopHandleTouch(handle: View) {
        var startRawY = 0f
        var startScale = 1f

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawY = event.rawY
                    startScale = PrefsHelper.getKeyboardHeightScale(this)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startRawY
                    val refHeight = dp(230f).toFloat()
                    // کشیدنِ دستگیره‌ی بالا به سمتِ بالا (dy منفی) باید کیبورد رو بلندتر کنه
                    val newScale = (startScale - dy / refHeight).coerceIn(0.6f, 1.6f)
                    applyHeightScale(newScale)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun applyHeightScale(newScale: Float) {
        PrefsHelper.setKeyboardHeightScale(this, newScale)
        val lp = keyboardCard.layoutParams as FrameLayout.LayoutParams
        lp.height = keyboardHeightPx(newScale)
        keyboardCard.layoutParams = lp
        keyboardView.requestLayout()
    }

    private fun persistCurrentCardBounds() {
        val dm = resources.displayMetrics
        val lp = keyboardCard.layoutParams as FrameLayout.LayoutParams
        val widthScale = (lp.width.toFloat() / dm.widthPixels).coerceIn(0.4f, 1f)
        val slack = (dm.widthPixels - lp.width).coerceAtLeast(0)
        val leftFraction = if (slack > 0) (lp.leftMargin.toFloat() / slack).coerceIn(0f, 1f) else 0.5f
        PrefsHelper.setKeyboardWidthScale(this, widthScale)
        PrefsHelper.setKeyboardLeftMarginFraction(this, leftFraction)
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.refreshBackground()
        setupSideHandleTouch(leftHandle, isLeftSide = true)
        setupSideHandleTouch(rightHandle, isLeftSide = false)
        setupTopHandleTouch(topHandle)
        setupBottomHandleTouch(bottomHandle)
        updateSuggestions()
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
        updateSuggestions()
    }

    override fun onBackspace() {
        if (!isAllowed()) return
        currentInputConnection?.deleteSurroundingText(1, 0)
        updateSuggestions()
    }

    override fun onEnter() {
        if (!isAllowed()) return
        learnCurrentWord()
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        updateSuggestions()
    }

    override fun onSpace() {
        if (!isAllowed()) return
        learnCurrentWord()
        currentInputConnection?.commitText(" ", 1)
        updateSuggestions()
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
            return
        }
        if (autoTypeChars.isEmpty()) {
            val text = PrefsHelper.getAutoTypeText(this)
            if (text.isNotEmpty()) {
                autoTypeChars = text.map { it.toString() }
                autoTypeIndex = PrefsHelper.getAutoTypeProgress(this).coerceIn(0, autoTypeChars.size)
            }
        }
        if (autoTypeChars.isNotEmpty() && autoTypeIndex < autoTypeChars.size) {
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

    override fun onSettingsButton() {
        if (!isAllowed()) return
        val intent = Intent(this, SettingsActivity::class.java)
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
        PrefsHelper.setAutoTypeProgress(this, 0)
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
            PrefsHelper.setAutoTypeProgress(this, 0)
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
            PrefsHelper.setAutoTypeProgress(this, autoTypeIndex)
            scheduleNextChar()
        }, delay)
    }

    private fun pauseAutoType() {
        autoTypeRunning = false
        handler.removeCallbacksAndMessages(null)
    }
}
