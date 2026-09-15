package com.customkeyboard.app

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * ویرایش‌گر بصری کیبورد: دقیقاً همون چیدمانِ کیبورد واقعی رو نشون می‌ده —
 * نوار بالا (میکروفون/ترجمه/تنظیمات/ایموجی/کلیپ‌بورد/شبکه)، سه ردیف حروف فارسی + بک‌اسپیس،
 * و ردیف پایین (؟١٢٣ / اتوتایپ / زبان / فاصله / توقف‌-ادامه / نیم‌فاصله / اینتر).
 *
 * روی هر کلید (هرکدوم از این‌ها) انگشت رو نگه دارید (long-press) تا یه دکمه‌ی «✏️ ویرایش»
 * کنارش ظاهر بشه؛ با زدنش یه ابزار شبیه کراپ‌کردنِ عکس باز می‌شه که با کشیدنِ گوشه‌ش
 * عرض/ارتفاعِ همون کلید رو تنظیم می‌کنید. فقط حروف فارسی رو هم می‌شه با کشیدنِ مستقیمِ
 * خودشون (بدون نگه داشتن) داخل همون ردیف جابجا کرد.
 */
class KeyboardEditorActivity : AppCompatActivity() {

    // شناسه‌ی هر کلیدِ خاص (نوار بالا و ردیف پایین) + لیبل نمایشیِ ساده‌اش تو همین صفحه‌ی ویرایش
    private data class SpecialKey(val id: String, val label: String, val defaultWeight: Float)

    // تعریفِ یه هدفِ قابل‌ویرایش برای ابزار کراپ: از کجا وزنِ فعلیِ عرض/ارتفاع رو بخونه و کجا ذخیره‌ش کنه
    private data class EditTarget(
        val displayLabel: String,
        val widthDefault: Float,
        val getWidthWeight: () -> Float,
        val setWidthWeight: (Float) -> Unit,
        val getHeightWeight: () -> Float,
        val setHeightWeight: (Float) -> Unit
    )

    private val toolbarKeys = listOf(
        SpecialKey("toolbar_mic", "🎤", 1f),
        SpecialKey("toolbar_translate", "🌐", 1f),
        SpecialKey("toolbar_settings", "⚙️", 1f),
        SpecialKey("toolbar_emoji", "🙂", 1f),
        SpecialKey("toolbar_clipboard", "📋", 1f),
        SpecialKey("toolbar_grid", "▦", 1f)
    )

    private val bottomKeys = listOf(
        SpecialKey("symbols_toggle", "؟١٢٣", 12f),
        SpecialKey("autotype", "⌨", 14f),
        SpecialKey("lang_switch", "🌐", 14f),
        SpecialKey("space", "فاصله", 22f),
        SpecialKey("pause_resume", "⏸", 13f),
        SpecialKey("zwnj", "نیم‌فاصله", 9f),
        SpecialKey("enter", "⏎", 16f)
    )

    private lateinit var rowsContainer: LinearLayout
    private val rowsData: MutableList<MutableList<String>> = mutableListOf()
    private val widthWeights = mutableMapOf<String, Float>()   // وزن عرض حروف + بک‌اسپیس (کلید = خودِ حرف یا "⌫")
    private val rowHeightWeights = mutableListOf(1f, 1f, 1f)   // وزن ارتفاع سه ردیف حروف
    private val specialWeights = mutableMapOf<String, Float>() // وزن عرض کلیدهای خاص (نوار بالا + ردیف پایین)
    private var toolbarHeightWeight = 1f
    private var bottomRowHeightWeight = 1f

    private val density get() = resources.displayMetrics.density
    private val minWeight = 0.4f
    private val maxWeight = 3f
    private val longPressTimeoutMs = 450L
    private val handler = Handler(Looper.getMainLooper())
    private var activeOverlay: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keyboard_editor)

        if (RemoteStatusHelper.blockIfDisabled(this)) return

        rowsContainer = findViewById(R.id.rowsContainer)

        loadData()
        rebuildEditorUI()

        findViewById<Button>(R.id.btnResetEditor).setOnClickListener {
            PrefsHelper.resetKeyboardEditorLayout(this)
            loadData()
            rebuildEditorUI()
            Toast.makeText(this, "به حالت پیش‌فرض برگشت", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadData() {
        val order = PrefsHelper.getCustomPersianOrder(this) ?: KeyboardLayouts.PERSIAN.flatten()
        val chunked = KeyboardLayouts.chunkToRows(order, KeyboardLayouts.persianRowSizes())
        rowsData.clear()
        chunked.forEach { rowsData.add(it.toMutableList()) }

        widthWeights.clear()
        widthWeights.putAll(PrefsHelper.getLetterWidthWeights(this))

        rowHeightWeights.clear()
        rowHeightWeights.addAll(PrefsHelper.getPersianRowHeightWeights(this))

        specialWeights.clear()
        specialWeights.putAll(PrefsHelper.getSpecialKeyWidthWeights(this))

        toolbarHeightWeight = PrefsHelper.getToolbarHeightWeight(this)
        bottomRowHeightWeight = PrefsHelper.getBottomRowHeightWeight(this)
    }

    private fun persistAll() {
        val flatOrder = rowsData.flatten()
        PrefsHelper.setCustomPersianOrder(this, flatOrder)
        PrefsHelper.setLetterWidthWeights(this, widthWeights)
        PrefsHelper.setPersianRowHeightWeights(this, rowHeightWeights)
        PrefsHelper.setSpecialKeyWidthWeights(this, specialWeights)
        PrefsHelper.setToolbarHeightWeight(this, toolbarHeightWeight)
        PrefsHelper.setBottomRowHeightWeight(this, bottomRowHeightWeight)
    }

    private fun rebuildEditorUI() {
        dismissOverlay()
        rowsContainer.removeAllViews()

        rowsContainer.addView(buildSpecialRowView(toolbarKeys, isToolbar = true))
        for (rowIndex in rowsData.indices) {
            rowsContainer.addView(buildLetterRowView(rowIndex))
        }
        rowsContainer.addView(buildSpecialRowView(bottomKeys, isToolbar = false))
    }

    // ---------- ردیف نوار بالا / ردیف پایین (فقط قابل ویرایش با نگه‌داشتن، غیرقابل جابجایی) ----------
    private fun buildSpecialRowView(keysList: List<SpecialKey>, isToolbar: Boolean): LinearLayout {
        val heightWeight = if (isToolbar) toolbarHeightWeight else bottomRowHeightWeight
        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, heightWeight
            )
            tag = if (isToolbar) "toolbar" else "bottom"
        }
        for (key in keysList) {
            rowLayout.addView(buildSpecialKeyView(key, isToolbar))
        }
        return rowLayout
    }

    private fun buildSpecialKeyView(key: SpecialKey, isToolbar: Boolean): FrameLayout {
        val weight = specialWeights[key.id] ?: key.defaultWeight

        val keyFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).also {
                it.marginStart = (1.5f * density).toInt()
                it.marginEnd = (1.5f * density).toInt()
                it.topMargin = (1.5f * density).toInt()
                it.bottomMargin = (1.5f * density).toInt()
            }
            setBackgroundColor(Color.parseColor(if (isToolbar) "#1C1C1E" else "#232325"))
        }

        val label = TextView(this).apply {
            text = key.label
            setTextColor(Color.WHITE)
            textSize = if (isToolbar) 14f else 12f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        keyFrame.addView(label)

        attachLongPressOnly(keyFrame) {
            showEditButtonNear(keyFrame, specialEditTarget(key, isToolbar))
        }

        return keyFrame
    }

    private fun specialEditTarget(key: SpecialKey, isToolbar: Boolean): EditTarget = EditTarget(
        displayLabel = key.label,
        widthDefault = key.defaultWeight,
        getWidthWeight = { specialWeights[key.id] ?: key.defaultWeight },
        setWidthWeight = { specialWeights[key.id] = it },
        getHeightWeight = { if (isToolbar) toolbarHeightWeight else bottomRowHeightWeight },
        setHeightWeight = { if (isToolbar) toolbarHeightWeight = it else bottomRowHeightWeight = it }
    )

    // ---------- ردیف‌های حروف فارسی (قابل جابجایی + قابل ویرایش با نگه‌داشتن)؛ ردیف آخر بک‌اسپیس هم داره ----------
    private fun buildLetterRowView(rowIndex: Int): LinearLayout {
        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, rowHeightWeights[rowIndex]
            )
            tag = rowIndex
        }
        for (colIndex in rowsData[rowIndex].indices) {
            rowLayout.addView(buildKeyView(rowIndex, colIndex))
        }
        val isLastRow = rowIndex == rowsData.size - 1
        if (isLastRow) {
            rowLayout.addView(buildBackspaceKeyView(rowIndex))
        }
        return rowLayout
    }

    private fun buildKeyView(rowIndex: Int, colIndex: Int): FrameLayout {
        val letter = rowsData[rowIndex][colIndex]
        val weight = widthWeights[letter] ?: 1f

        val keyFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).also {
                it.marginStart = (1.5f * density).toInt()
                it.marginEnd = (1.5f * density).toInt()
                it.topMargin = (1.5f * density).toInt()
                it.bottomMargin = (1.5f * density).toInt()
            }
            setBackgroundColor(Color.parseColor("#2C2C2E"))
        }

        val label = TextView(this).apply {
            text = letter
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        keyFrame.addView(label)

        setupMoveTouch(keyFrame, rowIndex, letter)

        return keyFrame
    }

    private fun letterEditTarget(rowIndex: Int, letter: String): EditTarget = EditTarget(
        displayLabel = letter,
        widthDefault = 1f,
        getWidthWeight = { widthWeights[letter] ?: 1f },
        setWidthWeight = { widthWeights[letter] = it },
        getHeightWeight = { rowHeightWeights[rowIndex] },
        setHeightWeight = { rowHeightWeights[rowIndex] = it }
    )

    // بک‌اسپیس: فقط قابل ویرایش با نگه‌داشتن (نه جابجایی)، چون همیشه ته ردیف آخره
    private fun buildBackspaceKeyView(rowIndex: Int): FrameLayout {
        val weight = widthWeights["⌫"] ?: 1f

        val keyFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).also {
                it.marginStart = (1.5f * density).toInt()
                it.marginEnd = (1.5f * density).toInt()
                it.topMargin = (1.5f * density).toInt()
                it.bottomMargin = (1.5f * density).toInt()
            }
            setBackgroundColor(Color.parseColor("#3A3A3C"))
        }

        val label = TextView(this).apply {
            text = "⌫"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        keyFrame.addView(label)

        attachLongPressOnly(keyFrame) {
            showEditButtonNear(keyFrame, backspaceEditTarget(rowIndex))
        }

        return keyFrame
    }

    private fun backspaceEditTarget(rowIndex: Int): EditTarget = EditTarget(
        displayLabel = "⌫ بک‌اسپیس",
        widthDefault = 1f,
        getWidthWeight = { widthWeights["⌫"] ?: 1f },
        setWidthWeight = { widthWeights["⌫"] = it },
        getHeightWeight = { rowHeightWeights[rowIndex] },
        setHeightWeight = { rowHeightWeights[rowIndex] = it }
    )

    // ---------- کشیدنِ خود کلید برای جابجایی داخل همون ردیف؛ نگه‌داشتنِ بدون حرکت = نمایشِ دکمه‌ی ویرایش ----------
    private fun setupMoveTouch(keyFrame: FrameLayout, rowIndex: Int, letter: String) {
        var startRawX = 0f
        var moved = false
        val longPressRunnable = Runnable {
            showEditButtonNear(keyFrame, letterEditTarget(rowIndex, letter))
        }

        keyFrame.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    moved = false
                    v.bringToFront()
                    handler.postDelayed(longPressRunnable, longPressTimeoutMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    if (!moved && kotlin.math.abs(dx) > 6 * density) {
                        moved = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    if (moved) v.translationX = dx
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (moved) {
                        val rowLayout = v.parent as LinearLayout
                        val currentIndex = rowLayout.indexOfChild(v)
                        val finalCenterX = v.x + v.translationX + v.width / 2f
                        var targetIndex = 0
                        // بک‌اسپیس همیشه تهِ ردیفه؛ بازه‌ی جابجایی رو به تعداد حروف محدود می‌کنیم
                        val letterCount = rowsData[rowIndex].size
                        for (i in 0 until minOf(rowLayout.childCount, letterCount)) {
                            if (i == currentIndex) continue
                            val sibling = rowLayout.getChildAt(i)
                            if (finalCenterX > sibling.x + sibling.width / 2f) {
                                targetIndex = if (i < currentIndex) i + 1 else i
                            }
                        }
                        targetIndex = targetIndex.coerceIn(0, letterCount - 1)
                        v.translationX = 0f
                        if (targetIndex != currentIndex) {
                            val movedLetter = rowsData[rowIndex].removeAt(currentIndex)
                            rowsData[rowIndex].add(targetIndex, movedLetter)
                            persistAll()
                            rebuildEditorUI()
                        }
                    } else {
                        v.translationX = 0f
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ---------- نگه‌داشتنِ ساده (بدون قابلیت جابجایی) برای بک‌اسپیس و کلیدهای خاص ----------
    private fun attachLongPressOnly(view: View, onLongPress: () -> Unit) {
        val longPressRunnable = Runnable { onLongPress() }
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handler.postDelayed(longPressRunnable, longPressTimeoutMs)
                    true
                }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }
    }

    // ---------- دکمه‌ی شناور «✏️ ویرایش» که بعد از نگه‌داشتنِ یه کلید، کنارش ظاهر می‌شه ----------
    private fun dismissOverlay() {
        activeOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        activeOverlay = null
    }

    private fun showEditButtonNear(anchor: View, target: EditTarget) {
        dismissOverlay()
        val contentRoot = window.decorView.findViewById<ViewGroup>(android.R.id.content)

        val overlay = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val scrim = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setOnClickListener { dismissOverlay() }
        }
        overlay.addView(scrim)

        val anchorLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        val rootLoc = IntArray(2)
        contentRoot.getLocationOnScreen(rootLoc)
        val anchorLocalX = anchorLoc[0] - rootLoc[0]
        val anchorLocalY = anchorLoc[1] - rootLoc[1]

        val button = TextView(this).apply {
            text = "✏️ ویرایش"
            setTextColor(Color.WHITE)
            textSize = 13f
            setBackgroundColor(Color.parseColor("#4A90E2"))
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            setOnClickListener {
                dismissOverlay()
                openCropTool(target)
            }
        }
        val btnLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        btnLp.leftMargin = (anchorLocalX + anchor.width / 2 - 42 * density).toInt().coerceAtLeast((4 * density).toInt())
        btnLp.topMargin = (anchorLocalY - 44 * density).toInt().coerceAtLeast((4 * density).toInt())
        overlay.addView(button, btnLp)

        contentRoot.addView(overlay)
        activeOverlay = overlay
    }

    // ---------- ابزار کراپ: با کشیدنِ گوشه‌ی پایین-راستِ جعبه، عرض/ارتفاعِ کلید رو تنظیم می‌کنه ----------
    private fun openCropTool(target: EditTarget) {
        dismissOverlay()
        val contentRoot = window.decorView.findViewById<ViewGroup>(android.R.id.content)

        val overlay = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#CC000000"))
            isClickable = true
            isFocusable = true
            setOnTouchListener { _, _ -> true } // نذاره لمس از زیرِ کارت رد بشه و بره رو کلیدهای کیبورد پشتش
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1C1C1E"))
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            isClickable = true
        }
        val cardLp = FrameLayout.LayoutParams((300 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        cardLp.gravity = Gravity.CENTER

        val title = TextView(this).apply {
            text = "ویرایش «${target.displayLabel}»"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(0, 0, 0, (4 * density).toInt())
        }
        card.addView(title)

        val hint = TextView(this).apply {
            text = "مربعِ آبی رو از گوشه‌ی پایین-راستش (نقطه‌ی سفید) بکش"
            setTextColor(Color.parseColor("#8A8A8E"))
            textSize = 11f
            setPadding(0, 0, 0, (10 * density).toInt())
        }
        card.addView(hint)

        val canvasWpx = (240 * density).toInt()
        val canvasHpx = (150 * density).toInt()
        val canvas = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        card.addView(canvas, LinearLayout.LayoutParams(canvasWpx, canvasHpx).also { it.gravity = Gravity.CENTER_HORIZONTAL })

        val minBoxPx = 30f * density
        val maxBoxWpx = canvasWpx - 20f * density
        val maxBoxHpx = canvasHpx - 20f * density

        val widthMin = minWeight * target.widthDefault
        val widthMax = maxWeight * target.widthDefault
        val heightMin = minWeight
        val heightMax = maxWeight

        var curWidthWeight = target.getWidthWeight().coerceIn(widthMin, widthMax)
        var curHeightWeight = target.getHeightWeight().coerceIn(heightMin, heightMax)

        fun weightToPx(weight: Float, wMin: Float, wMax: Float, maxPx: Float): Float {
            val t = ((weight - wMin) / (wMax - wMin)).coerceIn(0f, 1f)
            return minBoxPx + t * (maxPx - minBoxPx)
        }
        fun pxToWeight(px: Float, wMin: Float, wMax: Float, maxPx: Float): Float {
            val t = ((px - minBoxPx) / (maxPx - minBoxPx)).coerceIn(0f, 1f)
            return wMin + t * (wMax - wMin)
        }

        val box = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#4A90E2"))
        }
        val boxLabel = TextView(this).apply {
            text = target.displayLabel
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        box.addView(boxLabel)

        var curWidthPx = weightToPx(curWidthWeight, widthMin, widthMax, maxBoxWpx)
        var curHeightPx = weightToPx(curHeightWeight, heightMin, heightMax, maxBoxHpx)

        val boxLp = FrameLayout.LayoutParams(curWidthPx.toInt(), curHeightPx.toInt())
        boxLp.gravity = Gravity.TOP or Gravity.START
        boxLp.leftMargin = (10 * density).toInt()
        boxLp.topMargin = (10 * density).toInt()
        canvas.addView(box, boxLp)

        // دستگیره‌ی بزرگ و واضح، گوشه‌ی پایین-راستِ جعبه (خودِ جعبه همون شکلِ کلیده)
        val cornerHandle = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }
        val cornerMark = View(this).apply { setBackgroundColor(Color.parseColor("#4A90E2")) }
        cornerHandle.addView(cornerMark, FrameLayout.LayoutParams((14 * density).toInt(), (14 * density).toInt()).also {
            it.gravity = Gravity.CENTER
        })
        box.addView(cornerHandle, FrameLayout.LayoutParams((34 * density).toInt(), (34 * density).toInt()).also {
            it.gravity = Gravity.BOTTOM or Gravity.END
        })

        val readout = TextView(this).apply {
            setTextColor(Color.parseColor("#BBBBBB"))
            textSize = 12f
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        fun updateReadout() {
            val wPct = ((curWidthWeight / target.widthDefault) * 100).toInt()
            val hPct = (curHeightWeight * 100).toInt()
            readout.text = "عرض: $wPct٪   ارتفاع: $hPct٪"
        }
        updateReadout()
        card.addView(readout)

        var lastRawX = 0f
        var lastRawY = 0f
        cornerHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastRawX
                    val dy = event.rawY - lastRawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY

                    curWidthPx = (curWidthPx + dx).coerceIn(minBoxPx, maxBoxWpx)
                    curHeightPx = (curHeightPx + dy).coerceIn(minBoxPx, maxBoxHpx)
                    curWidthWeight = pxToWeight(curWidthPx, widthMin, widthMax, maxBoxWpx)
                    curHeightWeight = pxToWeight(curHeightPx, heightMin, heightMax, maxBoxHpx)

                    val lp = box.layoutParams as FrameLayout.LayoutParams
                    lp.width = curWidthPx.toInt()
                    lp.height = curHeightPx.toInt()
                    box.layoutParams = lp
                    updateReadout()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (14 * density).toInt(), 0, 0)
        }
        val cancelBtn = TextView(this).apply {
            text = "انصراف"
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { dismissOverlay() }
        }
        val applyBtn = TextView(this).apply {
            text = "اعمال"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4A90E2"))
            gravity = Gravity.CENTER
            setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                target.setWidthWeight(curWidthWeight)
                target.setHeightWeight(curHeightWeight)
                persistAll()
                dismissOverlay()
                rebuildEditorUI()
            }
        }
        buttonsRow.addView(cancelBtn)
        buttonsRow.addView(applyBtn)
        card.addView(buttonsRow)

        overlay.addView(card, cardLp)
        contentRoot.addView(overlay)
        activeOverlay = overlay
    }
}
