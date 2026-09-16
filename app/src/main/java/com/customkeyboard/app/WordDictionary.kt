package com.customkeyboard.app

import android.content.Context

/**
 * دیکشنریِ کاملاً خودآموز برای کلماتِ پیشنهادی: هیچ لیستِ از پیش آماده‌ای نداره.
 * هر کلمه‌ای که کاربر با فاصله یا اینتر تمومش کنه، شمارنده‌ش یکی زیاد می‌شه؛
 * وقتی بیشتر از LEARN_THRESHOLD بار تایپ بشه (یعنی از دفعه‌ی چهارم به بعد)،
 * تو پیشنهادها ظاهر می‌شه.
 */
object WordDictionary {

    private const val LEARN_THRESHOLD = 3 // بیشتر از این تعداد تکرار = پیشنهاد داده می‌شه
    private const val MAX_WORDS = 400      // سقفِ تعداد کلمات ذخیره‌شده، برای جلوگیری از بزرگ‌شدنِ بی‌حدِ حافظه

    /** وقتی کاربر یه کلمه رو با فاصله/اینتر/زدنِ پیشنهاد تموم می‌کنه، صداش کن. */
    fun recordTyped(context: Context, word: String) {
        val trimmed = word.trim()
        if (trimmed.length < 2) return // تک‌حرفی‌ها یاد گرفته نشن

        val counts = PrefsHelper.getUserWordCounts(context).toMutableMap()
        counts[trimmed] = (counts[trimmed] ?: 0) + 1

        if (counts.size > MAX_WORDS) {
            // کم‌تکرارترین‌ها رو حذف کن تا اندازه‌ش کنترل‌شده بمونه؛ کلماتی که به آستانه‌ی
            // پیشنهاد رسیدن رو تا حد امکان نگه می‌داره چون مرتب‌سازی بر اساس تعداد تکراره
            val toDrop = counts.entries.sortedBy { it.value }.take(counts.size - MAX_WORDS)
            toDrop.forEach { counts.remove(it.key) }
        }

        PrefsHelper.setUserWordCounts(context, counts)
    }

    /** لیستِ پیشنهادها برای پیشوندِ فعلی؛ فقط کلماتی که از آستانه رد شدن برمی‌گرده. */
    fun getSuggestions(context: Context, prefix: String, limit: Int = 3): List<String> {
        if (prefix.isBlank()) return emptyList()
        val counts = PrefsHelper.getUserWordCounts(context)
        return counts.entries
            .filter { it.value > LEARN_THRESHOLD && it.key != prefix && it.key.startsWith(prefix) }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }
    }
}
