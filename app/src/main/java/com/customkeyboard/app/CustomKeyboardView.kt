package com.customkeyboard.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

class CustomKeyboardView(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    interface Listener {
        fun onCommitText(text: String)
        fun onBackspace()
        fun onEnter()
        fun onSpace()
        fun onSpaceLongPress()
        fun onAutoTypeButton()
        fun onPauseResumeButton()
        fun onWordShuffleButton()
        fun onSettingsButton()
        fun onToggleResizeMode()
        fun onSuggestionTap(word: String)
    }

    var listener: Listener? = null

    private enum class KeyType { LETTER, SYMBOL, SPACE, BACKSPACE, ENTER, LANG_SWITCH, AUTOTYPE, PAUSE_RESUME, WORD_SHUFFLE, SYMBOLS_TOGGLE, ZWNJ, SHIFT, TOOLBAR_MIC, TOOLBAR_TRANSLATE, TOOLBAR_SETTINGS, TOOLBAR_EMOJI, TOOLBAR_CLIPBOARD, TOOLBAR_GRID, SUGGESTION }
    private enum class KeyboardMode { LETTERS, SYMBOLS, NUMBERS, EMOJI }

    private data class KeyRect(
        val label: String,
        val rect: RectF,
        val type: KeyType,
        val subLabel: String = "",
        val hasReplacement: Boolean = false
    )

    companion object {
        private const val SPACE_LONG_PRESS_MS = 2000L
        private const val BACKSPACE_INITIAL_DELAY_MS = 280L
        private const val LETTER_LONG_PRESS_MS = 350L
        private const val TOOLBAR_HEIGHT_DP = 42f
    }

    private var usePersian = true
    private val keys = mutableListOf<KeyRect>()
    private var mode = KeyboardMode.LETTERS
    // کلماتِ پیشنهادیِ فعلی؛ وقتی خالی نیست، همین ۴۲dp نوار بالا به‌جای ۶ آیکونِ عادی، این‌ها رو نشون می‌ده
    private var currentSuggestions: List<String> = emptyList()

    /** از سرویسِ کیبورد صدا زده می‌شه تا نوار بالا بینِ حالتِ عادی و حالتِ پیشنهادها سوییچ کنه. */
    fun setSuggestions(suggestions: List<String>) {
        currentSuggestions = suggestions
        if (width > 0 && height > 0) {
            rebuildKeys(width, height)
            invalidate()
        }
    }

    private val keyPaint = Paint().apply {
        color = Color.parseColor("#992A2A2A")
        isAntiAlias = true
    }
    private val specialKeyPaint = Paint().apply {
        color = Color.parseColor("#991A1A1A")
        isAntiAlias = true
    }
    private val accentPaint = Paint().apply {
        color = Color.parseColor("#4A90E2")
        isAntiAlias = true
    }
    private val enterAccentPaint = Paint().apply {
        color = Color.parseColor("#3B4A5E") // آبی مات و تیره (slate)، نه آبی روشن
        isAntiAlias = true
    }
    private val highlightPaint = Paint().apply {
        color = Color.parseColor("#E23B3B")
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint().apply {
        color = Color.parseColor("#BBBBBB")
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 24f
    }
    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#66000000")
    }

    private var rowHeight = 0f
    private val density = context.resources.displayMetrics.density
    private var backgroundBitmap: Bitmap? = null
    private var backgroundW = -1
    private var backgroundH = -1
    private var fallbackBgColor = Color.parseColor("#121212")
    private var keyPadDp = 3f
    private var keyCornerDp = 6f

    private val handler = Handler(Looper.getMainLooper())
    private var highlightedLabel: String? = null

    private var spacePressed = false
    private var spacePointerId = -1
    private var spaceLongPressTriggered = false
    private val spaceLongPressRunnable = Runnable {
        spaceLongPressTriggered = true
        listener?.onSpaceLongPress()
    }

    private var backspacePressed = false
    private var backspacePointerId = -1
    private var backspaceRepeatCount = 0
    private val backspaceRunnable = object : Runnable {
        override fun run() {
            if (!backspacePressed) return
            listener?.onBackspace()
            backspaceRepeatCount++
            val nextDelay = when {
                backspaceRepeatCount < 6 -> 55L
                backspaceRepeatCount < 12 -> 35L
                backspaceRepeatCount < 20 -> 20L
                else -> 12L
            }
            handler.postDelayed(this, nextDelay)
        }
    }

    private var letterPressed = false
    private var letterPointerId = -1
    private var letterPressedKey: KeyRect? = null
    private var letterLongPressTriggered = false
    private val letterLongPressRunnable = Runnable {
        val key = letterPressedKey
        val alt = key?.let { longPressAltFor(it.label) }
        if (key != null && alt != null) {
            letterLongPressTriggered = true
            flashKey(key.label)
            listener?.onCommitText(alt)
        }
    }

    // کاراکترِ جایگزینِ فشارِ طولانی برای یه حرف؛ فقط تو حالتِ فارسی + صفحه‌ی حروف فعاله
    private fun longPressAltFor(label: String): String? {
        if (!usePersian || mode != KeyboardMode.LETTERS) return null
        return KeyboardLayouts.PERSIAN_LONG_PRESS[label]
    }

    // شیفت (فقط تو صفحه‌کلیدِ انگلیسی): یه ضربه = بزرگ‌شدنِ یه‌باره‌ی حرفِ بعدی، دو ضربه‌ی پشتِ‌هم = قفلِ حروفِ بزرگ
    private var isShiftOn = false
    private var isCapsLock = false
    private var shiftPressed = false
    private var shiftPointerId = -1
    private var shiftDoubleTapCandidate = false
    private var lastShiftUpTime = 0L

    private var langPressed = false
    private var langPointerId = -1
    private var lastLangUpTime = 0L
    private var langDoubleTapCandidate = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightScale = PrefsHelper.getKeyboardHeightScale(context)
        val heightPx = ((230 + TOOLBAR_HEIGHT_DP) * context.resources.displayMetrics.density * heightScale).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, heightPx)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyThemeColors()
        rebuildKeys(w, h)
        loadBackground(w, h)
    }

    private fun loadBackground(w: Int, h: Int) {
        if (w == 0 || h == 0) return
        if (backgroundBitmap != null && backgroundW == w && backgroundH == h) return
        backgroundW = w
        backgroundH = h

        val customUriString = PrefsHelper.getBackgroundImageUri(context)
        if (customUriString != null) {
            try {
                val uri = Uri.parse(customUriString)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val original = BitmapFactory.decodeStream(stream)
                    if (original != null) {
                        backgroundBitmap = centerCrop(original, w, h)
                        return
                    }
                }
            } catch (e: Exception) {
            }
        }

        if (PrefsHelper.isDarkMode(context)) {
            backgroundBitmap = null
            return
        }

        val resId = resources.getIdentifier("keyboard_background", "drawable", context.packageName)
        backgroundBitmap = if (resId != 0) {
            val original = BitmapFactory.decodeResource(resources, resId)
            centerCrop(original, w, h)
        } else {
            null
        }
    }

    fun refreshBackground() {
        applyThemeColors()
        backgroundBitmap = null
        backgroundW = -1
        backgroundH = -1
        loadBackground(width, height)
        invalidate()
    }

    private fun applyThemeColors() {
        if (PrefsHelper.isDarkMode(context)) {
            keyPaint.color = Color.parseColor("#662C2C2E")
            specialKeyPaint.color = Color.parseColor("#66232324")
            accentPaint.color = Color.parseColor("#663A3A3C")
            textPaint.color = Color.WHITE
            labelPaint.color = Color.parseColor("#8E8E93")
            overlayPaint.color = Color.parseColor("#00000000")
            fallbackBgColor = Color.parseColor("#000000")
            arrowIconPaint.color = Color.WHITE
            smileyStrokePaint.color = Color.WHITE
            smileyDotPaint.color = Color.WHITE
        } else {
            keyPaint.color = Color.parseColor("#99FFFFFF")
            specialKeyPaint.color = Color.parseColor("#99DDDDDD")
            accentPaint.color = Color.parseColor("#4A90E2")
            textPaint.color = Color.BLACK
            labelPaint.color = Color.parseColor("#555555")
            overlayPaint.color = Color.parseColor("#11000000")
            fallbackBgColor = Color.parseColor("#F0F0F0")
            arrowIconPaint.color = Color.BLACK
            smileyStrokePaint.color = Color.BLACK
            smileyDotPaint.color = Color.BLACK
        }
    }

    private fun centerCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcRatio = src.width.toFloat() / src.height
        val targetRatio = targetW.toFloat() / targetH
        val cropW: Int
        val cropH: Int
        if (srcRatio > targetRatio) {
            cropH = src.height
            cropW = (cropH * targetRatio).toInt().coerceAtMost(src.width)
        } else {
            cropW = src.width
            cropH = (cropW / targetRatio).toInt().coerceAtMost(src.height)
        }
        val x = (src.width - cropW) / 2
        val y = (src.height - cropH) / 2
        val cropped = Bitmap.createBitmap(src, x, y, cropW, cropH)
        return Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
    }

    private fun generateGrungeTexture(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val gradient = Paint().apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                Color.parseColor("#0D0D0D"), Color.parseColor("#1F1F1F"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gradient)

        val rnd = Random(42)
        val scratchPaint = Paint().apply {
            isAntiAlias = true
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        repeat(60) {
            val red = it % 9 == 0
            scratchPaint.color = if (red) {
                Color.argb(rnd.nextInt(60, 140), 200, 40, 40)
            } else {
                Color.argb(rnd.nextInt(15, 50), 255, 255, 255)
            }
            val x1 = rnd.nextFloat() * w
            val y1 = rnd.nextFloat() * h
            val len = rnd.nextFloat() * h * 0.5f + 20f
            val angle = rnd.nextFloat() * 40f - 20f + 60f
            val rad = Math.toRadians(angle.toDouble())
            val x2 = (x1 + len * Math.cos(rad)).toFloat()
            val y2 = (y1 + len * Math.sin(rad)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, scratchPaint)
        }
        return bmp
    }

    fun setLanguage(persian: Boolean) {
        usePersian = persian
        isShiftOn = false
        isCapsLock = false
        rebuildKeys(width, height)
        invalidate()
    }

    fun isPersian(): Boolean = usePersian

    fun updateSpaceLabel() {
        rebuildKeys(width, height)
        invalidate()
    }

    private fun getPersianLetterRows(): List<List<String>> {
        val custom = PrefsHelper.getCustomPersianOrder(context) ?: return KeyboardLayouts.PERSIAN
        return KeyboardLayouts.chunkToRows(custom, KeyboardLayouts.persianRowSizes())
    }

    // شناسه‌ی ثابتِ هر کلیدِ نوار بالا، هم‌ترتیب با حلقه‌ی زیر و با KeyboardEditorActivity
    private val toolbarKeyIds = listOf(
        "toolbar_mic", "toolbar_translate", "toolbar_settings",
        "toolbar_emoji", "toolbar_clipboard", "toolbar_grid"
    )

    // شناسه‌ + عرضِ پیش‌فرضِ هر کلیدِ ردیف پایین (جمعشون از ۱۰۰ باید همون نسبت‌های قبلی رو بده)
    private val bottomKeyDefaults = listOf(
        "symbols_toggle" to 12f, "autotype" to 14f, "lang_switch" to 14f,
        "space" to 22f, "pause_resume" to 13f, "zwnj" to 9f, "enter" to 16f
    )
    private val bottomKeyTypes = listOf(
        KeyType.SYMBOLS_TOGGLE, KeyType.AUTOTYPE, KeyType.LANG_SWITCH,
        KeyType.SPACE, KeyType.PAUSE_RESUME, KeyType.ZWNJ, KeyType.ENTER
    )

    private fun rebuildKeys(w: Int, h: Int) {
        keys.clear()
        if (w == 0 || h == 0) return

        // وزن‌های عرض/ارتفاعِ کلیدهای خاص (نوار بالا + ردیف پایین)، قابل ویرایش از KeyboardEditorActivity
        val specialWeights = PrefsHelper.getSpecialKeyWidthWeights(context)
        val toolbarHeightWeight = PrefsHelper.getToolbarHeightWeight(context)
        val bottomRowHeightWeight = PrefsHelper.getBottomRowHeightWeight(context)

        val toolbarHeight = TOOLBAR_HEIGHT_DP * density * toolbarHeightWeight

        if (currentSuggestions.isNotEmpty()) {
            // حالتِ پیشنهادها: میکروفون + حداکثر ۳ پیشنهاد + گرید، دقیقاً تو همون ردیفِ نوار بالا
            // (به‌جای ترجمه/تنظیمات/ایموجی/کلیپ‌بورد که موقتاً کنار می‌رن)
            val micWeight = specialWeights["toolbar_mic"] ?: 1f
            val gridWeight = specialWeights["toolbar_grid"] ?: 1f
            val middleWeight = (specialWeights["toolbar_translate"] ?: 1f) +
                (specialWeights["toolbar_settings"] ?: 1f) +
                (specialWeights["toolbar_emoji"] ?: 1f) +
                (specialWeights["toolbar_clipboard"] ?: 1f)
            val suggestionCount = currentSuggestions.size.coerceAtMost(3)
            val perSuggestionWeight = middleWeight / suggestionCount
            val weights = mutableListOf(micWeight)
            repeat(suggestionCount) { weights.add(perSuggestionWeight) }
            weights.add(gridWeight)
            val sum = weights.sum().takeIf { it > 0f } ?: 1f

            var leftTb = 0f
            var right = leftTb + w * (weights[0] / sum)
            keys.add(KeyRect("", RectF(leftTb, 0f, right, toolbarHeight), KeyType.TOOLBAR_MIC))
            leftTb = right
            for (i in 0 until suggestionCount) {
                right = leftTb + w * (weights[1 + i] / sum)
                keys.add(KeyRect(currentSuggestions[i], RectF(leftTb, 0f, right, toolbarHeight), KeyType.SUGGESTION))
                leftTb = right
            }
            keys.add(KeyRect("", RectF(leftTb, 0f, w.toFloat(), toolbarHeight), KeyType.TOOLBAR_GRID))
        } else {
            val toolbarWeights = toolbarKeyIds.map { specialWeights[it] ?: 1f }
            val toolbarWeightSum = toolbarWeights.sum().takeIf { it > 0f } ?: 1f
            var leftTb = 0f
            for (i in 0 until 6) {
                val right = leftTb + w * (toolbarWeights[i] / toolbarWeightSum)
                val type = when (i) {
                    0 -> KeyType.TOOLBAR_MIC
                    1 -> KeyType.TOOLBAR_TRANSLATE
                    2 -> KeyType.TOOLBAR_SETTINGS
                    3 -> KeyType.TOOLBAR_EMOJI
                    4 -> KeyType.TOOLBAR_CLIPBOARD
                    else -> KeyType.TOOLBAR_GRID
                }
                keys.add(KeyRect("", RectF(leftTb, 0f, right, toolbarHeight), type))
                leftTb = right
            }
        }

        val contentRows: List<List<String>> = when (mode) {
            KeyboardMode.LETTERS -> if (usePersian) {
                getPersianLetterRows()
            } else if (isShiftOn || isCapsLock) {
                KeyboardLayouts.ENGLISH.map { r -> r.map { it.uppercase() } }
            } else {
                KeyboardLayouts.ENGLISH
            }
            KeyboardMode.SYMBOLS -> KeyboardLayouts.SYMBOLS
            KeyboardMode.NUMBERS -> KeyboardLayouts.NUMBERS
            KeyboardMode.EMOJI -> KeyboardLayouts.EMOJI
        }
        val contentKeyType = if (mode == KeyboardMode.LETTERS) KeyType.LETTER else KeyType.SYMBOL

        val useWeightedLetters = mode == KeyboardMode.LETTERS && usePersian
        val widthWeights = if (useWeightedLetters) PrefsHelper.getLetterWidthWeights(context) else emptyMap()
        val rowHeightWeightsRaw = if (useWeightedLetters) PrefsHelper.getPersianRowHeightWeights(context) else null

        val keyboardAreaHeight = h - toolbarHeight
        rowHeight = keyboardAreaHeight / (contentRows.size + 1)

        // ارتفاع واقعی هر ردیف حروف + سهمِ ردیف پایین از کل ارتفاع (هر دو قابل تغییر تو ویرایش‌گر)
        val rowHeightWeightsResolved = rowHeightWeightsRaw ?: List(contentRows.size) { 1f }
        val totalWeight = rowHeightWeightsResolved.sum() + bottomRowHeightWeight
        val letterRowHeights: List<Float> = rowHeightWeightsResolved.map { keyboardAreaHeight * (it / totalWeight) }

        var runningTop = toolbarHeight
        for ((rowIndex, row) in contentRows.withIndex()) {
            val isLastContentRow = rowIndex == contentRows.size - 1
            val top = runningTop
            val thisRowHeight = letterRowHeights.getOrElse(rowIndex) { rowHeight }
            val bottom = top + thisRowHeight
            runningTop = bottom

            if (isLastContentRow) {
                val isEnglishLetters = contentKeyType == KeyType.LETTER && !usePersian
                val rowWeights = row.map { widthWeights[it] ?: 1f }
                val backspaceWeight = widthWeights["⌫"] ?: 1f
                val shiftWeight = 1.3f
                val sumWeights = rowWeights.sum() + backspaceWeight + (if (isEnglishLetters) shiftWeight else 0f)
                var left = 0f
                if (isEnglishLetters) {
                    val right = left + w * (shiftWeight / sumWeights)
                    val shiftLabel = if (isCapsLock) "⇪" else "⇧"
                    keys.add(KeyRect(shiftLabel, RectF(left, top, right, bottom), KeyType.SHIFT))
                    left = right
                }
                for ((colIndex, label) in row.withIndex()) {
                    val right = left + w * (rowWeights[colIndex] / sumWeights)
                    val hasRepl = contentKeyType == KeyType.LETTER && PrefsHelper.getReplacement(context, label).isNotBlank()
                    keys.add(KeyRect(label, RectF(left, top, right, bottom), contentKeyType, hasReplacement = hasRepl))
                    left = right
                }
                keys.add(KeyRect("⌫", RectF(left, top, w.toFloat(), bottom), KeyType.BACKSPACE))
            } else {
                val rowWeights = row.map { widthWeights[it] ?: 1f }
                val sumWeights = rowWeights.sum()
                var left = 0f
                for ((colIndex, label) in row.withIndex()) {
                    val right = left + w * (rowWeights[colIndex] / sumWeights)
                    val hint = if (mode == KeyboardMode.LETTERS && usePersian && rowIndex == 0) {
                        KeyboardLayouts.PERSIAN_ROW1_DIGIT_HINTS.getOrElse(colIndex) { "" }
                    } else {
                        ""
                    }
                    val hasRepl = contentKeyType == KeyType.LETTER && PrefsHelper.getReplacement(context, label).isNotBlank()
                    keys.add(KeyRect(label, RectF(left, top, right, bottom), contentKeyType, hint, hasRepl))
                    left = right
                }
            }
        }

        val bottomTop = runningTop
        val bottomBottom = toolbarHeight + keyboardAreaHeight
        val bottomWeights = bottomKeyDefaults.map { (id, def) -> specialWeights[id] ?: def }
        val bottomWeightSum = bottomWeights.sum().takeIf { it > 0f } ?: 1f
        val spaceLabel = if (mode == KeyboardMode.NUMBERS) "٠" else PrefsHelper.getSpaceLabel(context)

        var x = 0f
        for (i in bottomKeyDefaults.indices) {
            val right = x + w * (bottomWeights[i] / bottomWeightSum)
            val type = bottomKeyTypes[i]
            val label = when (type) {
                KeyType.SPACE -> spaceLabel
                KeyType.ENTER -> "⏎"
                else -> ""
            }
            keys.add(KeyRect(label, RectF(x, bottomTop, right, bottomBottom), type))
            x = right
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        backgroundBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        } ?: canvas.drawColor(fallbackBgColor)

        textPaint.textSize = rowHeight * 0.4f

        for (key in keys) {
            if (key.type == KeyType.TOOLBAR_MIC || key.type == KeyType.TOOLBAR_TRANSLATE ||
                key.type == KeyType.TOOLBAR_SETTINGS || key.type == KeyType.TOOLBAR_EMOJI ||
                key.type == KeyType.TOOLBAR_CLIPBOARD || key.type == KeyType.TOOLBAR_GRID
            ) {
                when (key.type) {
                    KeyType.TOOLBAR_MIC -> drawMicIcon(canvas, key.rect)
                    KeyType.TOOLBAR_TRANSLATE -> drawTranslateIcon(canvas, key.rect)
                    KeyType.TOOLBAR_SETTINGS -> drawGearIcon(canvas, key.rect)
                    KeyType.TOOLBAR_EMOJI -> drawStickerIcon(canvas, key.rect)
                    KeyType.TOOLBAR_CLIPBOARD -> drawClipboardIcon(canvas, key.rect)
                    KeyType.TOOLBAR_GRID -> drawGridIcon(canvas, key.rect)
                    else -> {}
                }
                continue
            }
            if (key.type == KeyType.SUGGESTION) {
                drawSuggestionKey(canvas, key)
                continue
            }
            val paint = when {
                key.label == highlightedLabel -> highlightPaint
                key.type == KeyType.ENTER || key.type == KeyType.SYMBOLS_TOGGLE ||
                    key.type == KeyType.AUTOTYPE || key.type == KeyType.ZWNJ -> enterAccentPaint
                key.type == KeyType.BACKSPACE -> accentPaint
                key.type == KeyType.SHIFT && (isShiftOn || isCapsLock) -> accentPaint
                key.type == KeyType.LETTER || key.type == KeyType.SYMBOL -> keyPaint
                else -> specialKeyPaint
            }
            val pad = keyPadDp * density
            val paddedRect = RectF(key.rect.left + pad, key.rect.top + pad, key.rect.right - pad, key.rect.bottom - pad)
            val corner = if (key.type == KeyType.SYMBOLS_TOGGLE || key.type == KeyType.ENTER) {
                paddedRect.height() / 2f // شکل کپسولی/قرصی برای ۱۲۳ و Enter
            } else {
                keyCornerDp * density
            }
            canvas.drawRoundRect(paddedRect, corner, corner, paint)
            val cx = key.rect.centerX()
            val cy = key.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2

            if (key.type == KeyType.SYMBOLS_TOGGLE) {
                val label = if (mode == KeyboardMode.LETTERS) "؟١٢٣" else "حروف"
                canvas.drawText(label, cx, cy, Paint(textPaint).apply { textSize = rowHeight * 0.2f })
            } else if (key.type == KeyType.SPACE) {
                val spaceTextPaint = Paint(textPaint).apply {
                    textSize = rowHeight * 0.22f
                }
                canvas.drawText(key.label, cx, cy, spaceTextPaint)
            } else if (key.type == KeyType.PAUSE_RESUME) {
                when {
                    mode == KeyboardMode.SYMBOLS -> canvas.drawText("١٢٣", cx, cy, Paint(textPaint).apply { textSize = rowHeight * 0.2f })
                    mode == KeyboardMode.NUMBERS -> canvas.drawText("؟", cx, cy, Paint(textPaint).apply { textSize = rowHeight * 0.24f })
                    else -> drawArrowIcon(canvas, key.rect)
                }
            } else if (key.type == KeyType.AUTOTYPE) {
                when {
                    mode == KeyboardMode.NUMBERS -> canvas.drawText(".", cx, cy, Paint(textPaint).apply { textSize = rowHeight * 0.3f })
                    else -> drawAutoTypeIcon(canvas, key.rect)
                }
            } else if (key.type == KeyType.LANG_SWITCH) {
                when {
                    mode != KeyboardMode.LETTERS -> canvas.drawText("ابپ", cx, cy, Paint(textPaint).apply { textSize = rowHeight * 0.2f })
                    else -> drawGlobeIcon(canvas, key.rect)
                }
            } else if (key.type == KeyType.ENTER) {
                drawEnterIcon(canvas, key.rect)
            } else if (key.type == KeyType.ZWNJ) {
                drawZwnjIcon(canvas, key.rect)
            } else if (key.type == KeyType.SHIFT) {
                drawShiftIcon(canvas, key.rect)
            } else {
                canvas.drawText(key.label, cx, cy, textPaint)
            }

            if (key.subLabel.isNotEmpty()) {
                val hintPaint = Paint(labelPaint).apply {
                    textAlign = Paint.Align.RIGHT
                    textSize = rowHeight * 0.16f
                }
                canvas.drawText(key.subLabel, key.rect.right - 8f * density, key.rect.top + rowHeight * 0.24f, hintPaint)
            }

            if (key.type == KeyType.LETTER) {
                if (key.hasReplacement) {
                    canvas.drawText("•", key.rect.centerX(), key.rect.bottom - 10f, labelPaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val actionIndex = event.actionIndex
        val x = event.getX(actionIndex)
        val y = event.getY(actionIndex)
        val pointerId = event.getPointerId(actionIndex)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val key = keys.firstOrNull { it.rect.contains(x, y) } ?: return true
                when (key.type) {
                    KeyType.SPACE -> {
                        spacePressed = true
                        spacePointerId = pointerId
                        spaceLongPressTriggered = false
                        handler.postDelayed(spaceLongPressRunnable, SPACE_LONG_PRESS_MS)
                    }
                    KeyType.BACKSPACE -> {
                        backspacePressed = true
                        backspacePointerId = pointerId
                        backspaceRepeatCount = 0
                        listener?.onBackspace()
                        handler.postDelayed(backspaceRunnable, BACKSPACE_INITIAL_DELAY_MS)
                    }
                    KeyType.LANG_SWITCH -> {
                        langPressed = true
                        langPointerId = pointerId
                        val now = System.currentTimeMillis()
                        langDoubleTapCandidate = (now - lastLangUpTime) < 300L
                    }
                    KeyType.LETTER -> {
                        letterPressed = true
                        letterPointerId = pointerId
                        letterPressedKey = key
                        letterLongPressTriggered = false
                        handler.postDelayed(letterLongPressRunnable, LETTER_LONG_PRESS_MS)
                    }
                    KeyType.SHIFT -> {
                        shiftPressed = true
                        shiftPointerId = pointerId
                        val now = System.currentTimeMillis()
                        shiftDoubleTapCandidate = (now - lastShiftUpTime) < 300L
                    }
                    else -> dispatchKey(key)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (spacePressed && pointerId == spacePointerId) {
                    handler.removeCallbacks(spaceLongPressRunnable)
                    if (!spaceLongPressTriggered) {
                        if (mode == KeyboardMode.NUMBERS) {
                            listener?.onCommitText("٠")
                        } else {
                            listener?.onSpace()
                        }
                    }
                    spacePressed = false
                }
                if (langPressed && pointerId == langPointerId) {
                    if (mode != KeyboardMode.LETTERS) {
                        mode = KeyboardMode.LETTERS
                        rebuildKeys(width, height)
                        invalidate()
                        lastLangUpTime = 0L
                    } else if (langDoubleTapCandidate) {
                        mode = KeyboardMode.SYMBOLS
                        rebuildKeys(width, height)
                        invalidate()
                        lastLangUpTime = 0L
                    } else {
                        setLanguage(!usePersian)
                        lastLangUpTime = System.currentTimeMillis()
                    }
                    langPressed = false
                }
                if (backspacePressed && pointerId == backspacePointerId) {
                    handler.removeCallbacks(backspaceRunnable)
                    backspacePressed = false
                }
                if (letterPressed && pointerId == letterPointerId) {
                    handler.removeCallbacks(letterLongPressRunnable)
                    if (!letterLongPressTriggered) {
                        letterPressedKey?.let { dispatchKey(it) }
                    }
                    letterPressed = false
                    letterPressedKey = null
                }
                if (shiftPressed && pointerId == shiftPointerId) {
                    if (shiftDoubleTapCandidate) {
                        isCapsLock = !isCapsLock
                        isShiftOn = false
                        lastShiftUpTime = 0L
                    } else {
                        if (isCapsLock) {
                            isCapsLock = false
                            isShiftOn = false
                        } else {
                            isShiftOn = !isShiftOn
                        }
                        lastShiftUpTime = System.currentTimeMillis()
                    }
                    shiftPressed = false
                    rebuildKeys(width, height)
                    invalidate()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (spacePressed) {
                    handler.removeCallbacks(spaceLongPressRunnable)
                    spacePressed = false
                }
                if (langPressed) {
                    langPressed = false
                }
                if (backspacePressed) {
                    handler.removeCallbacks(backspaceRunnable)
                    backspacePressed = false
                }
                if (letterPressed) {
                    handler.removeCallbacks(letterLongPressRunnable)
                    letterPressed = false
                    letterPressedKey = null
                }
                if (shiftPressed) {
                    shiftPressed = false
                }
            }
        }
        return true
    }

    private fun dispatchKey(key: KeyRect) {
        when (key.type) {
            KeyType.BACKSPACE -> listener?.onBackspace()
            KeyType.ENTER -> listener?.onEnter()
            KeyType.AUTOTYPE -> {
                if (mode == KeyboardMode.NUMBERS) {
                    listener?.onCommitText(".")
                } else {
                    listener?.onAutoTypeButton()
                }
            }
            KeyType.PAUSE_RESUME -> {
                when (mode) {
                    KeyboardMode.SYMBOLS -> {
                        mode = KeyboardMode.NUMBERS
                        rebuildKeys(width, height)
                        invalidate()
                    }
                    KeyboardMode.NUMBERS -> {
                        mode = KeyboardMode.SYMBOLS
                        rebuildKeys(width, height)
                        invalidate()
                    }
                    KeyboardMode.LETTERS -> listener?.onPauseResumeButton()
                    KeyboardMode.EMOJI -> listener?.onPauseResumeButton()
                }
            }
            KeyType.WORD_SHUFFLE -> listener?.onWordShuffleButton()
            KeyType.SYMBOLS_TOGGLE -> {
                mode = if (mode == KeyboardMode.LETTERS) KeyboardMode.SYMBOLS else KeyboardMode.LETTERS
                rebuildKeys(width, height)
                invalidate()
            }
            KeyType.TOOLBAR_SETTINGS -> listener?.onSettingsButton()
            KeyType.TOOLBAR_EMOJI -> {
                mode = if (mode == KeyboardMode.EMOJI) KeyboardMode.LETTERS else KeyboardMode.EMOJI
                rebuildKeys(width, height)
                invalidate()
            }
            KeyType.TOOLBAR_CLIPBOARD -> {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clipText = clipboard?.primaryClip?.let { clip ->
                    if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
                }
                if (!clipText.isNullOrEmpty()) {
                    listener?.onCommitText(clipText)
                }
            }
            KeyType.TOOLBAR_GRID -> listener?.onToggleResizeMode()
            KeyType.TOOLBAR_MIC, KeyType.TOOLBAR_TRANSLATE -> {
                android.widget.Toast.makeText(context, "این بخش هنوز آماده نیست", android.widget.Toast.LENGTH_SHORT).show()
            }
            KeyType.SUGGESTION -> listener?.onSuggestionTap(key.label)
            KeyType.LETTER -> handleLetterTap(key.label)
            KeyType.SYMBOL -> {
                flashKey(key.label)
                listener?.onCommitText(key.label)
            }
            KeyType.ZWNJ -> {
                flashKey("\u200C")
                listener?.onCommitText("\u200C")
            }
            else -> {}
        }
    }

    private val arrowIconPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private fun drawArrowIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val triW = rowHeight * 0.09f
        val triH = rowHeight * 0.13f
        val gap = rowHeight * 0.16f

        val leftPath = Path().apply {
            moveTo(cx - gap + triW * 0.5f, cy - triH)
            lineTo(cx - gap - triW * 0.5f, cy)
            lineTo(cx - gap + triW * 0.5f, cy + triH)
            close()
        }
        canvas.drawPath(leftPath, arrowIconPaint)

        val rightPath = Path().apply {
            moveTo(cx + gap - triW * 0.5f, cy - triH)
            lineTo(cx + gap + triW * 0.5f, cy)
            lineTo(cx + gap - triW * 0.5f, cy + triH)
            close()
        }
        canvas.drawPath(rightPath, arrowIconPaint)

        val dotR = rowHeight * 0.014f
        val dotSpacing = rowHeight * 0.065f
        for (i in -2..2) {
            canvas.drawCircle(cx, cy + i * dotSpacing, dotR, smileyDotPaint)
        }
    }

    // آیکونِ شیفت: پیکانِ رو به بالا؛ وقتی فعاله توپر و پررنگه، وقتی قفلِ حروفِ بزرگه یه خطِ زیرش هم داره
    private fun drawShiftIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rowHeight * 0.19f

        val arrowPath = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r * 0.62f, cy - r * 0.14f)
            lineTo(cx + r * 0.26f, cy - r * 0.14f)
            lineTo(cx + r * 0.26f, cy + r * 0.55f)
            lineTo(cx - r * 0.26f, cy + r * 0.55f)
            lineTo(cx - r * 0.26f, cy - r * 0.14f)
            lineTo(cx - r * 0.62f, cy - r * 0.14f)
            close()
        }

        if (isShiftOn || isCapsLock) {
            val fillPaint = Paint(smileyDotPaint).apply { style = Paint.Style.FILL; isAntiAlias = true }
            canvas.drawPath(arrowPath, fillPaint)
        } else {
            val outlinePaint = Paint(smileyStrokePaint).apply {
                strokeWidth = r * 0.16f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(arrowPath, outlinePaint)
        }

        if (isCapsLock) {
            val barPaint = Paint(smileyDotPaint).apply { style = Paint.Style.FILL; isAntiAlias = true }
            canvas.drawRoundRect(
                RectF(cx - r * 0.62f, cy + r * 0.8f, cx + r * 0.62f, cy + r * 0.98f),
                r * 0.08f, r * 0.08f, barPaint
            )
        }
    }

    private val smileyStrokePaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
    }
    private val smileyDotPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private fun drawSmileyIcon(canvas: Canvas, rect: RectF, radius: Float = rowHeight * 0.17f) {
        smileyStrokePaint.strokeWidth = 1.6f * density
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = radius

        canvas.drawCircle(cx, cy, r, smileyStrokePaint)

        val eyeR = r * 0.11f
        val eyeOffsetX = r * 0.38f
        val eyeOffsetY = r * 0.22f
        canvas.drawCircle(cx - eyeOffsetX, cy - eyeOffsetY, eyeR, smileyDotPaint)
        canvas.drawCircle(cx + eyeOffsetX, cy - eyeOffsetY, eyeR, smileyDotPaint)

        val mouthRect = RectF(cx - r * 0.55f, cy - r * 0.35f, cx + r * 0.55f, cy + r * 0.6f)
        canvas.drawArc(mouthRect, 20f, 140f, false, smileyStrokePaint)
    }

    private fun drawStickerIcon(canvas: Canvas, rect: RectF) {
        val strokePaint = Paint(smileyStrokePaint).apply {
            strokeWidth = 1.7f * density
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        }
        val cx = rect.centerX()
        val cy = rect.centerY()
        val size = rect.height() * 0.42f // بدنه دقیقاً مربع
        val half = size * 0.5f
        val left = cx - half
        val right = cx + half
        val top = cy - half
        val bottom = cy + half
        val fold = size * 0.27f // اندازه‌ی گوشه‌ی تاشده

        // بدنه‌ی مربعی با گوشه‌ی تا‌شده (سر تیز) پایین-راست
        val path = Path().apply {
            moveTo(left, top)
            lineTo(right, top)
            lineTo(right, bottom - fold)
            lineTo(right - fold, bottom)
            lineTo(left, bottom)
            close()
        }
        canvas.drawPath(path, strokePaint)

        // خط تای گوشه
        val foldPath = Path().apply {
            moveTo(right - fold, bottom - fold)
            lineTo(right, bottom - fold)
            lineTo(right - fold, bottom)
            close()
        }
        canvas.drawPath(foldPath, strokePaint)

        val fillPaint = Paint(smileyDotPaint).apply { style = Paint.Style.FILL; isAntiAlias = true }

        // دو چشم قوسی‌شکل و هم‌اندازه (مثل چشم‌های خندون بسته)
        val eyeR = size * 0.11f
        val eyeOffsetX = size * 0.19f
        val eyeY = top + size * 0.30f
        val eyeRectLeft = RectF(cx - eyeOffsetX - eyeR, eyeY - eyeR, cx - eyeOffsetX + eyeR, eyeY + eyeR)
        val eyeRectRight = RectF(cx + eyeOffsetX - eyeR, eyeY - eyeR, cx + eyeOffsetX + eyeR, eyeY + eyeR)
        canvas.drawArc(eyeRectLeft, 180f, 180f, true, fillPaint)
        canvas.drawArc(eyeRectRight, 180f, 180f, true, fillPaint)

        // لبخند: شکل هلالی توپر و پررنگ که به سمت پایین باریک و نوک‌تیز می‌شه
        val mouthHalfW = size * 0.25f
        val mouthTopY = top + size * 0.55f
        val mouthBottomY = top + size * 0.72f
        val mouthPath = Path().apply {
            moveTo(cx - mouthHalfW, mouthTopY)
            quadTo(cx, mouthTopY - size * 0.06f, cx + mouthHalfW, mouthTopY)
            quadTo(cx, mouthBottomY, cx - mouthHalfW, mouthTopY)
            close()
        }
        canvas.drawPath(mouthPath, fillPaint)
    }

    private fun drawAutoTypeIcon(canvas: Canvas, rect: RectF, radius: Float = rowHeight * 0.15f) {
        val cx = rect.centerX()
        val cy = rect.centerY() - radius * 0.55f
        val r = radius

        val strokePaint = Paint(smileyStrokePaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = r * 0.10f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawCircle(cx, cy, r, strokePaint)

        val fillPaint = Paint(smileyDotPaint).apply { style = Paint.Style.FILL; isAntiAlias = true }

        // چشم‌ها
        val eyeR = r * 0.14f
        val eyeOffsetX = r * 0.37f
        val eyeOffsetY = r * 0.24f
        canvas.drawCircle(cx - eyeOffsetX, cy - eyeOffsetY, eyeR, fillPaint)
        canvas.drawCircle(cx + eyeOffsetX, cy - eyeOffsetY, eyeR, fillPaint)

        // لبخندِ توپر (به‌جای خطِ نازکِ کمانی)
        val mouthHalfW = r * 0.46f
        val mouthTopY = cy + r * 0.24f
        val mouthTipY = cy + r * 0.59f
        val mouthPath = Path().apply {
            moveTo(cx - mouthHalfW, mouthTopY)
            quadTo(cx, mouthTopY - r * 0.05f, cx + mouthHalfW, mouthTopY)
            quadTo(cx, mouthTipY, cx - mouthHalfW, mouthTopY)
            close()
        }
        canvas.drawPath(mouthPath, fillPaint)

        // علامتِ کاما — شکلِ برداریِ دقیق و خمیده (به‌جای گلیفِ فونتِ نازک)
        val commaPath = Path().apply {
            moveTo(cx + 0.0560f * r, cy + 3.0960f * r)
            lineTo(cx + 0.0160f * r, cy + 3.1200f * r)
            lineTo(cx + -0.0080f * r, cy + 3.1440f * r)
            lineTo(cx + -0.0240f * r, cy + 3.1680f * r)
            lineTo(cx + -0.0480f * r, cy + 3.1920f * r)
            lineTo(cx + -0.0640f * r, cy + 3.2160f * r)
            lineTo(cx + -0.0800f * r, cy + 3.2400f * r)
            lineTo(cx + -0.0960f * r, cy + 3.2640f * r)
            lineTo(cx + -0.1120f * r, cy + 3.2880f * r)
            lineTo(cx + -0.1280f * r, cy + 3.3120f * r)
            lineTo(cx + -0.1440f * r, cy + 3.3360f * r)
            lineTo(cx + -0.1520f * r, cy + 3.3600f * r)
            lineTo(cx + -0.1680f * r, cy + 3.3840f * r)
            lineTo(cx + -0.1840f * r, cy + 3.4080f * r)
            lineTo(cx + -0.1920f * r, cy + 3.4320f * r)
            lineTo(cx + -0.2080f * r, cy + 3.4560f * r)
            lineTo(cx + -0.2160f * r, cy + 3.4800f * r)
            lineTo(cx + -0.2240f * r, cy + 3.5040f * r)
            lineTo(cx + -0.2320f * r, cy + 3.5280f * r)
            lineTo(cx + -0.2400f * r, cy + 3.5520f * r)
            lineTo(cx + -0.2480f * r, cy + 3.5760f * r)
            lineTo(cx + -0.2560f * r, cy + 3.6000f * r)
            lineTo(cx + -0.2640f * r, cy + 3.6240f * r)
            lineTo(cx + -0.2640f * r, cy + 3.6480f * r)
            lineTo(cx + -0.2640f * r, cy + 3.6720f * r)
            lineTo(cx + -0.2640f * r, cy + 3.6960f * r)
            lineTo(cx + -0.2640f * r, cy + 3.7200f * r)
            lineTo(cx + -0.2560f * r, cy + 3.7440f * r)
            lineTo(cx + -0.2480f * r, cy + 3.7680f * r)
            lineTo(cx + -0.2400f * r, cy + 3.7920f * r)
            lineTo(cx + -0.2240f * r, cy + 3.8160f * r)
            lineTo(cx + -0.2000f * r, cy + 3.8400f * r)
            lineTo(cx + -0.1680f * r, cy + 3.8640f * r)
            lineTo(cx + -0.1120f * r, cy + 3.8880f * r)
            lineTo(cx + -0.0240f * r, cy + 3.8880f * r)
            lineTo(cx + 0.0400f * r, cy + 3.8640f * r)
            lineTo(cx + 0.0720f * r, cy + 3.8400f * r)
            lineTo(cx + 0.0960f * r, cy + 3.8160f * r)
            lineTo(cx + 0.1200f * r, cy + 3.7920f * r)
            lineTo(cx + 0.1280f * r, cy + 3.7680f * r)
            lineTo(cx + 0.1360f * r, cy + 3.7440f * r)
            lineTo(cx + 0.1440f * r, cy + 3.7200f * r)
            lineTo(cx + 0.1360f * r, cy + 3.6960f * r)
            lineTo(cx + 0.1280f * r, cy + 3.6720f * r)
            lineTo(cx + 0.0960f * r, cy + 3.6480f * r)
            lineTo(cx + 0.0320f * r, cy + 3.6240f * r)
            lineTo(cx + 0.0000f * r, cy + 3.6000f * r)
            lineTo(cx + -0.0080f * r, cy + 3.5760f * r)
            lineTo(cx + -0.0160f * r, cy + 3.5520f * r)
            lineTo(cx + -0.0160f * r, cy + 3.5280f * r)
            lineTo(cx + -0.0080f * r, cy + 3.5040f * r)
            lineTo(cx + -0.0080f * r, cy + 3.4800f * r)
            lineTo(cx + 0.0000f * r, cy + 3.4560f * r)
            lineTo(cx + 0.0080f * r, cy + 3.4320f * r)
            lineTo(cx + 0.0160f * r, cy + 3.4080f * r)
            lineTo(cx + 0.0320f * r, cy + 3.3840f * r)
            lineTo(cx + 0.0400f * r, cy + 3.3600f * r)
            lineTo(cx + 0.0560f * r, cy + 3.3360f * r)
            lineTo(cx + 0.0640f * r, cy + 3.3120f * r)
            lineTo(cx + 0.0800f * r, cy + 3.2880f * r)
            lineTo(cx + 0.0880f * r, cy + 3.2640f * r)
            lineTo(cx + 0.1040f * r, cy + 3.2400f * r)
            lineTo(cx + 0.1200f * r, cy + 3.2160f * r)
            lineTo(cx + 0.1360f * r, cy + 3.1920f * r)
            lineTo(cx + 0.1440f * r, cy + 3.1680f * r)
            lineTo(cx + 0.1440f * r, cy + 3.1440f * r)
            lineTo(cx + 0.1360f * r, cy + 3.1200f * r)
            lineTo(cx + 0.1120f * r, cy + 3.0960f * r)
            close()
        }
        canvas.drawPath(commaPath, fillPaint)
    }

    // این‌جوری کلمه‌ی طولانی، فضای گرید/منو رو نمی‌گیره و همیشه یه‌ذره جا براش می‌مونه
    private fun drawSuggestionKey(canvas: Canvas, key: KeyRect) {
        val paint = Paint(textPaint).apply { textSize = rowHeight * 0.3f }
        val maxWidth = (key.rect.width() - 10f * density).coerceAtLeast(10f)
        var text = key.label
        if (paint.measureText(text) > maxWidth) {
            while (text.length > 1 && paint.measureText("$text…") > maxWidth) {
                text = text.dropLast(1)
            }
            text = "$text…"
        }
        val cx = key.rect.centerX()
        val cy = key.rect.centerY() - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(text, cx, cy, paint)
    }

    private fun drawMicIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val h = rect.height() * 0.26f // واحدِ اندازه: هم‌مقیاس با بقیه‌ی آیکون‌های تولبار

        val strokePaint = Paint(smileyStrokePaint).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = h * 0.22f // دیواره‌ی ضخیم‌تر برای جمع‌وجورتر شدنِ حفره‌ی وسطِ کپسول
        }

        // بدنه‌ی کپسولی (سرِ میکروفون) — حفره‌ی وسط جمع‌وجورتر
        val bodyW = h * 0.56f
        val bodyTop = cy - h
        val bodyBottom = cy + h * 0.05f
        val bodyRect = RectF(cx - bodyW / 2f, bodyTop, cx + bodyW / 2f, bodyBottom)
        canvas.drawRoundRect(bodyRect, bodyW / 2f, bodyW / 2f, strokePaint)

        // حلقه‌ی نگه‌دارنده: کوچیک‌تر از نسخه‌ی قبل
        val standHalfW = h * 0.52f
        val standTop = cy - h * 0.45f
        val standBottom = cy + h * 0.42f
        val standRect = RectF(cx - standHalfW, standTop, cx + standHalfW, standBottom)
        canvas.drawArc(standRect, 0f, 180f, false, strokePaint)

        // ساقه‌ی عمودی با نوکِ گرد — بدونِ خطِ افقیِ پایه
        canvas.drawLine(cx, standBottom, cx, cy + h * 0.7f, strokePaint)
    }

    private fun drawTranslateIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val cardSize = rect.height() * 0.34f
        val cardCorner = cardSize * 0.22f
        val overlap = cardSize * 0.32f // هم‌پوشانی افقی بین دو کارت

        val fillPaint = Paint(smileyDotPaint).apply { style = Paint.Style.FILL; isAntiAlias = true }
        val seamPaint = Paint().apply {
            color = fallbackBgColor
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
            isAntiAlias = true
        }
        val darkTextPaint = Paint().apply {
            color = fallbackBgColor
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // کارت پشتی (سمت راست) با یه کاراکتر شبیه حروف ترجمه (سبک CJK)
        val backCx = cx + overlap
        val backRect = RectF(backCx - cardSize / 2, cy - cardSize / 2, backCx + cardSize / 2, cy + cardSize / 2)
        canvas.drawRoundRect(backRect, cardCorner, cardCorner, fillPaint)
        val cjkPaint = Paint(darkTextPaint).apply { textSize = cardSize * 0.56f }
        canvas.drawText("字", backCx, cy - (cjkPaint.descent() + cjkPaint.ascent()) / 2, cjkPaint)

        // کارت جلویی (سمت چپ) با حرف G توپر، با یه خط باریک دور خودش تا از کارت پشتی جدا دیده بشه
        val frontCx = cx - overlap
        val frontRect = RectF(frontCx - cardSize / 2, cy - cardSize / 2, frontCx + cardSize / 2, cy + cardSize / 2)
        canvas.drawRoundRect(frontRect, cardCorner, cardCorner, fillPaint)
        canvas.drawRoundRect(frontRect, cardCorner, cardCorner, seamPaint)
        val gPaint = Paint(darkTextPaint).apply { textSize = cardSize * 0.62f; isFakeBoldText = true }
        canvas.drawText("G", frontCx, cy - (gPaint.descent() + gPaint.ascent()) / 2, gPaint)
    }

    private fun drawTinyStar(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val path = Path()
        for (i in 0 until 4) {
            val angle = Math.toRadians((i * 90).toDouble())
            val outerX = cx + (r * Math.cos(angle)).toFloat()
            val outerY = cy + (r * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(outerX, outerY) else path.lineTo(outerX, outerY)
            val midAngle = Math.toRadians((i * 90 + 45).toDouble())
            val innerX = cx + (r * 0.4f * Math.cos(midAngle)).toFloat()
            val innerY = cy + (r * 0.4f * Math.sin(midAngle)).toFloat()
            path.lineTo(innerX, innerY)
        }
        path.close()
        val starPaint = Paint(smileyDotPaint).apply { style = Paint.Style.FILL }
        canvas.drawPath(path, starPaint)
    }

    private fun drawGearIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val rOuter = rect.height() * 0.27f // شعاعِ نوکِ پره‌ها تا مرکز

        val rBody = rOuter * 0.725f          // شعاعِ حلقه‌ی ضخیمِ پشتِ پره‌ها
        val holeR = rOuter * 0.30f           // شعاعِ حفره‌ی وسط
        val toothInnerR = rOuter * 0.575f    // از کجای شعاع، پره شروع می‌شه (داخلِ حلقه، برای اتصالِ بی‌درز)
        val toothLen = rOuter * 0.425f       // طولِ پره (نوکش دقیقاً رو rOuter می‌شینه)
        val toothHalfW = rOuter * 0.25f      // نصفِ عرضِ پره
        val cornerRx = rOuter * 0.0875f      // گردیِ ملایمِ گوشه‌های پره

        val fillPaint = Paint().apply {
            color = smileyDotPaint.color
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        canvas.drawCircle(cx, cy, rBody, fillPaint)

        // ۶ پره، یکی‌شون دقیقاً رو به بالا؛ بقیه هر ۶۰ درجه فاصله دارن
        val angles = floatArrayOf(-90f, -30f, 30f, 90f, 150f, 210f)
        for (angle in angles) {
            canvas.save()
            canvas.rotate(angle, cx, cy)
            val toothRect = RectF(
                cx + toothInnerR,
                cy - toothHalfW,
                cx + toothInnerR + toothLen,
                cy + toothHalfW
            )
            canvas.drawRoundRect(toothRect, cornerRx, cornerRx, fillPaint)
            canvas.restore()
        }

        val holePaint = Paint().apply {
            color = fallbackBgColor
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, holeR, holePaint)
    }

    private fun drawClipboardIcon(canvas: Canvas, rect: RectF) {
        val strokePaint = Paint(smileyStrokePaint).apply {
            strokeWidth = 1.6f * density
            style = Paint.Style.STROKE
        }
        val cx = rect.centerX()
        val cy = rect.centerY()
        val size = rect.height() * 0.42f // بدنه دقیقاً مربع (عرض = ارتفاع)

        val bodyTop = cy - size / 2f
        val bodyBottom = cy + size / 2f
        val bodyRect = RectF(cx - size / 2f, bodyTop, cx + size / 2f, bodyBottom)
        canvas.drawRect(bodyRect, strokePaint) // گوشه‌های تیز، بدون گرد شدن

        // یه نیم‌دایره‌ی توخالی (فقط خط دور) وسط لبه‌ی بالا، با یه نقطه‌ی توپر کوچیک وسطش
        val clipR = size * 0.15f
        val clipRect = RectF(cx - clipR, bodyTop - clipR, cx + clipR, bodyTop + clipR)
        canvas.drawArc(clipRect, 180f, 180f, false, strokePaint)
        val dotPaint = Paint(smileyDotPaint).apply { style = Paint.Style.FILL; isAntiAlias = true }
        canvas.drawCircle(cx, bodyTop - clipR * 0.3f, clipR * 0.2f, dotPaint)

        // سه خط داخلی؛ دوتای اول تمام‌عرض و هم‌تراز، سومی از سمت چپ کوتاه‌تره
        // و لبه‌ی راستش با اون دوتا یکیه (فقط از چپ نصفه شده)
        val linePaint = Paint(smileyStrokePaint).apply {
            strokeWidth = 1.6f * density
            strokeCap = Paint.Cap.ROUND
        }
        val innerRight = cx + size * 0.27f
        val innerLeftFull = cx - size * 0.27f
        val innerLeftShort = cx - size * 0.09f
        val lineY1 = bodyTop + size * 0.26f
        val lineY2 = bodyTop + size * 0.49f
        val lineY3 = bodyTop + size * 0.73f
        canvas.drawLine(innerLeftFull, lineY1, innerRight, lineY1, linePaint)
        canvas.drawLine(innerLeftFull, lineY2, innerRight, lineY2, linePaint)
        canvas.drawLine(innerLeftShort, lineY3, innerRight, lineY3, linePaint)
    }

    private fun drawZwnjIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY() - rect.height() * 0.171f
        val r = rect.height() * 0.083f

        val dotPaint = Paint(smileyDotPaint).apply { style = Paint.Style.FILL; isAntiAlias = true }

        // دایره‌ی نقطه‌چین (شبیه یه حلقه‌ی نقطه‌نقطه)
        val ringDotRadius = r * 0.133f
        val ringDotCount = 10
        for (i in 0 until ringDotCount) {
            val angle = 2.0 * Math.PI * i / ringDotCount
            val dx = cx + (r * Math.cos(angle)).toFloat()
            val dy = cy + (r * Math.sin(angle)).toFloat()
            canvas.drawCircle(dx, dy, ringDotRadius, dotPaint)
        }

        // دو تا خط کوچیک بالا-چپ دایره (نشونه‌ی حرکت)
        val tickPaint = Paint(smileyStrokePaint).apply {
            strokeWidth = 1.3f * density
            strokeCap = Paint.Cap.ROUND
        }
        val tickBaseX = cx - r * 1.3f
        val tickBaseY = cy - r * 0.95f
        val tickLen = r * 0.55f
        canvas.drawLine(tickBaseX, tickBaseY, tickBaseX + tickLen, tickBaseY - tickLen * 0.4f, tickPaint)
        canvas.drawLine(tickBaseX - tickLen * 0.25f, tickBaseY + tickLen * 0.45f, tickBaseX + tickLen * 0.5f, tickBaseY - tickLen * 0.05f, tickPaint)

        // نقطه‌ی جدا زیر دایره
        val bigDotRadius = r * 0.419f
        canvas.drawCircle(cx, cy + r * 4.15f, bigDotRadius, dotPaint)
    }

    private fun drawGridIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val s = rect.height() * 0.16f
        val gap = rect.height() * 0.07f
        for (row in -1..0) {
            for (col in -1..0) {
                val left = cx + col * (s + gap) + gap / 2
                val top = cy + row * (s + gap) + gap / 2
                canvas.drawRoundRect(RectF(left, top, left + s, top + s), 2f * density, 2f * density, smileyDotPaint)
            }
        }
    }

    private fun drawEnterIcon(canvas: Canvas, rect: RectF) {
        val enterIconPaint = Paint().apply {
            color = Color.parseColor("#16305C")
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val cx = rect.centerX()
        val cy = rect.centerY()
        val w = rowHeight * 0.15f
        val hookH = rowHeight * 0.1f

        val shaftPath = Path().apply {
            moveTo(cx - w * 0.35f, cy - hookH)
            lineTo(cx - w * 0.35f, cy)
            lineTo(cx + w, cy)
        }
        canvas.drawPath(shaftPath, enterIconPaint)

        val headSize = rowHeight * 0.09f
        val headPath = Path().apply {
            moveTo(cx + w - headSize, cy - headSize)
            lineTo(cx + w, cy)
            lineTo(cx + w - headSize, cy + headSize)
        }
        canvas.drawPath(headPath, enterIconPaint)
    }

    private fun drawGlobeIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rowHeight * 0.17f
        smileyStrokePaint.strokeWidth = r * 0.18f // ضخیم و متناسب با اندازه‌ی آیکون

        // دایره‌ی بیرونی
        canvas.drawCircle(cx, cy, r, smileyStrokePaint)

        // بیضیِ عمودیِ وسط (نصف‌النهار)
        val vrx = r * 0.37f
        val vOval = RectF(cx - vrx, cy - r, cx + vrx, cy + r)
        canvas.drawOval(vOval, smileyStrokePaint)

        // دو خطِ افقی (مدار)، هرکدوم فقط تا لبه‌ی داخلیِ دایره در همون ارتفاع امتداد داره
        val dy = r * 0.30f
        val halfW = kotlin.math.sqrt((r * r - dy * dy).coerceAtLeast(0f))
        canvas.drawLine(cx - halfW, cy - dy, cx + halfW, cy - dy, smileyStrokePaint)
        canvas.drawLine(cx - halfW, cy + dy, cx + halfW, cy + dy, smileyStrokePaint)
    }

    private fun handleLetterTap(label: String) {
        val replacement = PrefsHelper.getReplacement(context, label)
        val toCommit = if (replacement.isNotBlank()) replacement else label
        flashKey(label)
        listener?.onCommitText(toCommit)
        if (isShiftOn && !isCapsLock) {
            isShiftOn = false
            rebuildKeys(width, height)
            invalidate()
        }
    }

    private fun flashKey(label: String) {
        highlightedLabel = label
        invalidate()
        handler.postDelayed({
            if (highlightedLabel == label) {
                highlightedLabel = null
                invalidate()
            }
        }, 70)
    }

    fun highlightKey(char: String) {
        flashKey(char)
    }
}
