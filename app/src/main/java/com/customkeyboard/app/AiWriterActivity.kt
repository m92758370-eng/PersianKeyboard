package com.customkeyboard.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class AiWriterActivity : AppCompatActivity() {

    private lateinit var edtApiKey: EditText
    private lateinit var edtStyleNotes: EditText
    private lateinit var edtPrompt: EditText
    private lateinit var edtOutput: EditText
    private lateinit var btnGenerate: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var examplesContainer: LinearLayout

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val MODEL = "gemini-3.6-flash"
        private const val ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_writer)

        if (RemoteStatusHelper.blockIfDisabled(this)) return

        edtApiKey = findViewById(R.id.edtApiKey)
        edtStyleNotes = findViewById(R.id.edtStyleNotes)
        edtPrompt = findViewById(R.id.edtPrompt)
        edtOutput = findViewById(R.id.edtOutput)
        btnGenerate = findViewById(R.id.btnGenerate)
        progressBar = findViewById(R.id.progressBar)
        examplesContainer = findViewById(R.id.examplesContainer)

        edtApiKey.setText(PrefsHelper.getGeminiApiKey(this))
        edtStyleNotes.setText(PrefsHelper.getAiStyleNotes(this))

        findViewById<Button>(R.id.btnSaveApiKey).setOnClickListener {
            PrefsHelper.setGeminiApiKey(this, edtApiKey.text.toString().trim())
            Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnSaveStyleNotes).setOnClickListener {
            PrefsHelper.setAiStyleNotes(this, edtStyleNotes.text.toString())
            Toast.makeText(this, "قوانین سبک ذخیره شد", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnAddExample).setOnClickListener {
            addExampleFromOutput()
        }

        findViewById<Button>(R.id.btnCopyOutput).setOnClickListener {
            copyToClipboard(edtOutput.text.toString())
        }

        findViewById<Button>(R.id.btnTeachFromOutput).setOnClickListener {
            addExampleFromOutput()
        }

        btnGenerate.setOnClickListener {
            generate()
        }

        rebuildExamplesList()
    }

    override fun onDestroy() {
        super.onDestroy()
        PrefsHelper.setAiStyleNotes(this, edtStyleNotes.text.toString())
    }

    private fun rebuildExamplesList() {
        examplesContainer.removeAllViews()
        val examples = PrefsHelper.getAiExamples(this)
        if (examples.isEmpty()) {
            val tv = TextView(this)
            tv.text = "هنوز نمونه‌ای ثبت نشده"
            tv.setTextColor(0xFF888888.toInt())
            examplesContainer.addView(tv)
            return
        }
        examples.forEachIndexed { index, example ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, 8, 0, 8)

            val preview = example.take(40).replace("\n", " ")
            val tv = TextView(this)
            tv.text = "نمونه ${index + 1}: $preview${if (example.length > 40) "..." else ""}"
            tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val btnDelete = Button(this)
            btnDelete.text = "حذف"
            btnDelete.textSize = 12f
            btnDelete.setOnClickListener {
                PrefsHelper.removeAiExampleAt(this, index)
                rebuildExamplesList()
            }

            row.addView(tv)
            row.addView(btnDelete)
            examplesContainer.addView(row)
        }
    }

    private fun addExampleFromOutput() {
        val text = edtOutput.text.toString().trim()
        if (text.isBlank()) {
            Toast.makeText(this, "اول یه متن تولید کن یا بنویس", Toast.LENGTH_SHORT).show()
            return
        }
        PrefsHelper.addAiExample(this, text)
        Toast.makeText(this, "به‌عنوان نمونه ذخیره شد؛ از این به بعد سبکش رو در نظر می‌گیره", Toast.LENGTH_LONG).show()
        rebuildExamplesList()
    }

    private fun copyToClipboard(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, "چیزی برای کپی نیست", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("متن", text))
        Toast.makeText(this, "کپی شد", Toast.LENGTH_SHORT).show()
    }

    private fun generate() {
        val apiKey = edtApiKey.text.toString().trim()
        if (apiKey.isBlank()) {
            Toast.makeText(this, "اول API key جمینای رو وارد و ذخیره کن", Toast.LENGTH_LONG).show()
            return
        }
        val userRequest = edtPrompt.text.toString().trim()
        if (userRequest.isBlank()) {
            Toast.makeText(this, "بنویس چی می‌خوای بنویسه", Toast.LENGTH_SHORT).show()
            return
        }

        val fullPrompt = buildPrompt(userRequest)

        btnGenerate.isEnabled = false
        progressBar.visibility = View.VISIBLE

        executor.execute {
            val result = try {
                callGemini(apiKey, fullPrompt)
            } catch (e: Exception) {
                "خطا: ${e.message}"
            }
            mainHandler.post {
                progressBar.visibility = View.GONE
                btnGenerate.isEnabled = true
                edtOutput.setText(result)
            }
        }
    }

    private fun buildPrompt(userRequest: String): String {
        val sb = StringBuilder()
        sb.append("تو یک دستیار تخصصی داستان‌نویسی و پارت‌نویسی فارسی هستی. ")
        sb.append("فقط در زمینه‌ی نوشتن، ادامه دادن و ویرایش داستان و پارت کمک کن.\n\n")

        val styleNotes = PrefsHelper.getAiStyleNotes(this)
        if (styleNotes.isNotBlank()) {
            sb.append("قوانین و سبک نوشتاری من که باید رعایت کنی:\n")
            sb.append(styleNotes)
            sb.append("\n\n")
        }

        val examples = PrefsHelper.getAiExamples(this)
        if (examples.isNotEmpty()) {
            sb.append("چند نمونه از پارت‌های قبلی من که باید سبک نوشتنم رو از روشون یاد بگیری:\n")
            examples.take(3).forEachIndexed { i, ex ->
                sb.append("--- نمونه ${i + 1} ---\n")
                sb.append(ex)
                sb.append("\n\n")
            }
        }

        sb.append("درخواست الان:\n")
        sb.append(userRequest)
        return sb.toString()
    }

    private fun callGemini(apiKey: String, prompt: String): String {
        val url = URL("$ENDPOINT_BASE$MODEL:generateContent?key=$apiKey")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 60000

        val partObj = JSONObject().put("text", prompt)
        val partsArray = JSONArray().put(partObj)
        val contentObj = JSONObject().put("parts", partsArray)
        val contentsArray = JSONArray().put(contentObj)
        val body = JSONObject().put("contents", contentsArray)

        connection.outputStream.use { os ->
            os.write(body.toString().toByteArray(Charsets.UTF_8))
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        connection.disconnect()

        if (code !in 200..299) {
            return "خطا از سرور ($code): ${extractErrorMessage(responseText)}"
        }

        return extractGeneratedText(responseText)
    }

    private fun extractGeneratedText(responseText: String): String {
        return try {
            val json = JSONObject(responseText)
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return "پاسخی برنگشت. شاید محتوا فیلتر شده باشه."
            }
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val textBuilder = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    textBuilder.append(parts.getJSONObject(i).optString("text", ""))
                }
            }
            if (textBuilder.isBlank()) "پاسخی برنگشت." else textBuilder.toString()
        } catch (e: Exception) {
            "خطا در خوندن پاسخ: ${e.message}"
        }
    }

    private fun extractErrorMessage(responseText: String): String {
        return try {
            val json = JSONObject(responseText)
            json.optJSONObject("error")?.optString("message") ?: responseText.take(200)
        } catch (e: Exception) {
            responseText.take(200)
        }
    }
}
