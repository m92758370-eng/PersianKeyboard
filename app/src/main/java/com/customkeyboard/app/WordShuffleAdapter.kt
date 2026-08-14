package com.customkeyboard.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * دو نوع ردیف داره: هدر (همه‌ی دکمه‌ها و تنظیمات بالای صفحه، فقط یه بار وجود داره)
 * و پارت (هر متن تولیدشده). چون RecyclerView فقط چیزی که دیده می‌شه رو واقعاً می‌سازه،
 * حتی با صدها پارت هم اسکرول روون می‌مونه.
 */
class WordShuffleAdapter(
    private val activity: WordShuffleActivity
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_PART = 1
    }

    // هر آیتم: (شماره واقعی پارت بر اساس ترتیب تولید, متن نشونه‌دار). جدیدترین پارت همیشه ایندکس 0.
    val partsData = mutableListOf<Pair<Int, String>>()

    override fun getItemCount(): Int = 1 + partsData.size

    override fun getItemViewType(position: Int): Int =
        if (position == 0) TYPE_HEADER else TYPE_PART

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_word_shuffle_header, parent, false))
        } else {
            PartViewHolder(inflater.inflate(R.layout.item_word_shuffle_part, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            activity.bindHeader(holder)
        } else if (holder is PartViewHolder) {
            val (partNumber, marked) = partsData[position - 1]
            activity.bindPart(holder, partNumber, marked)
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val edtManualWord: EditText = view.findViewById(R.id.edtManualWord)
        val btnAddWord: Button = view.findViewById(R.id.btnAddWord)
        val btnPickFile: Button = view.findViewById(R.id.btnPickFile)
        val txtWordCount: TextView = view.findViewById(R.id.txtWordCount)
        val btnClearWords: Button = view.findViewById(R.id.btnClearWords)
        val edtWordListName: EditText = view.findViewById(R.id.edtWordListName)
        val btnSaveWordList: Button = view.findViewById(R.id.btnSaveWordList)
        val savedListsContainer: LinearLayout = view.findViewById(R.id.savedListsContainer)
        val btnCombineSelected: Button = view.findViewById(R.id.btnCombineSelected)
        val btnGenerate: Button = view.findViewById(R.id.btnGenerate)
        val txtParagraphCount: TextView = view.findViewById(R.id.txtParagraphCount)
        val btnResetProgress: Button = view.findViewById(R.id.btnResetProgress)
    }

    class PartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtPartLabel: TextView = view.findViewById(R.id.txtPartLabel)
        val edtPartText: EditText = view.findViewById(R.id.edtPartText)
        val btnCopyPart: Button = view.findViewById(R.id.btnCopyPart)
    }
}
