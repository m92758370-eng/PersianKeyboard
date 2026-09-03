package com.customkeyboard.app

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
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

class OverlapFinderActivity : AppCompatActivity() {

    private data class WordToken(val text: String, val range: IntRange)
    private data class PartResult(val originalText: String, val tokens: List<WordToken>, val matchedIndices: Set<Int>)
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
            val results = computeOverlaps(texts, minLen)
            mainHandler.post {
                progressBar.visibility = View.GONE
                btnFindOverlap.isEnabled = true
                showResults(results)
            }
        }
    }

    private fun tokenize(text: String): List<WordToken> {
        val tokens = mutableListOf<WordToken>()
        for (match in Regex("\\S+").findAll(text)) {
            tokens.add(WordToken(match.value, match.range))
        }
        return tokens
    }

    private fun computeOverlaps(texts: List<String>, minLen: Int): List<PartResult> {
        val tokenLists = texts.map { tokenize(it) }
        val wordArrays = tokenLists.map { list -> list.map { it.text } }
        val matched = Array(texts.size) { mutableSetOf<Int>() }

        for (i in texts.indices) {
            for (j in i + 1 until texts.size) {
                val a = wordArrays[i]
                val b = wordArrays[j]
                if (a.isEmpty() || b.isEmpty()) continue
                val (matchedA, matchedB) = findMaximalRuns(a, b, minLen)
                matched[i].addAll(matchedA)
                matched[j].addAll(matchedB)
            }
        }

        return texts.indices.map { idx -> PartResult(texts[idx], tokenLists[idx], matched[idx]) }
    }

    private fun findMaximalRuns(a: List<String>, b: List<String>, minLen: Int): Pair<Set<Int>, Set<Int>> {
        val n = a.size
        val m = b.size
        var prevRow = IntArray(m + 1)
        val matchedA = mutableSetOf<Int>()
        val matchedB = mutableSetOf<Int>()

        for (i in 1..n) {
            val currRow = IntArray(m + 1)
            for (j in 1..m) {
                val len = if (a[i - 1] == b[j - 1]) prevRow[j - 1] + 1 else 0
                currRow[j] = len
                if (len > 0) {
                    val extends = i < n && j < m && a[i] == b[j]
                    if (!extends && len >= minLen) {
                        for (k in 0 until len) {
                            matchedA.add(i - 1 - k)
                            matchedB.add(j - 1 - k)
                        }
                    }
                }
            }
            prevRow = currRow
        }

        return matchedA to matchedB
    }

    private fun buildSpannable(result: PartResult): SpannableStringBuilder {
        val builder = SpannableStringBuilder(result.originalText)
        if (result.matchedIndices.isEmpty()) return builder

        fun applySpan(startIdx: Int, endIdx: Int) {
            val startChar = result.tokens[startIdx].range.first
            val endChar = result.tokens[endIdx].range.last + 1
            builder.setSpan(ForegroundColorSpan(Color.RED), startChar, endChar, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(UnderlineSpan(), startChar, endChar, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val sortedIdx = result.matchedIndices.sorted()
        var runStart = sortedIdx[0]
        var prev = sortedIdx[0]
        for (k in 1 until sortedIdx.size) {
            val cur = sortedIdx[k]
            if (cur != prev + 1) {
                applySpan(runStart, prev)
                runStart = cur
            }
            prev = cur
        }
        applySpan(runStart, prev)

        return builder
    }

    private fun showResults(results: List<PartResult>) {
        resultsContainer.removeAllViews()
        val density = resources.displayMetrics.density

        for ((idx, result) in results.withIndex()) {
            val label = TextView(this).apply {
                text = "پارت ${idx + 1}"
                setTypeface(typeface, Typeface.BOLD)
                textSize = 15f
                setPadding(0, 0, 0, (4 * density).toInt())
            }

            val body = TextView(this).apply {
                text = buildSpannable(result)
                setTextIsSelectable(true)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            resultsContainer.addView(label)
            resultsContainer.addView(body)

            if (result.matchedIndices.isEmpty()) {
                val note = TextView(this).apply {
                    text = "بدون اشتراک با پارت‌های دیگه"
                    setTextColor(Color.parseColor("#888888"))
                    textSize = 12f
                    setPadding(0, (4 * density).toInt(), 0, 0)
                }
                resultsContainer.addView(note)
            }

            if (idx != results.lastIndex) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (1 * density).toInt()
                    ).also {
                        it.topMargin = (16 * density).toInt()
                        it.bottomMargin = (16 * density).toInt()
                    }
                    setBackgroundColor(Color.parseColor("#CCCCCC"))
                }
                resultsContainer.addView(divider)
            }
        }
    }
}
