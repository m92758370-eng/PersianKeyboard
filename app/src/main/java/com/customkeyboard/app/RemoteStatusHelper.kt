package com.customkeyboard.app

import android.app.AlertDialog
import android.content.Context
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object RemoteStatusHelper {

    private const val OWNER_DEVICE_ID = "4fd934243c4d9286"
    private const val STATUS_URL = "https://raw.githubusercontent.com/m92758370-eng/PersianKeyboard/main/status.json"
    private const val PREFS_NAME = "remote_status_prefs"
    private const val KEY_CACHED_ENABLED = "cached_enabled"

    private val executor = Executors.newSingleThreadExecutor()

    fun isOwnerDevice(context: Context): Boolean {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return id == OWNER_DEVICE_ID
    }

    fun isEnabledCached(context: Context): Boolean {
        if (isOwnerDevice(context)) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CACHED_ENABLED, true)
    }

    fun refreshStatusAsync(context: Context, onResult: ((Boolean) -> Unit)? = null) {
        if (isOwnerDevice(context)) {
            onResult?.invoke(true)
            return
        }
        executor.execute {
            val enabled = try {
                val connection = URL(STATUS_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.requestMethod = "GET"
                val text = connection.inputStream.bufferedReader().readText()
                connection.disconnect()
                !(text.contains("\"enabled\": false") || text.contains("\"enabled\":false"))
            } catch (e: Exception) {
                isEnabledCached(context)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_CACHED_ENABLED, enabled).apply()
            onResult?.invoke(enabled)
        }
    }

    fun blockIfDisabled(activity: AppCompatActivity): Boolean {
        refreshStatusAsync(activity)
        if (!isEnabledCached(activity)) {
            AlertDialog.Builder(activity)
                .setTitle("غیرفعال شده")
                .setMessage("این برنامه در دسترس نیست.")
                .setCancelable(false)
                .setPositiveButton("باشه") { _, _ -> activity.finish() }
                .show()
            return true
        }
        return false
    }
}
