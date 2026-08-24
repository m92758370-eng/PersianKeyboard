package com.customkeyboard.app

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.random.Random

class WordShuffleActivity : AppCompatActivity() {

    private val currentWords = mutableListOf<String>()

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WordShuffleAdapter

    companion object {
        private const val WORDS_PER_PART = 350
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

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = WordShuffleAdapter(this)
        recyclerView.adapter = adapter
        // این باعث می‌شه چند تا ویو پارت از قبل آماده نگه داشته بشن تا اسکرول روون‌تر باشه
        recyclerView.setItemViewCacheSize(6)

        loadSavedParagraphsIntoAdapter()
    }

    private fun loadSavedParagraphsIntoAdapter() {
        val paragraphs = PrefsHelper.getParagraphs(this)
        adapter.partsData.clear()
        // جدیدترین پارت (آخرین ایندکس) اول لیست قرار می‌گیره تا بالای صفحه دیده بشه
        for (i in paragraphs.indices.reversed()) {
            adapter.partsData.add((i + 1) to paragraphs[i])
        }
        adapter.notifyDataSetChanged()
    }

    // این تابع از داخل WordShuffleAdapter صدا زده می‌شه تا ردیف هدر (همه‌ی دکمه‌ها) پر بشه
    fun bindHeader(holder: WordShuffleAdapter.HeaderViewHolder) {
        holder.txtWordCount.text = "تعداد کلمات فعلی: ${currentWords.size}"
        holder.txtParagraphCount.text = "پارت تولید شده: ${PrefsHelper.getParagraphs(this).size}"

        holder.btnAddWord.setOnClickListener {
            val word = holder.edtManualWord.text.toString().trim()
            if (word.isNotEmpty()) {
                currentWords.add(word)
                holder.edtManualWord.setText("")
                holder.txtWordCount.text = "تعداد کلمات فعلی: ${currentWords.size}"
            }
        }

        holder.btnPickFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
            }
            filePickerLauncher.launch(intent)
        }

        holder.btnClearWords.setOnClickListener {
            currentWords.clear()
            holder.txtWordCount.text = "تعداد کلمات فعلی: ${currentWords.size}"
            Toast.makeText(this, "لیست فعلی پاک شد", Toast.LENGTH_SHORT).show()
        }

        holder.btnSaveWordList.setOnClickListener {
            val name = holder.edtWordListName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "اول یه اسم برای لیست بنویس", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (currentWords.isEmpty()) {
                Toast.makeText(this, "لیست فعلی خالیه", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PrefsHelper.saveWordList(this, name, currentWords.toList())
            holder.edtWordListName.setText("")
            Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
            refreshSavedListsUI(holder.savedListsContainer)
        }

        holder.btnCombineSelected.setOnClickListener {
            var added = 0
            val container = holder.savedListsContainer
            for (i in 0 until container.childCount) {
                val row = container.getChildAt(i)
                val checkBox = row.findViewById<CheckBox>(R.id.checkboxWordList)
                val name = row.tag as? String ?: continue
                if (checkBox.isChecked) {
                    val words = PrefsHelper.getWordList(this, name)
                    currentWords.addAll(words)
                    added += words.size
                }
            }
            holder.txtWordCount.text = "تعداد کلمات فعلی: ${currentWords.size}"
            Toast.makeText(this, "$added کلمه اضافه شد", Toast.LENGTH_SHORT).show()
        }

        holder.btnGenerate.setOnClickListener {
            generateNextParagraph(holder)
        }

        holder.btnResetProgress.setOnClickListener {
            PrefsHelper.clearParagraphs(this)
            adapter.partsData.clear()
            adapter.notifyDataSetChanged()
            holder.txtParagraphCount.text = "پارت تولید شده: 0"
            Toast.makeText(this, "پیشرفت پاک شد", Toast.LENGTH_SHORT).show()
        }

        refreshSavedListsUI(holder.savedListsContainer)
    }

    // این تابع از داخل WordShuffleAdapter صدا زده می‌شه تا یه کارت پارت پر بشه
    fun bindPart(holder: WordShuffleAdapter.PartViewHolder, partNumber: Int, marked: String) {
        holder.txtPartLabel.text = "پارت $partNumber"
        holder.edtPartText.setText(buildSpannable(marked), TextView.BufferType.SPANNABLE)

        // وقتی از روی این پارت فوکوس برداشته می‌شه (یعنی کاربر احتمالاً چیزی اصلاح کرده)، ذخیره می‌کنیم
        holder.edtPartText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val newPlain = holder.edtPartText.text.toString()
                PrefsHelper.updateParagraph(this, partNumber - 1, newPlain)
                val idx = adapter.partsData.indexOfFirst { it.first == partNumber }
                if (idx != -1) {
                    adapter.partsData[idx] = partNumber to newPlain
                }
            }
        }

        holder.btnCopyPart.setOnClickListener {
            val plainText = holder.edtPartText.text.toString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("پارت $partNumber", plainText))
            Toast.makeText(this, "پارت $partNumber کپی شد", Toast.LENGTH_SHORT).show()
        }
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

    private fun loadWordsFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val text = reader.readText()
                val words = text.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
                currentWords.addAll(words)
                adapter.notifyItemChanged(0)
                Toast.makeText(this, "${words.size} کلمه بارگذاری شد", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در خواندن فایل", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddWordToListDialog(name: String, container: LinearLayout) {
        val edt = EditText(this).apply {
            hint = "کلمه‌ی جدید..."
        }
        AlertDialog.Builder(this)
            .setTitle("افزودن کلمه به \"$name\"")
            .setView(edt)
            .setPositiveButton("اضافه کن") { _, _ ->
                val word = edt.text.toString().trim()
                if (word.isNotEmpty()) {
                    PrefsHelper.addWordToList(this, name, word)
                    refreshSavedListsUI(container)
                    Toast.makeText(this, "اضافه شد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun refreshSavedListsUI(container: LinearLayout) {
        container.removeAllViews()
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
            val addWordBtn = Button(this).apply {
                text = "+ کلمه"
                textSize = 12f
                setOnClickListener {
                    showAddWordToListDialog(name, container)
                }
            }
            val deleteBtn = Button(this).apply {
                text = "حذف"
                textSize = 12f
                setOnClickListener {
                    PrefsHelper.deleteWordList(this@WordShuffleActivity, name)
                    refreshSavedListsUI(container)
                }
            }
            row.addView(checkBox)
            row.addView(label)
            row.addView(addWordBtn)
            row.addView(deleteBtn)
            container.addView(row)
        }
    }

    private fun generateNextParagraph(holder: WordShuffleAdapter.HeaderViewHolder) {
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

        // پارت جدید همیشه اول لیست (بالای صفحه) اضافه می‌شه، پارت‌های قبلی پایین می‌رن
        adapter.partsData.add(0, partNumber to markedText)
        adapter.notifyItemInserted(1) // آیتم 0 هدره، پس پارت جدید می‌شه آیتم 1
        recyclerView.scrollToPosition(0)

        holder.txtParagraphCount.text = "پارت تولید شده: ${PrefsHelper.getParagraphs(this).size}"
    }
}
