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
    }

    var listener: Listener? = null

    private enum class KeyType { LETTER, SPACE, BACKSPACE, ENTER, LANG_SWITCH, AUTOTYPE, PAUSE_RESUME, WORD_SHUFFLE }

    private data class KeyRect(
        val label: String,
        val rect: RectF,
        val type: KeyType
    )

    companion object {
        private const val SPACE_LONG_PRESS_MS = 2000L
        private const val BACKSPACE_INITIAL_DELAY_MS = 400L
    }

    private var usePersian = true
    private val keys = mutableListOf<KeyRect>()

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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightPx = (230 * context.resources.displayMetrics.density).toInt()
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
        } else if (PrefsHelper.isDarkMode(context)) {
            generateGrungeTexture(w, h)
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
            keyPaint.color = Color.parseColor("#992A2A2A")
            specialKeyPaint.color = Color.parseColor("#991A1A1A")
            textPaint.color = Color.WHITE
            labelPaint.color = Color.parseColor("#BBBBBB")
            overlayPaint.color = Color.parseColor("#66000000")
            fallbackBgColor = Color.parseColor("#121212")
            arrowIconPaint.color = Color.WHITE
            smileyStrokePaint.color = Color.WHITE
            smileyDotPaint.color = Color.WHITE
        } else {
            keyPaint.color = Color.parseColor("#99FFFFFF")
            specialKeyPaint.color = Color.parseColor("#99DDDDDD")
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

    private fun rebuildKeys(w: Int, h: Int) {
        keys.clear()
        if (w == 0 || h == 0) return

        val letterRows = if (usePersian) KeyboardLayouts.PERSIAN else KeyboardLayouts.ENGLISH
        val totalRows = letterRows.size + 1
        rowHeight = h.toFloat() / totalRows

        for ((rowIndex, row) in letterRows.withIndex()) {
            val keyWidth = w.toFloat() / row.size
            val top = rowHeight * rowIndex
            val bottom = top + rowHeight
            for ((colIndex, label) in row.withIndex()) {
                val left = keyWidth * colIndex
                val right = left + keyWidth
                keys.add(KeyRect(label, RectF(left, top, right, bottom), KeyType.LETTER))
            }
        }

        val bottomTop = rowHeight * letterRows.size
        val bottomBottom = bottomTop + rowHeight
        val switchW = w * 0.13f
        val pauseW = w * 0.12f
        val autoW = w * 0.13f
        val backW = w * 0.15f
        val enterW = w * 0.15f
        val spaceW = w - switchW - pauseW - autoW - backW - enterW

        var x = 0f
        keys.add(KeyRect("", RectF(x, bottomTop, x + switchW, bottomBottom), KeyType.LANG_SWITCH))
        x += switchW
        keys.add(KeyRect("", RectF(x, bottomTop, x + pauseW, bottomBottom), KeyType.PAUSE_RESUME))
        x += pauseW
        keys.add(KeyRect("", RectF(x, bottomTop, x + autoW, bottomBottom), KeyType.AUTOTYPE))
        x += autoW
        keys.add(KeyRect(PrefsHelper.getSpaceLabel(context), RectF(x, bottomTop, x + spaceW, bottomBottom), KeyType.SPACE))
        x += spaceW
        keys.add(KeyRect("⌫", RectF(x, bottomTop, x + backW, bottomBottom), KeyType.BACKSPACE))
        x += backW
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
            val paint = when {
                key.label == highlightedLabel -> highlightPaint
                editingSpaceLabel && key.type == KeyType.AUTOTYPE -> accentPaint
                editingSpaceLabel && key.type == KeyType.LANG_SWITCH -> highlightPaint
                key.type == KeyType.ENTER -> accentPaint
                key.type == KeyType.LETTER -> keyPaint
                else -> specialKeyPaint
            }
            val pad = 4f * density
            val corner = 14f * density
            canvas.drawRoundRect(
                RectF(key.rect.left + pad, key.rect.top + pad, key.rect.right - pad, key.rect.bottom - pad),
                corner, corner, paint
            )
            val cx = key.rect.centerX()
            val cy = key.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2

            if (key.type == KeyType.SPACE) {
                val displayText = if (editingSpaceLabel) spaceLabelBuffer.toString() + "│" else key.label
                val spaceTextPaint = Paint(textPaint).apply {
                    textSize = rowHeight * 0.22f
                }
                canvas.drawText(displayText, cx, cy, spaceTextPaint)
            } else if (key.type == KeyType.PAUSE_RESUME) {
                drawArrowIcon(canvas, key.rect)
            } else if (key.type == KeyType.AUTOTYPE) {
                if (editingSpaceLabel) {
                    val savePaint = Paint(textPaint).apply { textSize = rowHeight * 0.2f }
                    canvas.drawText("ذخیره", cx, cy, savePaint)
                } else {
                    drawSmileyIcon(canvas, key.rect)
                }
            } else if (key.type == KeyType.LANG_SWITCH) {
                if (editingSpaceLabel) {
                    val cancelPaint = Paint(textPaint).apply { textSize = rowHeight * 0.2f }
                    canvas.drawText("لغو", cx, cy, cancelPaint)
                } else {
                    drawGlobeIcon(canvas, key.rect)
                }
            } else {
                canvas.drawText(key.label, cx, cy, textPaint)
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
                            listener?.onSpace()
                            lastSpaceUpTime = System.currentTimeMillis()
                        }
                    }
                    spacePressed = false
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
                KeyType.LETTER -> {
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
                KeyType.LANG_SWITCH -> {
                    editingSpaceLabel = false
                    invalidate()
                }
                else -> {}
            }
            return
        }
        when (key.type) {
            KeyType.SPACE -> listener?.onSpace()
            KeyType.BACKSPACE -> listener?.onBackspace()
            KeyType.ENTER -> listener?.onEnter()
            KeyType.LANG_SWITCH -> setLanguage(!usePersian)
            KeyType.AUTOTYPE -> listener?.onAutoTypeButton()
            KeyType.PAUSE_RESUME -> listener?.onPauseResumeButton()
            KeyType.WORD_SHUFFLE -> listener?.onWordShuffleButton()
            KeyType.LETTER -> handleLetterTap(key.label)
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

    private fun drawSmileyIcon(canvas: Canvas, rect: RectF) {
        smileyStrokePaint.strokeWidth = 1.6f * density
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rowHeight * 0.17f

        canvas.drawCircle(cx, cy, r, smileyStrokePaint)

        val eyeR = r * 0.11f
        val eyeOffsetX = r * 0.38f
        val eyeOffsetY = r * 0.22f
        canvas.drawCircle(cx - eyeOffsetX, cy - eyeOffsetY, eyeR, smileyDotPaint)
        canvas.drawCircle(cx + eyeOffsetX, cy - eyeOffsetY, eyeR, smileyDotPaint)

        val mouthRect = RectF(cx - r * 0.55f, cy - r * 0.35f, cx + r * 0.55f, cy + r * 0.6f)
        canvas.drawArc(mouthRect, 20f, 140f, false, smileyStrokePaint)
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
