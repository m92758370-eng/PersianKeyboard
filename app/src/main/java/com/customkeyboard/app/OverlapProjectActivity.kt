package com.customkeyboard.app

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * صفحه‌ی چت‌مانند یه پروژه‌ی الصاق‌گیر: پارت‌ها یکی‌یکی مثل پیام اضافه می‌شن،
 * و هر وقت خواستی دکمه‌ی «پیدا کن» عبارت‌های مشترک بین همه‌ی پارت‌های تا اون لحظه رو نشون می‌ده.
 */
class OverlapProjectActivity : AppCompatActivity() {

    private var projectId: Long = -1L
    private var projectName: String = ""
    private val parts = mutableListOf<String>()

    private lateinit var txtProjectName: TextView
    private lateinit var chatContainer: LinearLayout
    private lateinit var edtPartInput: EditText
    private lateinit var edtMinLength: EditText
    private lateinit var btnFindOverlap: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var scrollRoot: ScrollView
    private lateinit var resultsHeading: TextView
    private lateinit var resultsDivider: View
    private lateinit var resultsContainer: LinearLayout

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overlap_project)

        if (RemoteStatusHelper.blockIfDisabled(this)) return

        projectId = intent.getLongExtra("projectId", -1L)

        txtProjectName = findViewById(R.id.txtProjectName)
        chatContainer = findViewById(R.id.chatContainer)
        edtPartInput = findViewById(R.id.edtPartInput)
        edtMinLength = findViewById(R.id.edtMinLength)
        btnFindOverlap = findViewById(R.id.btnFindOverlap)
        progressBar = findViewById(R.id.progressBarOverlap)
        scrollRoot = findViewById(R.id.scrollRoot)
        resultsHeading = findViewById(R.id.resultsHeading)
        resultsDivider = findViewById(R.id.resultsDivider)
        resultsContainer = findViewById(R.id.resultsContainer)

        loadProject()
        rebuildChatUI()

        findViewById<Button>(R.id.btnNextPart).setOnClickListener {
            val text = edtPartInput.text.toString()
            if (text.isBlank()) {
                Toast.makeText(this, "یه متن بنویس", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            parts.add(text)
            persistProject()
            addChatBubble(parts.size, text)
            edtPartInput.setText("")
            scrollRoot.post { scrollRoot.fullScroll(View.FOCUS_DOWN) }
        }

        btnFindOverlap.setOnClickListener {
            runOverlapDetection()
        }
    }

    override fun onPause() {
        super.onPause()
        persistProject()
    }

    // ---------- ذخیره‌سازی پروژه ----------

    private fun loadAllProjects(): JSONArray = try {
        JSONArray(PrefsHelper.getOverlapProjectsJson(this))
    } catch (e: Exception) {
        JSONArray()
    }

    private fun saveAllProjects(arr: JSONArray) {
        PrefsHelper.saveOverlapProjectsJson(this, arr.toString())
    }

    private fun loadProject() {
        val arr = loadAllProjects()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getLong("id") == projectId) {
                projectName = obj.getString("name")
                val partsArr = obj.getJSONArray("parts")
                for (j in 0 until partsArr.length()) parts.add(partsArr.getString(j))
                break
            }
        }
        txtProjectName.text = projectName
    }

    private fun persistProject() {
        val arr = loadAllProjects()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getLong("id") == projectId) {
                val partsArr = JSONArray()
                parts.forEach { partsArr.put(it) }
                obj.put("parts", partsArr)
                break
            }
        }
        saveAllProjects(arr)
    }

    // ---------- رابط چت ----------

    private fun rebuildChatUI() {
        chatContainer.removeAllViews()
        for ((idx, text) in parts.withIndex()) {
            addChatBubble(idx + 1, text)
        }
        scrollRoot.post { scrollRoot.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addChatBubble(index: Int, text: String) {
        val density = resources.displayMetrics.density
        val bubble = TextView(this).apply {
            this.text = "پارت $index\n$text"
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            setBackgroundColor(Color.parseColor("#252525"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (10 * density).toInt() }
            setTextIsSelectable(true)
        }
        chatContainer.addView(bubble)
    }

    // ---------- تشخیص الصاق (مثل قبل) ----------

    private fun runOverlapDetection() {
        val minLen = edtMinLength.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 3
        val nonBlankCount = parts.count { it.isNotBlank() }
        if (nonBlankCount < 2) {
            Toast.makeText(this, "حداقل ۲ پارت لازمه", Toast.LENGTH_SHORT).show()
            return
        }

        btnFindOverlap.isEnabled = false
        progressBar.visibility = View.VISIBLE

        val textsSnapshot = parts.toList()
        executor.execute {
            val phrases = computeOverlaps(textsSnapshot, minLen)
            mainHandler.post {
                progressBar.visibility = View.GONE
                btnFindOverlap.isEnabled = true
                showResults(phrases)
                scrollRoot.post { scrollRoot.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    private fun tokenize(text: String): List<String> =
        Regex("[^\\s\u200c]+").findAll(text).map { it.value }.toList()

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
        resultsHeading.visibility = View.VISIBLE
        resultsDivider.visibility = View.VISIBLE
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
                setTypeface(typeface, android.graphics.Typeface.BOLD)
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
