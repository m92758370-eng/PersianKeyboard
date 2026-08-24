package com.customkeyboard.app

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class AiWriterActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var edtPrompt: EditText
    private lateinit var btnSend: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ChatAdapter

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val MODEL = "gemini-3.6-flash"
        private const val ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
        private const val SYSTEM_INTRO =
            "تو یک دستیار تخصصی داستان‌نویسی و پارت‌نویسی فارسی هستی. فقط در زمینه‌ی نوشتن، ادامه دادن و ویرایش داستان و پارت کمک کن."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_writer)

        if (RemoteStatusHelper.blockIfDisabled(this)) return

        recyclerView = findViewById(R.id.messagesRecyclerView)
        edtPrompt = findViewById(R.id.edtPrompt)
        btnSend = findViewById(R.id.btnSend)
        progressBar = findViewById(R.id.progressBar)

        val history = PrefsHelper.getChatHistory(this).map { ChatMessage(it.first, it.second) }
        adapter = ChatAdapter(history.toMutableList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        scrollToBottom()

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            showSettingsDialog()
        }

        findViewById<Button>(R.id.btnClearChat).setOnClickListener {
            confirmClearChat()
        }

        btnSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) {
            recyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun showSettingsDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_ai_settings, null)
        val edtApiKey = view.findViewById<EditText>(R.id.edtApiKey)
        val edtStyleNotes = view.findViewById<EditText>(R.id.edtStyleNotes)

        edtApiKey.setText(PrefsHelper.getGeminiApiKey(this))
        edtStyleNotes.setText(PrefsHelper.getAiStyleNotes(this))

        AlertDialog.Builder(this)
            .setTitle("تنظیمات")
            .setView(view)
            .setPositiveButton("ذخیره") { _, _ ->
                PrefsHelper.setGeminiApiKey(this, edtApiKey.text.toString().trim())
                PrefsHelper.setAiStyleNotes(this, edtStyleNotes.text.toString())
                Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun confirmClearChat() {
        AlertDialog.Builder(this)
            .setTitle("پاک کردن مکالمه")
            .setMessage("کل مکالمه پاک می‌شه و دیگه قابل بازگشت نیست. مطمئنی؟")
            .setPositiveButton("پاک کن") { _, _ ->
                PrefsHelper.clearChatHistory(this)
                adapter.clear()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun sendMessage() {
        val apiKey = PrefsHelper.getGeminiApiKey(this)
        if (apiKey.isBlank()) {
            Toast.makeText(this, "اول از تنظیمات، API key رو وارد و ذخیره کن", Toast.LENGTH_LONG).show()
            return
        }
        val text = edtPrompt.text.toString().trim()
        if (text.isBlank()) return

        val userMessage = ChatMessage("user", text)
        adapter.addMessage(userMessage)
        PrefsHelper.addChatMessage(this, "user", text)
        edtPrompt.setText("")
        scrollToBottom()

        btnSend.isEnabled = false
        progressBar.visibility = View.VISIBLE

        val historySnapshot = PrefsHelper.getChatHistory(this)

        executor.execute {
            val result = try {
                callGemini(apiKey, historySnapshot)
            } catch (e: Exception) {
                "خطا: ${e.message}"
            }
            mainHandler.post {
                progressBar.visibility = View.GONE
                btnSend.isEnabled = true
                val aiMessage = ChatMessage("model", result)
                adapter.addMessage(aiMessage)
                PrefsHelper.addChatMessage(this, "model", result)
                scrollToBottom()
            }
        }
    }

    private fun callGemini(apiKey: String, history: List<Pair<String, String>>): String {
        val url = URL("$ENDPOINT_BASE$MODEL:generateContent?key=$apiKey")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 60000

        val contentsArray = JSONArray()
        for ((role, text) in history) {
            val partObj = JSONObject().put("text", text)
            val contentObj = JSONObject()
                .put("role", role)
                .put("parts", JSONArray().put(partObj))
            contentsArray.put(contentObj)
        }

        val styleNotes = PrefsHelper.getAiStyleNotes(this)
        val systemText = if (styleNotes.isNotBlank()) {
            "$SYSTEM_INTRO\n\nقوانین و سبک نوشتاری که گفته شده بدون محدودیت باید همیشه رعایت کنی:\n$styleNotes"
        } else {
            SYSTEM_INTRO
        }
        val systemInstruction = JSONObject()
            .put("parts", JSONArray().put(JSONObject().put("text", systemText)))

        val body = JSONObject()
            .put("contents", contentsArray)
            .put("systemInstruction", systemInstruction)

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
