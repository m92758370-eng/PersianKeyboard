package com.customkeyboard.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

/**
 * لیست «پروژه»‌های الصاق‌گیر. هر پروژه یه مجموعه پارت مستقل داره؛
 * با زدن یه پروژه وارد صفحه‌ی چت‌مانند اون پروژه می‌شی (OverlapProjectActivity).
 */
class OverlapFinderActivity : AppCompatActivity() {

    data class OverlapProject(val id: Long, var name: String, val parts: MutableList<String>)

    private lateinit var projectsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overlap_finder)

        if (RemoteStatusHelper.blockIfDisabled(this)) return

        projectsContainer = findViewById(R.id.projectsContainer)

        findViewById<Button>(R.id.btnNewProject).setOnClickListener {
            val projects = loadProjects()
            val newProject = OverlapProject(
                id = System.currentTimeMillis(),
                name = "پروژه ${projects.size + 1}",
                parts = mutableListOf()
            )
            projects.add(0, newProject) // جدیدترین پروژه همیشه بالا
            saveProjects(projects)
            openProject(newProject.id)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshProjectsUI()
    }

    private fun openProject(id: Long) {
        startActivity(Intent(this, OverlapProjectActivity::class.java).putExtra("projectId", id))
    }

    private fun loadProjects(): MutableList<OverlapProject> {
        val list = mutableListOf<OverlapProject>()
        val arr = try {
            JSONArray(PrefsHelper.getOverlapProjectsJson(this))
        } catch (e: Exception) {
            JSONArray()
        }
        for (i in 0 until arr.length()) {
            try {
                val obj = arr.getJSONObject(i)
                val partsArr = obj.getJSONArray("parts")
                val parts = mutableListOf<String>()
                for (j in 0 until partsArr.length()) parts.add(partsArr.getString(j))
                list.add(OverlapProject(obj.getLong("id"), obj.getString("name"), parts))
            } catch (e: Exception) {
                // این آیتم خراب بود، ردش می‌کنیم و بقیه رو نگه می‌داریم
            }
        }
        return list
    }

    private fun saveProjects(projects: List<OverlapProject>) {
        val arr = JSONArray()
        for (p in projects) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            val partsArr = JSONArray()
            p.parts.forEach { partsArr.put(it) }
            obj.put("parts", partsArr)
            arr.put(obj)
        }
        PrefsHelper.saveOverlapProjectsJson(this, arr.toString())
    }

    private fun refreshProjectsUI() {
        projectsContainer.removeAllViews()
        val projects = loadProjects()
        val density = resources.displayMetrics.density

        if (projects.isEmpty()) {
            val empty = TextView(this).apply {
                text = "هنوز پروژه‌ای نساختی. دکمه‌ی بالا رو بزن."
                setTextColor(android.graphics.Color.parseColor("#888888"))
            }
            projectsContainer.addView(empty)
            return
        }

        for (project in projects) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (10 * density).toInt() }
                setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
                setBackgroundColor(android.graphics.Color.parseColor("#1F1F1F"))
                isClickable = true
                setOnClickListener { openProject(project.id) }
            }

            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val preview = project.parts.lastOrNull()?.take(40) ?: "بدون پارت هنوز"
                text = "${project.name}\n${project.parts.size} پارت — $preview"
                textSize = 14f
            }

            val deleteBtn = Button(this).apply {
                text = "حذف"
                textSize = 12f
                setOnClickListener {
                    val current = loadProjects()
                    current.removeAll { it.id == project.id }
                    saveProjects(current)
                    refreshProjectsUI()
                }
            }

            row.addView(label)
            row.addView(deleteBtn)
            projectsContainer.addView(row)
        }
    }
}
