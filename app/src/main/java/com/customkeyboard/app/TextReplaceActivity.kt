package com.customkeyboard.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TextReplaceActivity : AppCompatActivity() {

    private lateinit var edtSourceText: EditText
    private lateinit var edtWordsToRemove: EditText
    private lateinit var edtWordsReplacement: EditText
    private lateinit var edtOutput: EditText
    private lateinit var edtSearchQuery: EditText
    private lateinit var scrollRoot: ScrollView

    private var lastSearchIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_replace)

        scrollRoot = findViewById(R.id.scrollRoot)
        edtSourceText = findViewById(R.id.edtSourceText)
        edtWordsToRemove = findViewById(R.id.edtWordsToRemove)
        edtWordsReplacement = findViewById(R.id.edtWordsReplacement)
        edtOutput = findViewById(R.id.edtOutput)
        edtSearchQuery = findViewById(R.id.edtSearchQuery)

        findViewById<Button>(R.id.btnCopySource).setOnClickListener {
            copyToClipboard(edtSourceText.text.toString())
        }

        findViewById<Button>(R.id.btnClearSource).setOnClickListener {
            edtSourceText.setText("")
        }

        findViewById<Button>(R.id.btnCopyOutput).setOnClickListener {
            copyToClipboard(edtOutput.text.toString())
        }

        findViewById<Button>(R.id.btnClearOutput).setOnClickListener {
            edtOutput.setText("")
        }

        findViewById<Button>(R.id.btnSearch).setOnClickListener {
            searchInOutput()
        }

        findViewById<Button>(R.id.btnApplyReplace).setOnClickListener {
            applyReplace()
        }
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

    private fun searchInOutput() {
        val query = edtSearchQuery.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "اول یه کلمه برای سرچ بنویس", Toast.LENGTH_SHORT).show()
            return
        }
        val fullText = edtOutput.text.toString()
        if (fullText.isEmpty()) {
            Toast.makeText(this, "نتیجه‌ای برای سرچ نیست", Toast.LENGTH_SHORT).show()
            return
        }

        val startFrom = if (lastSearchIndex >= fullText.length) 0 else lastSearchIndex
        var index = fullText.indexOf(query, startFrom)
        if (index == -1) {
            index = fullText.indexOf(query)
        }

        if (index == -1) {
            Toast.makeText(this, "پیدا نشد", Toast.LENGTH_SHORT).show()
            lastSearchIndex = 0
            return
        }

        lastSearchIndex = index + query.length

        edtOutput.requestFocus()
        edtOutput.setSelection(index, index + query.length)

        edtOutput.post {
            val layout = edtOutput.layout
            if (layout != null) {
                val line = layout.getLineForOffset(index)
                val y = layout.getLineTop(line)
                scrollRoot.post {
                    scrollRoot.smoothScrollTo(0, edtOutput.top + y)
                }
            }
        }
    }

    private fun applyReplace() {
        val sourceText = edtSourceText.text.toString()
        if (sourceText.isBlank()) {
            Toast.makeText(this, "اول یه متن اصلی وارد کن", Toast.LENGTH_SHORT).show()
            return
        }

        val wordsToRemove = edtWordsToRemove.text.toString()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val replacements = edtWordsReplacement.text.toString()
            .split("\n")
            .map { it.trim() }

        if (wordsToRemove.isEmpty()) {
            Toast.makeText(this, "حداقل یه کلمه برای حذف وارد کن", Toast.LENGTH_SHORT).show()
            return
        }

        var result = sourceText
        for (i in wordsToRemove.indices) {
            val target = wordsToRemove[i]
            val replacement = if (i < replacements.size) replacements[i] else ""
            if (target.isNotEmpty()) {
                result = result.replace(target, replacement)
            }
        }

        edtOutput.setText(result)
        lastSearchIndex = 0
        Toast.makeText(this, "انجام شد", Toast.LENGTH_SHORT).show()
    }
}
