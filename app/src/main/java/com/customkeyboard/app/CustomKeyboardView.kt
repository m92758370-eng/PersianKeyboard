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
    }

    var listener: Listener? = null

    private enum class KeyType { LETTER, SYMBOL, SPACE, BACKSPACE, ENTER, LANG_SWITCH, AUTOTYPE, PAUSE_RESUME, WORD_SHUFFLE, SYMBOLS_TOGGLE, TOOLBAR_MIC, TOOLBAR_TRANSLATE, TOOLBAR_SETTINGS, TOOLBAR_EMOJI, TOOLBAR_CLIPBOARD, TOOLBAR_GRID }
    private enum class KeyboardMode { LETTERS, SYMBOLS, NUMBERS, EMOJI }

    private data class KeyRect(
        val label: String,
        val rect: RectF,
        val type: KeyType,
        val subLabel: String = ""
    )

    companion object {
        private const val SPACE_LONG_PRESS_MS = 2000L
        private const val BACKSPACE_INITIAL_DELAY_MS = 400L
        private const val TOOLBAR_HEIGHT_DP = 42f
    }

    private var usePersian = true
    private val keys = mutableListOf<KeyRect>()
    private var mode = KeyboardMode.LETTERS

    private var editingSpaceLabel = false
    private val spaceLabelBuffer = StringBuilder()

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
    private var lastSpaceUpTime = 0L
    private var spaceDoubleTapCandidate = false
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
                backspaceRepeatCount < 8 -> 90L
                backspaceRepeatCount < 16 -> 60L
                backspaceRepeatCount < 24 -> 40L
                else -> 25L
            }
            handler.postDelayed(this, nextDelay)
        }
    }

    private var langPressed = false
    private var langPointerId = -1
    private var lastLangUpTime = 0L
    private var langDoubleTapCandidate = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightPx = ((230 + TOOLBAR_HEIGHT_DP) * context.resources.displayMetrics.density).toInt()
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
            keyPaint.color = Color.parseColor("#FF353238")
            specialKeyPaint.color = Color.parseColor("#FF3D3238")
            accentPaint.color = Color.parseColor("#FF4A3540")
            textPaint.color = Color.WHITE
            labelPaint.color = Color.parseColor("#BBBBBB")
            overlayPaint.color = Color.parseColor("#66000000")
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

    private fun rebuildKeys(w: Int, h: Int) {
        keys.clear()
        if (w == 0 || h == 0) return

        val toolbarHeight = TOOLBAR_HEIGHT_DP * density
        val toolbarItemW = w / 6f
        for (i in 0 until 6) {
            val left = toolbarItemW * i
            val right = left + toolbarItemW
            val type = when (i) {
                0 -> KeyType.TOOLBAR_MIC
                1 -> KeyType.TOOLBAR_TRANSLATE
                2 -> KeyType.TOOLBAR_SETTINGS
                3 -> KeyType.TOOLBAR_EMOJI
                4 -> KeyType.TOOLBAR_CLIPBOARD
                else -> KeyType.TOOLBAR_GRID
            }
            keys.add(KeyRect("", RectF(left, 0f, right, toolbarHeight), type))
        }

        val contentRows: List<List<String>> = when (mode) {
            KeyboardMode.LETTERS -> if (usePersian) getPersianLetterRows() else KeyboardLayouts.ENGLISH
            KeyboardMode.SYMBOLS -> KeyboardLayouts.SYMBOLS
            KeyboardMode.NUMBERS -> KeyboardLayouts.NUMBERS
            KeyboardMode.EMOJI -> KeyboardLayouts.EMOJI
        }
        val contentKeyType = if (mode == KeyboardMode.LETTERS) KeyType.LETTER else KeyType.SYMBOL

        val keyboardAreaHeight = h - toolbarHeight
        val totalRows = contentRows.size + 1
        rowHeight = keyboardAreaHeight / totalRows

        for ((rowIndex, row) in contentRows.withIndex()) {
            val isLastContentRow = rowIndex == contentRows.size - 1
            val top = toolbarHeight + rowHeight * rowIndex
            val bottom = top + rowHeight

            if (isLastContentRow) {
                val totalCols = row.size + 1
                val itemWidth = w.toFloat() / totalCols
                for ((colIndex, label) in row.withIndex()) {
                    val left = itemWidth * colIndex
                    val right = left + itemWidth
                    keys.add(KeyRect(label, RectF(left, top, right, bottom), contentKeyType))
                }
                val backLeft = itemWidth * row.size
                keys.add(KeyRect("⌫", RectF(backLeft, top, w.toFloat(), bottom), KeyType.BACKSPACE))
            } else {
                val keyWidth = w.toFloat() / row.size
                for ((colIndex, label) in row.withIndex()) {
                    val left = keyWidth * colIndex
                    val right = left + keyWidth
                    val hint = if (mode == KeyboardMode.LETTERS && usePersian && rowIndex == 0) {
                        KeyboardLayouts.PERSIAN_ROW1_DIGIT_HINTS.getOrElse(colIndex) { "" }
                    } else {
                        ""
                    }
                    keys.add(KeyRect(label, RectF(left, top, right, bottom), contentKeyType, hint))
                }
            }
        }

        val bottomTop = toolbarHeight + rowHeight * contentRows.size
        val bottomBottom = bottomTop + rowHeight
        val symbolsToggleW = w * 0.12f
        val autoW = w * 0.14f
        val switchW = w * 0.14f
        val pauseW = w * 0.13f
        val enterW = w * 0.16f
        val spaceW = w - symbolsToggleW - autoW - switchW - pauseW - enterW

        var x = 0f
        keys.add(KeyRect("", RectF(x, bottomTop, x + symbolsToggleW, bottomBottom), KeyType.SYMBOLS_TOGGLE))
        x += symbolsToggleW
        keys.add(KeyRect("", RectF(x, bottomTop, x + autoW, bottomBottom), KeyType.AUTOTYPE))
        x += autoW
        keys.add(KeyRect("", RectF(x, bottomTop, x + switchW, bottomBottom), KeyType.LANG_SWITCH))
        x += switchW
        val spaceLabel = if (mode == KeyboardMode.NUMBERS) "٠" else PrefsHelper.getSpaceLabel(context)
        keys.add(KeyRect(spaceLabel, RectF(x, bottomTop, x + spaceW, bottomBottom), KeyType.SPACE))
        x += spaceW
        keys.add(KeyRect("", RectF(x, bottomTop, x + pauseW, bottomBottom), KeyType.PAUSE_RESUME))
        x += pauseW
        keys.add(KeyRect("⏎", RectF(x, bottomTop, x + enterW, bottomBottom), KeyType.ENTER))
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
                    KeyType.TOOLBAR_EMOJI -> drawSmileyIcon(canvas, key.rect, key.rect.height() * 0.34f)
                    KeyType.TOOLBAR_CLIPBOARD -> drawClipboardIcon(canvas, key.rect)
                    KeyType.TOOLBAR_GRID -> drawGridIcon(canvas, key.rect)
                    else -> {}
                }
                continue
            }
            val paint = when {
                key.label == highlightedLabel -> highlightPaint
                editingSpaceLabel && key.type == KeyType.AUTOTYPE -> accentPaint
                editingSpaceLabel && key.type == KeyType.LANG_SWITCH -> highlightPaint
                key.type == KeyType.ENTER || key.type == KeyType.BACKSPACE -> accentPaint
                key.type == KeyType.LETTER || key.type == KeyType.SYMBOL -> keyPaint
                else -> specialKeyPaint
            }
            val pad = keyPadDp * density
            val corner = keyCornerDp * density
            canvas.drawRoundRect(
                RectF(key.rect.left + pad, key.rect.top + pad, key.rect.right - pad, key.rect.bottom - pad),
                corner, corner, paint
            )
            val cx = key.rect.centerX()
            val cy = key.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2

            if (key.type == KeyType.SYMBOLS_TOGGLE) {
                val label = if (mode == KeyboardMode.LETTERS) "١٢٣" else "حروف"
                canvas.drawText(label, cx, cy, Paint(textPaint).apply { textSize = rowHeight * 0.2f })
            } else if (key.type == KeyType.SPACE) {
                val displayText = if (editingSpaceLabel) spaceLabelBuffer.toString() + "│" else key.label
                val spaceTextPaint = Paint(textPaint).apply {
                    textSize = rowHeight * 0.22f
                }
                canvas.drawText(displayText, cx, cy, spaceTextPaint)
            } else if (key.type == KeyType.PAUSE_RESUME) {
                when {
                    mode == KeyboardMode.SYMBOLS -> canvas.drawText("١٢٣", cx, cy, Paint(textPaint).apply { textSize = rowHeight * 0.2f })
                    mode == KeyboardMode.NUMBERS -> canvas.drawText("؟", cx, cy, Paint(textPaint).apply { textSize = rowHeight * 0.24f })
                    else -> drawArrowIcon(canvas, key.rect)
                }
            } else if (key.type == KeyType.AUTOTYPE) {
                when {
                    editingSpaceLabel -> canvas.drawText("ذخیره", cx, cy, Paint(textPaint).apply { textSize = rowHeight * 0.2f })
                    mode == KeyboardMode.NUMBERS -> canvas.drawText(".", cx, cy, Paint(textPaint).apply { textSize = rowHeight * 0.3f })
                    else -> drawSmileyIcon(canvas, key.rect)
                }
            } else if (key.type == KeyType.LANG_SWITCH) {
                when {
                    editingSpaceLabel -> canvas.drawText("لغو", cx, cy, Paint(textPaint).apply { textSize = rowHeight * 0.2f })
                    mode != KeyboardMode.LETTERS -> canvas.drawText("ابپ", cx, cy, Paint(textPaint).apply { textSize = rowHeight * 0.2f })
                    else -> drawGlobeIcon(canvas, key.rect)
                }
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
                val replacement = PrefsHelper.getReplacement(context, key.label)
                if (replacement.isNotBlank()) {
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
                        if (editingSpaceLabel) {
                            spaceLabelBuffer.append(" ")
                            invalidate()
                        } else {
                            spacePressed = true
                            spacePointerId = pointerId
                            spaceLongPressTriggered = false
                            val now = System.currentTimeMillis()
                            spaceDoubleTapCandidate = (now - lastSpaceUpTime) < 300L
                            handler.postDelayed(spaceLongPressRunnable, SPACE_LONG_PRESS_MS)
                        }
                    }
                    KeyType.BACKSPACE -> {
                        if (editingSpaceLabel) {
                            if (spaceLabelBuffer.isNotEmpty()) {
                                spaceLabelBuffer.deleteCharAt(spaceLabelBuffer.length - 1)
                            }
                            invalidate()
                        } else {
                            backspacePressed = true
                            backspacePointerId = pointerId
                            backspaceRepeatCount = 0
                            listener?.onBackspace()
                            handler.postDelayed(backspaceRunnable, BACKSPACE_INITIAL_DELAY_MS)
                        }
                    }
                    KeyType.LANG_SWITCH -> {
                        if (editingSpaceLabel) {
                            editingSpaceLabel = false
                            invalidate()
                        } else {
                            langPressed = true
                            langPointerId = pointerId
                            val now = System.currentTimeMillis()
                            langDoubleTapCandidate = (now - lastLangUpTime) < 300L
                        }
                    }
                    else -> dispatchKey(key)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (spacePressed && pointerId == spacePointerId) {
                    handler.removeCallbacks(spaceLongPressRunnable)
                    if (!spaceLongPressTriggered) {
                        if (spaceDoubleTapCandidate) {
                            editingSpaceLabel = true
                            spaceLabelBuffer.setLength(0)
                            spaceLabelBuffer.append(PrefsHelper.getSpaceLabel(context))
                            invalidate()
                            lastSpaceUpTime = 0L
                        } else {
                            if (mode == KeyboardMode.NUMBERS) {
                                listener?.onCommitText("٠")
                            } else {
                                listener?.onSpace()
                            }
                            lastSpaceUpTime = System.currentTimeMillis()
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
            }
        }
        return true
    }

    private fun dispatchKey(key: KeyRect) {
        if (editingSpaceLabel) {
            when (key.type) {
                KeyType.LETTER, KeyType.SYMBOL -> {
                    spaceLabelBuffer.append(key.label)
                    flashKey(key.label)
                    invalidate()
                }
                KeyType.AUTOTYPE, KeyType.ENTER -> {
                    val newLabel = spaceLabelBuffer.toString().trim()
                    if (newLabel.isNotEmpty()) {
                        PrefsHelper.setSpaceLabel(context, newLabel)
                    }
                    editingSpaceLabel = false
                    rebuildKeys(width, height)
                    invalidate()
                }
                else -> {}
            }
            return
        }
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
            KeyType.TOOLBAR_MIC, KeyType.TOOLBAR_TRANSLATE, KeyType.TOOLBAR_GRID -> {
                android.widget.Toast.makeText(context, "این بخش هنوز آماده نیست", android.widget.Toast.LENGTH_SHORT).show()
            }
            KeyType.LETTER -> handleLetterTap(key.label)
            KeyType.SYMBOL -> {
                flashKey(key.label)
                listener?.onCommitText(key.label)
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
        val triHeight = rowHeight * 0.16f
        val triWidth = rowHeight * 0.11f
        val gap = rowHeight * 0.14f

        val leftCx = cx - gap
        val leftPath = Path().apply {
            moveTo(leftCx + triWidth * 0.5f, cy - triHeight)
            lineTo(leftCx - triWidth * 0.5f, cy)
            lineTo(leftCx + triWidth * 0.5f, cy + triHeight)
            close()
        }
        canvas.drawPath(leftPath, arrowIconPaint)

        val rightCx = cx + gap
        val rightPath = Path().apply {
            moveTo(rightCx - triWidth * 0.5f, cy - triHeight)
            lineTo(rightCx + triWidth * 0.5f, cy)
            lineTo(rightCx - triWidth * 0.5f, cy + triHeight)
            close()
        }
        canvas.drawPath(rightPath, arrowIconPaint)
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

    private fun drawMicIcon(canvas: Canvas, rect: RectF) {
        smileyStrokePaint.strokeWidth = 1.6f * density
        val cx = rect.centerX()
        val cy = rect.centerY()
        val h = rect.height() * 0.34f
        val bodyW = h * 0.5f
        val bodyRect = RectF(cx - bodyW / 2, cy - h * 0.55f, cx + bodyW / 2, cy + h * 0.15f)
        canvas.drawRoundRect(bodyRect, bodyW / 2, bodyW / 2, smileyStrokePaint)
        val standRect = RectF(cx - h * 0.42f, cy - h * 0.15f, cx + h * 0.42f, cy + h * 0.4f)
        canvas.drawArc(standRect, 0f, 180f, false, smileyStrokePaint)
        canvas.drawLine(cx, cy + h * 0.4f, cx, cy + h * 0.65f, smileyStrokePaint)
        canvas.drawLine(cx - h * 0.3f, cy + h * 0.65f, cx + h * 0.3f, cy + h * 0.65f, smileyStrokePaint)
    }

    private fun drawTranslateIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        val p = Paint(textPaint).apply { textSize = rect.height() * 0.32f }
        canvas.drawText("A/ا", cx, cy, p)
    }

    private fun drawGearIcon(canvas: Canvas, rect: RectF) {
        smileyStrokePaint.strokeWidth = 1.6f * density
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.height() * 0.24f
        canvas.drawCircle(cx, cy, r * 0.45f, smileyStrokePaint)
        canvas.drawCircle(cx, cy, r, smileyStrokePaint)
        val toothLen = r * 0.35f
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45).toDouble())
            val x1 = cx + (r * Math.cos(angle)).toFloat()
            val y1 = cy + (r * Math.sin(angle)).toFloat()
            val x2 = cx + ((r + toothLen) * Math.cos(angle)).toFloat()
            val y2 = cy + ((r + toothLen) * Math.sin(angle)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, smileyStrokePaint)
        }
    }

    private fun drawClipboardIcon(canvas: Canvas, rect: RectF) {
        smileyStrokePaint.strokeWidth = 1.6f * density
        val cx = rect.centerX()
        val cy = rect.centerY()
        val h = rect.height() * 0.34f
        val w = h * 0.75f
        val bodyRect = RectF(cx - w / 2, cy - h * 0.5f, cx + w / 2, cy + h * 0.55f)
        canvas.drawRoundRect(bodyRect, 4f * density, 4f * density, smileyStrokePaint)
        val clipRect = RectF(cx - w * 0.22f, cy - h * 0.68f, cx + w * 0.22f, cy - h * 0.42f)
        canvas.drawRoundRect(clipRect, 3f * density, 3f * density, smileyStrokePaint)
    }

    private fun drawGridIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val s = rect.height() * 0.11f
        val gap = rect.height() * 0.06f
        for (row in -1..0) {
            for (col in -1..0) {
                val left = cx + col * (s + gap) + gap / 2
                val top = cy + row * (s + gap) + gap / 2
                canvas.drawRoundRect(RectF(left, top, left + s, top + s), 2f * density, 2f * density, smileyDotPaint)
            }
        }
    }

    private fun drawGlobeIcon(canvas: Canvas, rect: RectF) {
        smileyStrokePaint.strokeWidth = 1.6f * density
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rowHeight * 0.17f

        canvas.drawCircle(cx, cy, r, smileyStrokePaint)
        canvas.drawLine(cx - r, cy, cx + r, cy, smileyStrokePaint)
        val vOval = RectF(cx - r * 0.42f, cy - r, cx + r * 0.42f, cy + r)
        canvas.drawOval(vOval, smileyStrokePaint)
    }

    private fun handleLetterTap(label: String) {
        val replacement = PrefsHelper.getReplacement(context, label)
        val toCommit = if (replacement.isNotBlank()) replacement else label
        flashKey(label)
        listener?.onCommitText(toCommit)
    }

    private fun flashKey(label: String) {
        highlightedLabel = label
        invalidate()
        handler.postDelayed({
            if (highlightedLabel == label) {
                highlightedLabel = null
                invalidate()
            }
        }, 120)
    }

    fun highlightKey(char: String) {
        flashKey(char)
    }
}
