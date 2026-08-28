package com.customkeyboard.app

object KeyboardLayouts {

    val PERSIAN: List<List<String>> = listOf(
        listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج"),
        listOf("ش", "س", "ی", "ب", "ل", "ا", "ت", "ن", "م", "ک", "گ"),
        listOf("ظ", "ط", "ژ", "ز", "ر", "ذ", "د", "پ", "و", "چ")
    )

    val ENGLISH: List<List<String>> = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m")
    )

    // شماره‌ی کوچیکی که گوشه‌ی هر کلید ردیف اول نشون داده می‌شه (شبیه کیبورد سیستمی)
    val PERSIAN_ROW1_DIGIT_HINTS: List<String> =
        listOf("١", "٢", "٣", "۴", "۵", "۶", "٧", "٨", "٩", "٠", "-")

    // صفحه‌ی علائم/اعداد (وقتی رو دکمه‌ی زبان دو بار ضربه بزنی باز می‌شه)
    val SYMBOLS: List<List<String>> = listOf(
        listOf("١", "٢", "٣", "۴", "۵", "۶", "٧", "٨", "٩", "٠"),
        listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", ","),
        listOf("/", ":", ";", "!", "؟", "\"", "'", "*")
    )

    // صفحه‌ی اعداد به شکل ماشین‌حساب (از داخل صفحه‌ی علائم قابل دسترسیه)
    val NUMBERS: List<List<String>> = listOf(
        listOf("٧", "٨", "٩"),
        listOf("۴", "۵", "۶"),
        listOf("١", "٢", "٣")
    )

    fun allLetters(): List<String> = PERSIAN.flatten() + ENGLISH.flatten()

    // اندازه‌ی هر ردیف حروف فارسی، برای تبدیل یه لیست تخت (که کاربر جابجا کرده) به ردیف‌بندی اصلی
    fun persianRowSizes(): List<Int> = PERSIAN.map { it.size }

    // یه لیست تخت از حروف رو بر اساس اندازه‌ی ردیف‌های داده‌شده به چند ردیف تقسیم می‌کنه
    fun chunkToRows(flat: List<String>, rowSizes: List<Int>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var index = 0
        for (size in rowSizes) {
            val end = (index + size).coerceAtMost(flat.size)
            rows.add(flat.subList(index, end))
            index = end
        }
        return rows
    }
}
