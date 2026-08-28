package com.customkeyboard.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class KeyLayoutActivity : AppCompatActivity() {

    private lateinit var adapter: KeyDragAdapter
    private val rowSizes = KeyboardLayouts.persianRowSizes()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_key_layout)

        if (RemoteStatusHelper.blockIfDisabled(this)) return

        val initialOrder = PrefsHelper.getCustomPersianOrder(this) ?: KeyboardLayouts.PERSIAN.flatten()

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewKeys)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = KeyDragAdapter(initialOrder.toMutableList(), rowSizes)
        recyclerView.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                adapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        touchHelper.attachToRecyclerView(recyclerView)

        findViewById<Button>(R.id.btnSaveKeyLayout).setOnClickListener {
            PrefsHelper.setCustomPersianOrder(this, adapter.currentOrder())
            Toast.makeText(this, "چیدمان ذخیره شد. دفعه‌ی بعد که کیبورد رو باز کنی اعمال می‌شه", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnResetKeyLayout).setOnClickListener {
            PrefsHelper.resetCustomPersianOrder(this)
            adapter.resetTo(KeyboardLayouts.PERSIAN.flatten())
            Toast.makeText(this, "به چیدمان پیش‌فرض برگشت", Toast.LENGTH_SHORT).show()
        }
    }

    private class KeyDragAdapter(
        private val items: MutableList<String>,
        private val rowSizes: List<Int>
    ) : RecyclerView.Adapter<KeyDragAdapter.RowHolder>() {

        fun currentOrder(): List<String> = items.toList()

        fun resetTo(newItems: List<String>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        fun moveItem(from: Int, to: Int) {
            if (from < 0 || to < 0 || from >= items.size || to >= items.size) return
            val moved = items.removeAt(from)
            items.add(to, moved)
            notifyItemMoved(from, to)
        }

        private fun rowNumberFor(position: Int): Int {
            var cumulative = 0
            for ((index, size) in rowSizes.withIndex()) {
                cumulative += size
                if (position < cumulative) return index + 1
            }
            return rowSizes.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_draggable_key, parent, false)
            return RowHolder(view)
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            holder.txtKeyLabel.text = items[position]
            holder.txtRowNumber.text = "ردیف ${rowNumberFor(position)}"
        }

        override fun getItemCount(): Int = items.size

        class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
            val txtKeyLabel: TextView = view.findViewById(R.id.txtKeyLabel)
            val txtRowNumber: TextView = view.findViewById(R.id.txtRowNumber)
        }
    }
}
