package com.customkeyboard.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
        fun onAutoTypeButton()
    }

    var listener: Listener? = null

    private enum class KeyType { LETTER, SPACE, BACKSPACE, ENTER, LANG_SWITCH, AUTOTYPE }

    private data class KeyRect(
        val label: String,
        val rect: RectF,
        val type: KeyType
    )

    companion object {
        private const val DOUBLE_TAP_TIMEOUT_MS = 280L
    }

    private var usePersian = true
    private val keys = mutableListOf<KeyRect>()

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
    private var backgroundBitmap: Bitmap? = null
    private var backgroundW = -1
    private var backgroundH = -1

    private val handler = Handler(Looper.getMainLooper())
    private var pendingKeyLabel: String? = null
    private var pendingRunnable: Runnable? = null
    private var highlightedLabel: String? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildKeys(w, h)
        loadBackground(w, h)
    }

    private fun loadBackground(w: Int, h: Int) {
        if (w == 0 || h == 0) return
        if (backgroundBitmap != null && backgroundW == w && backgroundH == h) return
        backgroundW = w
        backgroundH = h

        val resId = resources.getIdentifier("keyboard_background", "drawable", context.packageName)
        backgroundBitmap = if (resId != 0) {
            val original = BitmapFactory.decodeResource(resources, resId)
            centerCrop(original, w, h)
        } else {
            generateGrungeTexture(w, h)
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
        val switchW = w * 0.14f
        val autoW = w * 0.14f
        val backW = w * 0.16f
        val enterW = w * 0.16f
        val spaceW = w - switchW - autoW - backW - enterW

        var x = 0f
        keys.add(KeyRect(if (usePersian) "EN" else "فا", RectF(x, bottomTop, x + switchW, bottomBottom), KeyType.LANG_SWITCH))
        x += switchW
        keys.add(KeyRect("▶", RectF(x, bottomTop, x + autoW, bottomBottom), KeyType.AUTOTYPE))
        x += autoW
        keys.add(KeyRect("␣", RectF(x, bottomTop, x + spaceW, bottomBottom), KeyType.SPACE))
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
        } ?: canvas.drawColor(Color.parseColor("#121212"))

        textPaint.textSize = rowHeight * 0.4f

        for (key in keys) {
            val paint = when {
                key.label == highlightedLabel -> highlightPaint
                key.type == KeyType.ENTER -> accentPaint
                key.type == KeyType.LETTER -> keyPaint
                else -> specialKeyPaint
            }
            val pad = 3f
            canvas.drawRoundRect(
                RectF(key.rect.left + pad, key.rect.top + pad, key.rect.right - pad, key.rect.bottom - pad),
                10f, 10f, paint
            )
            val cx = key.rect.centerX()
            val cy = key.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(key.label, cx, cy, textPaint)

            if (key.type == KeyType.LETTER) {
                val replacement = PrefsHelper.getReplacement(context, key.label)
                if (replacement.isNotEmpty()) {
                    canvas.drawText("•", key.rect.centerX(), key.rect.bottom - 10f, labelPaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        val key = keys.firstOrNull { it.rect.contains(event.x, event.y) } ?: return true

        when (key.type) {
            KeyType.SPACE -> { listener?.onSpace(); clearPending() }
            KeyType.BACKSPACE -> { listener?.onBackspace(); clearPending() }
            KeyType.ENTER -> { listener?.onEnter(); clearPending() }
            KeyType.LANG_SWITCH -> { setLanguage(!usePersian); clearPending() }
            KeyType.AUTOTYPE -> { listener?.onAutoTypeButton(); clearPending() }
            KeyType.LETTER -> handleLetterTap(key.label)
        }
        return true
    }

    private fun handleLetterTap(label: String) {
        if (pendingKeyLabel == label && pendingRunnable != null) {
            handler.removeCallbacks(pendingRunnable!!)
            pendingRunnable = null
            pendingKeyLabel = null
            val replacement = PrefsHelper.getReplacement(context, label)
            val toCommit = if (replacement.isNotEmpty()) replacement else label
            flashKey(label)
            listener?.onCommitText(toCommit)
        } else {
            clearPending()
            pendingKeyLabel = label
            val runnable = Runnable {
                flashKey(label)
                listener?.onCommitText(label)
                pendingRunnable = null
                pendingKeyLabel = null
            }
            pendingRunnable = runnable
            handler.postDelayed(runnable, DOUBLE_TAP_TIMEOUT_MS)
        }
    }

    private fun clearPending() {
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = null
        pendingKeyLabel = null
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
