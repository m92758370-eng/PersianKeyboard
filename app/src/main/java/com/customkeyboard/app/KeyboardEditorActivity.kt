package com.customkeyboard.app

import android.graphics.Color
import android.os.Bundle
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
 * روی هر کلید (هرکدوم از این‌ها) می‌شه از گوشه‌ی پایین-راستش کشید تا عرض/ارتفاعش
 * تغییر کنه (شبیه کراپ کردن). فقط حروف فارسی رو هم می‌شه با کشیدنِ خودشون
 * داخل همون ردیف جابجا کرد؛ بقیه‌ی کلیدها (نوار بالا، ردیف پایین، بک‌اسپیس) فقط
 * قابل تغییر سایزن، چون جاشون تو کیبورد ثابته.
 */
class KeyboardEditorActivity : AppCompatActivity() {

    // شناسه‌ی هر کلیدِ خاص (نوار بالا و ردیف پایین) + لیبل نمایشیِ ساده‌اش تو همین صفحه‌ی ویرایش
    private data class SpecialKey(val id: String, val label: String, val defaultWeight: Float)

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
    private val handleSizePx get() = 22f * density
    private val minWeight = 0.4f
    private val maxWeight = 3f

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
        rowsContainer.removeAllViews()

        // ردیف نوار بالا
        rowsContainer.addView(buildSpecialRowView(toolbarKeys, isToolbar = true))

        // سه ردیف حروف فارسی (ردیف آخر بک‌اسپیس هم داره)
        for (rowIndex in rowsData.indices) {
            rowsContainer.addView(buildLetterRowView(rowIndex))
        }

        // ردیف پایین
        rowsContainer.addView(buildSpecialRowView(bottomKeys, isToolbar = false))
    }

    // ---------- ردیف نوار بالا / ردیف پایین (فقط قابل تغییر سایز، غیرقابل جابجایی) ----------
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

        val handle = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(handleSizePx.toInt(), handleSizePx.toInt()).also {
                it.gravity = Gravity.BOTTOM or Gravity.END
            }
            setBackgroundColor(Color.parseColor("#7A7A7A"))
        }
        keyFrame.addView(handle)

        setupSpecialResizeTouch(handle, keyFrame, key, isToolbar)

        return keyFrame
    }

    // ---------- کشیدنِ گوشه برای تغییر عرض/ارتفاعِ کلیدهای خاص ----------
    private fun setupSpecialResizeTouch(handle: View, keyFrame: FrameLayout, key: SpecialKey, isToolbar: Boolean) {
        var lastRawX = 0f
        var lastRawY = 0f

        handle.setOnTouchListener { _, event ->
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

                    val rowLayout = keyFrame.parent as LinearLayout
                    val itemCount = rowLayout.childCount.coerceAtLeast(1)
                    val widthDelta = dx / (resources.displayMetrics.widthPixels / itemCount.toFloat())
                    val currentWeight = specialWeights[key.id] ?: key.defaultWeight
                    val newWeight = (currentWeight + widthDelta)
                        .coerceIn(minWeight * key.defaultWeight, maxWeight * key.defaultWeight)
                    specialWeights[key.id] = newWeight
                    (keyFrame.layoutParams as LinearLayout.LayoutParams).weight = newWeight
                    keyFrame.requestLayout()

                    val heightDelta = dy / (rowsContainer.height / 5f).coerceAtLeast(1f)
                    if (isToolbar) {
                        toolbarHeightWeight = (toolbarHeightWeight + heightDelta).coerceIn(minWeight, maxWeight)
                        (rowLayout.layoutParams as LinearLayout.LayoutParams).weight = toolbarHeightWeight
                    } else {
                        bottomRowHeightWeight = (bottomRowHeightWeight + heightDelta).coerceIn(minWeight, maxWeight)
                        (rowLayout.layoutParams as LinearLayout.LayoutParams).weight = bottomRowHeightWeight
                    }
                    rowLayout.requestLayout()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    persistAll()
                    true
                }
                else -> false
            }
        }
    }

    // ---------- ردیف‌های حروف فارسی (قابل جابجایی + تغییر سایز)؛ ردیف آخر بک‌اسپیس هم داره ----------
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

        val handle = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(handleSizePx.toInt(), handleSizePx.toInt()).also {
                it.gravity = Gravity.BOTTOM or Gravity.END
            }
            setBackgroundColor(Color.parseColor("#7A7A7A"))
        }
        keyFrame.addView(handle)

        setupMoveTouch(keyFrame, rowIndex, colIndex)
        setupResizeTouch(handle, keyFrame, rowIndex, letter)

        return keyFrame
    }

    // بک‌اسپیس: فقط قابل تغییر سایزه (نه جابجایی)، چون همیشه ته ردیف آخره
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

        val handle = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(handleSizePx.toInt(), handleSizePx.toInt()).also {
                it.gravity = Gravity.BOTTOM or Gravity.END
            }
            setBackgroundColor(Color.parseColor("#7A7A7A"))
        }
        keyFrame.addView(handle)

        setupResizeTouch(handle, keyFrame, rowIndex, "⌫")

        return keyFrame
    }

    // ---------- کشیدنِ خود کلید برای جابجایی داخل همون ردیف ----------
    private fun setupMoveTouch(keyFrame: FrameLayout, rowIndex: Int, colIndexAtBind: Int) {
        var startRawX = 0f
        var moved = false

        keyFrame.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    moved = false
                    v.bringToFront()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    if (kotlin.math.abs(dx) > 6 * density) moved = true
                    v.translationX = dx
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
                            val letter = rowsData[rowIndex].removeAt(currentIndex)
                            rowsData[rowIndex].add(targetIndex, letter)
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

    // ---------- کشیدنِ گوشه برای تغییر عرض/ارتفاعِ حروف و بک‌اسپیس ----------
    private fun setupResizeTouch(handle: View, keyFrame: FrameLayout, rowIndex: Int, letter: String) {
        var lastRawX = 0f
        var lastRawY = 0f

        handle.setOnTouchListener { _, event ->
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

                    val widthDelta = dx / (resources.displayMetrics.widthPixels / 11f)
                    val newWidthWeight = ((widthWeights[letter] ?: 1f) + widthDelta).coerceIn(minWeight, maxWeight)
                    widthWeights[letter] = newWidthWeight
                    (keyFrame.layoutParams as LinearLayout.LayoutParams).weight = newWidthWeight
                    keyFrame.requestLayout()

                    val heightDelta = dy / (rowsContainer.height / 5f).coerceAtLeast(1f)
                    val newRowHeightWeight = (rowHeightWeights[rowIndex] + heightDelta).coerceIn(minWeight, maxWeight)
                    rowHeightWeights[rowIndex] = newRowHeightWeight
                    val rowLayout = keyFrame.parent as LinearLayout
                    (rowLayout.layoutParams as LinearLayout.LayoutParams).weight = newRowHeightWeight
                    rowLayout.requestLayout()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    persistAll()
                    true
                }
                else -> false
            }
        }
    }
}
