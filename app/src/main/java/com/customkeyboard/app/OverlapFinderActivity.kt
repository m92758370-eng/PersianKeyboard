package com.customkeyboard.app

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

/**
 * «الصاق‌گیر»: چند پارت متن گرفته می‌شه و فقط اون دنباله‌های کلمه‌ای که
 * عیناً بین حداقل دو تا از پارت‌ها مشترک باشن (حتی اگه جمله‌ی کامل نباشن)
 * به‌صورت یه لیست تک و بدون تکرار (بدون نمایش کل متن هر پارت) برگردونده می‌شه.
 */
class OverlapFinderActivity : AppCompatActivity() {

    private data class PartRow(val container: LinearLayout, val labelView: TextView, val editText: EditText)

    private val partRows = mutableListOf<PartRow>()

    private lateinit var partsContainer: LinearLayout
    private lateinit var resultsContainer: LinearLayout
    private lateinit var edtMinLength: EditText
    private lateinit var btnFindOverlap: Button
    private lateinit var progressBar: ProgressBar

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overlap_finder)

        if (RemoteStatusHelper.blockIfDisabled(this)) return

        partsContainer = findViewById(R.id.partsContainer)
        resultsContainer = findViewById(R.id.resultsContainer)
        edtMinLength = findViewById(R.id.edtMinLength)
        btnFindOverlap = findViewById(R.id.btnFindOverlap)
        progressBar = findViewById(R.id.progressBarOverlap)

        addPartRow()
        addPartRow()

        findViewById<Button>(R.id.btnAddPart).setOnClickListener {
            addPartRow()
        }

        btnFindOverlap.setOnClickListener {
            runOverlapDetection()
        }
    }

    private fun addPartRow(initialText: String = "") {
        val density = resources.displayMetrics.density

        val rowContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (16 * density).toInt() }
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val label = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setTypeface(typeface, Typeface.BOLD)
        }

        val btnRemove = Button(this).apply {
            text = "حذف"
            textSize = 12f
            setOnClickListener {
                partsContainer.removeView(rowContainer)
                partRows.removeAll { it.container == rowContainer }
                relabelRows()
            }
        }

        headerRow.addView(label)
        headerRow.addView(btnRemove)

        val editText = EditText(this).apply {
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            hint = "متن این پارت رو اینجا بچسبون..."
            setText(initialText)
        }

        rowContainer.addView(headerRow)
        rowContainer.addView(editText)
        partsContainer.addView(rowContainer)

        partRows.add(PartRow(rowContainer, label, editText))
        relabelRows()
    }

    private fun relabelRows() {
        for ((index, row) in partRows.withIndex()) {
            row.labelView.text = "پارت ${index + 1}"
        }
    }

    private fun runOverlapDetection() {
        val minLen = edtMinLength.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 3
        val texts = partRows.map { it.editText.text.toString() }
        val nonBlankCount = texts.count { it.isNotBlank() }
        if (nonBlankCount < 2) {
            Toast.makeText(this, "حداقل ۲ پارت با متن لازمه", Toast.LENGTH_SHORT).show()
            return
        }

        btnFindOverlap.isEnabled = false
        progressBar.visibility = View.VISIBLE

        executor.execute {
            val phrases = computeOverlaps(texts, minLen)
            mainHandler.post {
                progressBar.visibility = View.GONE
                btnFindOverlap.isEnabled = true
                showResults(phrases)
            }
        }
    }

    private fun tokenize(text: String): List<String> =
        Regex("\\S+").findAll(text).map { it.value }.toList()

    // بین همه‌ی جفت‌پارت‌ها می‌گرده و فقط خودِ عبارت‌های مشترک رو (بدون تکرار) برمی‌گردونه
    private fun computeOverlaps(texts: List<String>, minLen: Int): List<String> {
        val wordArrays = texts.map { tokenize(it) }
        val phrases = LinkedHashSet<String>()

        for (i in texts.indices) {
            for (j in i + 1 until texts.size) {
                val a = wordArrays[i]
                val b = wordArrays[j]
                if (a.isEmpty() || b.isEmpty()) continue
                collectMaximalRunPhrases(a, b, minLen, phrases)
            }
        }

        return phrases.toList()
    }

    // پیدا کردن همه‌ی دنباله‌های حداکثریِ کلمات مشترک (پشت‌سرهم) بین دو لیست کلمه،
    // با یه DP شبیه LCS ولی فقط برای دنباله‌های پیوسته (نه هر جفت مشترک پراکنده)
    private fun collectMaximalRunPhrases(a: List<String>, b: List<String>, minLen: Int, out: MutableSet<String>) {
        val n = a.size
        val m = b.size
        var prevRow = IntArray(m + 1)

        for (i in 1..n) {
            val currRow = IntArray(m + 1)
            for (j in 1..m) {
                val len = if (a[i - 1] == b[j - 1]) prevRow[j - 1] + 1 else 0
                currRow[j] = len
                if (len > 0) {
                    val extends = i < n && j < m && a[i] == b[j]
                    if (!extends && len >= minLen) {
                        out.add(a.subList(i - len, i).joinToString(" "))
                    }
                }
            }
            prevRow = currRow
        }
    }

    private fun underlinedRed(text: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder(text)
        builder.setSpan(ForegroundColorSpan(Color.RED), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(UnderlineSpan(), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return builder
    }

    private fun showResults(phrases: List<String>) {
        resultsContainer.removeAllViews()
        val density = resources.displayMetrics.density

        if (phrases.isEmpty()) {
            val note = TextView(this).apply {
                text = "هیچ عبارت مشترکی پیدا نشد"
                setTextColor(Color.parseColor("#888888"))
            }
            resultsContainer.addView(note)
            return
        }

        for ((idx, phrase) in phrases.withIndex()) {
            val numberLabel = TextView(this).apply {
                text = "${idx + 1}."
                setTypeface(typeface, Typeface.BOLD)
                textSize = 13f
                setTextColor(Color.parseColor("#888888"))
            }

            val body = TextView(this).apply {
                text = underlinedRed(phrase)
                setTextIsSelectable(true)
                textSize = 15f
                setPadding(0, (2 * density).toInt(), 0, (10 * density).toInt())
            }

            resultsContainer.addView(numberLabel)
            resultsContainer.addView(body)
        }
    }
}
