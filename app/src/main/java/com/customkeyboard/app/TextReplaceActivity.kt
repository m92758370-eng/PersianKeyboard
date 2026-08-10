package com.customkeyboard.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TextReplaceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_replace)

        val edtSourceText = findViewById<EditText>(R.id.edtSourceText)
        val edtWordsToRemove = findViewById<EditText>(R.id.edtWordsToRemove)
        val edtWordsReplacement = findViewById<EditText>(R.id.edtWordsReplacement)
        val edtOutput = findViewById<EditText>(R.id.edtOutput)

        findViewById<Button>(R.id.btnApplyReplace).setOnClickListener {
            val sourceText = edtSourceText.text.toString()
            if (sourceText.isBlank()) {
                Toast.makeText(this, "اول یه متن اصلی وارد کن", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
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
                return@setOnClickListener
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
            Toast.makeText(this, "انجام شد", Toast.LENGTH_SHORT).show()
        }
    }
}
