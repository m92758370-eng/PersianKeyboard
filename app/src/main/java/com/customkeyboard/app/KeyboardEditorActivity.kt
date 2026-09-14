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
 * ویرایش‌گر بصری کیبورد: هر حرف فارسی رو می‌شه از گوشه‌ش کشید تا عرض/ارتفاعش عوض بشه،
 * یا خودش رو کشید تا تو همون ردیف جابجا بشه. تغییرات بلافاصله ذخیره می‌شن.
 *
 * محدودیت نسخه‌ی فعلی: فقط حروف فارسی (۳ ردیف اصلی)، و جابجایی فقط داخل همون ردیفه
 * (نه بین ردیف‌های مختلف) — چون سایز ردیف‌ها فعلاً ثابته (۱۱،۱۱،۹).
 */
class KeyboardEditorActivity : AppCompatActivity() {

    private lateinit var rowsContainer: LinearLayout
    private val rowsData: MutableList<MutableList<String>> = mutableListOf()
    private val widthWeights = mutableMapOf<String, Float>()
    private val rowHeightWeights = mutableListOf(1f, 1f, 1f)

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
            widthWeights.clear()
            rowHeightWeights.clear()
            rowHeightWeights.addAll(listOf(1f, 1f, 1f))
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
    }

    private fun persistAll() {
        val flatOrder = rowsData.flatten()
        PrefsHelper.setCustomPersianOrder(this, flatOrder)
        PrefsHelper.setLetterWidthWeights(this, widthWeights)
        PrefsHelper.setPersianRowHeightWeights(this, rowHeightWeights)
    }

    private fun rebuildEditorUI() {
        rowsContainer.removeAllViews()
        for (rowIndex in rowsData.indices) {
            rowsContainer.addView(buildRowView(rowIndex))
        }
    }

    private fun buildRowView(rowIndex: Int): LinearLayout {
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
                        for (i in 0 until rowLayout.childCount) {
                            if (i == currentIndex) continue
                            val sibling = rowLayout.getChildAt(i)
                            if (finalCenterX > sibling.x + sibling.width / 2f) {
                                targetIndex = if (i < currentIndex) i + 1 else i
                            }
                        }
                        targetIndex = targetIndex.coerceIn(0, rowsData[rowIndex].size - 1)
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

    // ---------- کشیدنِ گوشه برای تغییر عرض/ارتفاع ----------
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

                    val heightDelta = dy / (rowsContainer.height / 3f).coerceAtLeast(1f)
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
