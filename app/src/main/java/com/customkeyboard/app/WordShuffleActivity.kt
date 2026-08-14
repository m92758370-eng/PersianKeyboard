package com.customkeyboard.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.UnderlineSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class WordShuffleActivity : AppCompatActivity() {

    private val currentWords = mutableListOf<String>()

    private lateinit var edtManualWord: EditText
    private lateinit var txtWordCount: TextView
    private lateinit var savedListsContainer: LinearLayout
    private lateinit var edtWordListName: EditText
    private lateinit var partsContainer: LinearLayout
    private lateinit var txtParagraphCount: TextView

    companion object {
        private const val WORDS_PER_PART = 700
        // این کاراکتر نامرئی دور کلمه‌های تکراری (خط قرمز) رو تو متن ذخیره‌شده مشخص می‌کنه
        // تا بعد از بستن و باز کردن دوباره‌ی صفحه هم علامت قرمزها از بین نره
        private const val MARK = "\u0004"
    }

    private val filePickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                loadWordsFromUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_shuffle)

        if (RemoteStatusHelper.blockIfDisabled(this)) return

        edtManualWord = findViewById(R.id.edtManualWord)
        txtWordCount = findViewById(R.id.txtWordCount)
        savedListsContainer = findViewById(R.id.savedListsContainer)
        edtWordListName = findViewById(R.id.edtWordListName)
        partsContainer = findViewById(R.id.partsContainer)
        txtParagraphCount = findViewById(R.id.txtParagraphCount)

        findViewById<Button>(R.id.btnAddWord).setOnClickListener {
            val word = edtManualWord.text.toString().trim()
            if (word.isNotEmpty()) {
                currentWords.add(word)
                edtManualWord.setText("")
                updateWordCount()
            }
        }

        findViewById<Button>(R.id.btnPickFile).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
            }
            filePickerLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btnClearWords).setOnClickListener {
            currentWords.clear()
            updateWordCount()
            Toast.makeText(this, "لیست فعلی پاک شد", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnSaveWordList).setOnClickListener {
            val name = edtWordListName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "اول یه اسم برای لیست بنویس", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (currentWords.isEmpty()) {
                Toast.makeText(this, "لیست فعلی خالیه", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PrefsHelper.saveWordList(this, name, currentWords.toList())
            edtWordListName.setText("")
            Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
            refreshSavedListsUI()
        }

        findViewById<Button>(R.id.btnCombineSelected).setOnClickListener {
            var added = 0
            for (i in 0 until savedListsContainer.childCount) {
                val row = savedListsContainer.getChildAt(i)
                val checkBox = row.findViewById<CheckBox>(R.id.checkboxWordList)
                val name = row.tag as? String ?: continue
                if (checkBox.isChecked) {
                    val words = PrefsHelper.getWordList(this, name)
                    currentWords.addAll(words)
                    added += words.size
                }
            }
            updateWordCount()
            Toast.makeText(this, "$added کلمه اضافه شد", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnGenerate).setOnClickListener {
            generateNextParagraph()
        }

        findViewById<Button>(R.id.btnResetProgress).setOnClickListener {
            PrefsHelper.clearParagraphs(this)
            partsContainer.removeAllViews()
            updateParagraphCount()
            Toast.makeText(this, "پیشرفت پاک شد", Toast.LENGTH_SHORT).show()
        }

        refreshSavedListsUI()
        updateWordCount()
        loadSavedParagraphs()
    }

    private fun loadSavedParagraphs() {
        partsContainer.removeAllViews()
        val paragraphs = PrefsHelper.getParagraphs(this)
        for ((index, marked) in paragraphs.withIndex()) {
            addPartView(index + 1, marked)
        }
        updateParagraphCount()
    }

    // متن هر پارت رو با نشونه‌های MARK دور کلمات تکراری تبدیل به یه Spannable قرمز/زیرخط‌دار می‌کنه
    private fun buildSpannable(marked: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val segments = marked.split(MARK)
        for ((i, segment) in segments.withIndex()) {
            val start = builder.length
            builder.append(segment)
            val end = builder.length
            val isViolation = i % 2 == 1
            if (isViolation) {
                builder.setSpan(ForegroundColorSpan(android.graphics.Color.RED), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return builder
    }

    // یه کارت کوچیک برای یه پارت می‌سازه: شماره پارت + دکمه کپی مخصوص همون پارت + متن قابل ویرایش
    private fun addPartView(partNumber: Int, marked: String) {
        val view = layoutInflater.inflate(R.layout.item_word_shuffle_part, partsContainer, false)
        view.findViewById<TextView>(R.id.txtPartLabel).text = "پارت $partNumber"

        val edtPart = view.findViewById<EditText>(R.id.edtPartText)
        edtPart.setText(buildSpannable(marked), TextView.BufferType.SPANNABLE)

        view.findViewById<Button>(R.id.btnCopyPart).setOnClickListener {
            val plainText = edtPart.text.toString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("پارت $partNumber", plainText))
            Toast.makeText(this, "پارت $partNumber کپی شد", Toast.LENGTH_SHORT).show()
        }

        partsContainer.addView(view)
    }

    private fun updateParagraphCount() {
        val count = PrefsHelper.getParagraphs(this).size
        txtParagraphCount.text = "پارت تولید شده: $count"
    }

    private fun loadWordsFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val text = reader.readText()
                val words = text.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
                currentWords.addAll(words)
                updateWordCount()
                Toast.makeText(this, "${words.size} کلمه بارگذاری شد", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در خواندن فایل", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateWordCount() {
        txtWordCount.text = "تعداد کلمات فعلی: ${currentWords.size}"
    }

    private fun refreshSavedListsUI() {
        savedListsContainer.removeAllViews()
        val names = PrefsHelper.getWordListNames(this)
        for (name in names) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                tag = name
            }
            val checkBox = CheckBox(this).apply {
                id = R.id.checkboxWordList
            }
            val label = TextView(this).apply {
                text = "$name (${PrefsHelper.getWordList(this@WordShuffleActivity, name).size} کلمه)"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val deleteBtn = Button(this).apply {
                text = "حذف"
                textSize = 12f
                setOnClickListener {
                    PrefsHelper.deleteWordList(this@WordShuffleActivity, name)
                    refreshSavedListsUI()
                }
            }
            row.addView(checkBox)
            row.addView(label)
            row.addView(deleteBtn)
            savedListsContainer.addView(row)
        }
    }

    private fun generateNextParagraph() {
        if (currentWords.size < 2) {
            Toast.makeText(this, "حداقل ۲ کلمه لازمه", Toast.LENGTH_SHORT).show()
            return
        }

        val usedPairs = PrefsHelper.getUsedPairs(this).toMutableSet()
        val newPairsThisRun = mutableListOf<Pair<String, String>>()
        val resultWords = mutableListOf<String>()
        val violationIndices = mutableSetOf<Int>()

        // ادامه از آخرین کلمه‌ی پارت قبلی (حتی اگه از جلسه‌ی قبل باشه)
        var previous: String? = PrefsHelper.getLastWord(this)
        val rnd = Random(System.nanoTime())

        while (resultWords.size < WORDS_PER_PART) {
            val pool = currentWords.shuffled(rnd).toMutableList()
            while (pool.isNotEmpty() && resultWords.size < WORDS_PER_PART) {
                var chosenIndex = -1
                for (i in pool.indices) {
                    val candidate = pool[i]
                    val pairKey = if (previous != null) previous!! to candidate else null
                    if (pairKey == null || pairKey !in usedPairs) {
                        chosenIndex = i
                        break
                    }
                }
                if (chosenIndex == -1) {
                    chosenIndex = 0
                    if (previous != null) {
                        violationIndices.add(resultWords.size)
                    }
                }
                val chosen = pool.removeAt(chosenIndex)
                resultWords.add(chosen)
                if (previous != null) {
                    val pairKey = previous!! to chosen
                    usedPairs.add(pairKey)
                    newPairsThisRun.add(pairKey)
                }
                previous = chosen
            }
        }

        PrefsHelper.addUsedPairs(this, newPairsThisRun)
        PrefsHelper.setLastWord(this, previous)

        // به‌جای Spannable موقتی، یه متن ساده با نشونه‌ی MARK دور کلمات تکراری می‌سازیم
        // که قابل ذخیره‌سازیه و بعد از بستن برنامه هم خط قرمزها از بین نمی‌ره
        val markedBuilder = StringBuilder()
        for ((index, word) in resultWords.withIndex()) {
            val isViolation = index in violationIndices
            if (isViolation) markedBuilder.append(MARK)
            markedBuilder.append(word)
            if (isViolation) markedBuilder.append(MARK)
            if (index != resultWords.size - 1) {
                markedBuilder.append(" ")
            }
        }
        val markedText = markedBuilder.toString()

        PrefsHelper.addParagraph(this, markedText)
        val partNumber = PrefsHelper.getParagraphs(this).size
        addPartView(partNumber, markedText)
        updateParagraphCount()
    }
}
