package com.customkeyboard.app

import android.content.Context
import android.content.SharedPreferences

object PrefsHelper {

    private const val PREFS_NAME = "keyboard_prefs"
    private const val KEY_AUTO_TYPE_TEXT = "auto_type_text"
    private const val KEY_AUTO_TYPE_DELAY_MS = "auto_type_delay_ms"
    private const val KEY_AUTO_TYPE_PROGRESS = "auto_type_progress"
    private const val KEY_SAVED_TEXTS = "saved_texts"
    private const val KEY_WORDLIST_NAMES = "wordlist_names"
    private const val KEY_USED_PAIRS = "word_shuffle_used_pairs"
    private const val KEY_PARAGRAPHS = "word_shuffle_paragraphs"
    private const val KEY_LAST_WORD = "word_shuffle_last_word"
    private const val KEY_BACKGROUND_URI = "keyboard_background_uri"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_AI_STYLE_NOTES = "ai_style_notes"
    private const val KEY_AI_EXAMPLES = "ai_story_examples"
    private const val KEY_AI_CHAT_HISTORY = "ai_chat_history"
    private const val MSG_SEPARATOR = "\u0006"
    private const val ROLE_SEPARATOR = "\u0007"
    private const val MAP_PREFIX = "map_"
    private const val WORDLIST_PREFIX = "wordlist_"
    private const val SEPARATOR = "\u0001"
    private const val PAIR_SEPARATOR = "\u0002"
    private const val MAX_SAVED_TEXTS = 30
    private const val KEY_WORDLIST_TRASH = "wordlist_trash_index"
    private const val TRASH_ENTRY_SEPARATOR = "\u0003"
    private const val TRASH_RETENTION_MS = 48L * 60L * 60L * 1000L
    private const val KEY_SPACE_LABEL = "space_label"
    private const val DEFAULT_SPACE_LABEL = "کینگ آنتونی"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_CUSTOM_PERSIAN_ORDER = "custom_persian_key_order"
    private const val KEY_LETTER_WIDTH_WEIGHTS = "letter_width_weights"
    private const val KEY_LETTER_ROW_HEIGHT_WEIGHTS = "letter_row_height_weights"
    private const val KEY_OVERLAP_PARTS = "overlap_finder_parts"
    private const val KEY_SPECIAL_KEY_WIDTH_WEIGHTS = "special_key_width_weights"
    private const val KEY_TOOLBAR_HEIGHT_WEIGHT = "toolbar_height_weight"
    private const val KEY_BOTTOM_ROW_HEIGHT_WEIGHT = "bottom_row_height_weight"
    private const val KEY_KB_WIDTH_SCALE = "kb_width_scale"
    private const val KEY_KB_HEIGHT_SCALE = "kb_height_scale"
    private const val KEY_KB_LEFT_MARGIN_FRACTION = "kb_left_margin_fraction"
    private const val KEY_USER_WORD_COUNTS = "user_word_counts"

    const val DEFAULT_DELAY_MS = 15L
    const val MIN_DELAY_MS = 5L
    const val MAX_DELAY_MS = 300L

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getReplacement(context: Context, letter: String): String {
        return prefs(context).getString(MAP_PREFIX + letter, "") ?: ""
    }

    fun setReplacement(context: Context, letter: String, replacement: String) {
        prefs(context).edit().putString(MAP_PREFIX + letter, replacement).apply()
    }

    fun getAutoTypeText(context: Context): String {
        return prefs(context).getString(KEY_AUTO_TYPE_TEXT, "") ?: ""
    }

    fun setAutoTypeText(context: Context, text: String) {
        prefs(context).edit().putString(KEY_AUTO_TYPE_TEXT, text).apply()
    }

    fun getAutoTypeProgress(context: Context): Int {
        return prefs(context).getInt(KEY_AUTO_TYPE_PROGRESS, 0)
    }

    fun setAutoTypeProgress(context: Context, index: Int) {
        prefs(context).edit().putInt(KEY_AUTO_TYPE_PROGRESS, index).apply()
    }

    fun getAutoTypeDelayMs(context: Context): Long {
        return prefs(context).getLong(KEY_AUTO_TYPE_DELAY_MS, DEFAULT_DELAY_MS)
    }

    fun setAutoTypeDelayMs(context: Context, delayMs: Long) {
        prefs(context).edit().putLong(KEY_AUTO_TYPE_DELAY_MS, delayMs).apply()
    }

    fun getSavedTexts(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_SAVED_TEXTS, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    fun addSavedText(context: Context, text: String) {
        if (text.isBlank()) return
        val current = getSavedTexts(context).toMutableList()
        current.remove(text)
        current.add(0, text)
        val trimmed = if (current.size > MAX_SAVED_TEXTS) current.take(MAX_SAVED_TEXTS) else current
        prefs(context).edit().putString(KEY_SAVED_TEXTS, trimmed.joinToString(SEPARATOR)).apply()
    }

    fun removeSavedText(context: Context, text: String) {
        val current = getSavedTexts(context).toMutableList()
        current.remove(text)
        prefs(context).edit().putString(KEY_SAVED_TEXTS, current.joinToString(SEPARATOR)).apply()
    }

    fun getWordListNames(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_WORDLIST_NAMES, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    fun getWordList(context: Context, name: String): List<String> {
        val raw = prefs(context).getString(WORDLIST_PREFIX + name, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    fun saveWordList(context: Context, name: String, words: List<String>) {
        val names = getWordListNames(context).toMutableList()
        if (!names.contains(name)) {
            names.add(name)
            prefs(context).edit().putString(KEY_WORDLIST_NAMES, names.joinToString(SEPARATOR)).apply()
        }
        prefs(context).edit().putString(WORDLIST_PREFIX + name, words.joinToString(SEPARATOR)).apply()
    }

    fun addWordToList(context: Context, name: String, word: String) {
        val current = getWordList(context, name).toMutableList()
        current.add(word)
        prefs(context).edit().putString(WORDLIST_PREFIX + name, current.joinToString(SEPARATOR)).apply()
    }

    fun deleteWordList(context: Context, name: String) {
        val names = getWordListNames(context).toMutableList()
        names.remove(name)
        prefs(context).edit().putString(KEY_WORDLIST_NAMES, names.joinToString(SEPARATOR)).apply()

        // به‌جای پاک کردن کامل، کلمات همون‌جا (WORDLIST_PREFIX+name) می‌مونن
        // و فقط تو ایندکس سطل زباله با زمان حذف ثبت می‌شن، تا قابل بازگردانی باشه
        val trash = getTrashIndexRaw(context)
        trash.removeAll { it.first == name }
        trash.add(name to System.currentTimeMillis())
        saveTrashIndexRaw(context, trash)
    }

    private fun getTrashIndexRaw(context: Context): MutableList<Pair<String, Long>> {
        val raw = prefs(context).getString(KEY_WORDLIST_TRASH, "") ?: ""
        if (raw.isEmpty()) return mutableListOf()
        return raw.split(SEPARATOR).mapNotNull { entry ->
            val parts = entry.split(TRASH_ENTRY_SEPARATOR)
            if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L) else null
        }.toMutableList()
    }

    private fun saveTrashIndexRaw(context: Context, list: List<Pair<String, Long>>) {
        val serialized = list.joinToString(SEPARATOR) { "${it.first}$TRASH_ENTRY_SEPARATOR${it.second}" }
        prefs(context).edit().putString(KEY_WORDLIST_TRASH, serialized).apply()
    }

    // لیست‌هایی که بیشتر از ۴۸ ساعت تو سطل زباله بودن رو برای همیشه پاک می‌کنه
    fun purgeExpiredTrash(context: Context) {
        val trash = getTrashIndexRaw(context)
        val now = System.currentTimeMillis()
        val expired = trash.filter { now - it.second > TRASH_RETENTION_MS }
        if (expired.isEmpty()) return
        val editor = prefs(context).edit()
        for ((name, _) in expired) {
            editor.remove(WORDLIST_PREFIX + name)
        }
        editor.apply()
        val remaining = trash.filter { now - it.second <= TRASH_RETENTION_MS }
        saveTrashIndexRaw(context, remaining)
    }

    // هر آیتم: (اسم لیست, زمان حذف بر حسب میلی‌ثانیه, تعداد کلمات)
    fun getTrashedLists(context: Context): List<Triple<String, Long, Int>> {
        purgeExpiredTrash(context)
        return getTrashIndexRaw(context).map { (name, ts) ->
            Triple(name, ts, getWordList(context, name).size)
        }
    }

    fun restoreWordList(context: Context, name: String) {
        val trash = getTrashIndexRaw(context)
        trash.removeAll { it.first == name }
        saveTrashIndexRaw(context, trash)

        val names = getWordListNames(context).toMutableList()
        if (!names.contains(name)) {
            names.add(name)
            prefs(context).edit().putString(KEY_WORDLIST_NAMES, names.joinToString(SEPARATOR)).apply()
        }
    }

    fun getTrashRetentionMs(): Long = TRASH_RETENTION_MS

    fun getUsedPairs(context: Context): Set<Pair<String, String>> {
        val raw = prefs(context).getString(KEY_USED_PAIRS, "") ?: ""
        if (raw.isEmpty()) return emptySet()
        return raw.split(SEPARATOR).mapNotNull { entry ->
            val parts = entry.split(PAIR_SEPARATOR)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toSet()
    }

    fun addUsedPairs(context: Context, pairs: List<Pair<String, String>>) {
        val current = getUsedPairs(context).toMutableSet()
        current.addAll(pairs)
        val serialized = current.joinToString(SEPARATOR) { "${it.first}${PAIR_SEPARATOR}${it.second}" }
        prefs(context).edit().putString(KEY_USED_PAIRS, serialized).apply()
    }

    fun clearUsedPairs(context: Context) {
        prefs(context).edit().remove(KEY_USED_PAIRS).apply()
    }

    fun getParagraphs(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_PARAGRAPHS, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(SEPARATOR).filter { it.isNotEmpty() }
    }

    // پارت‌های ذخیره‌شده‌ی «الصاق‌گیر» — به‌صورت پروژه‌های جدا (JSON)
    fun getOverlapProjectsJson(context: Context): String {
        return prefs(context).getString(KEY_OVERLAP_PARTS, "[]") ?: "[]"
    }

    fun saveOverlapProjectsJson(context: Context, json: String) {
        prefs(context).edit().putString(KEY_OVERLAP_PARTS, json).apply()
    }

    fun addParagraph(context: Context, text: String) {
        val current = getParagraphs(context).toMutableList()
        current.add(text)
        prefs(context).edit().putString(KEY_PARAGRAPHS, current.joinToString(SEPARATOR)).apply()
    }

    fun deleteParagraphAt(context: Context, index: Int) {
        val current = getParagraphs(context).toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            prefs(context).edit().putString(KEY_PARAGRAPHS, current.joinToString(SEPARATOR)).apply()
        }
    }

    fun updateParagraph(context: Context, index: Int, newText: String) {
        val current = getParagraphs(context).toMutableList()
        if (index in current.indices) {
            current[index] = if (newText.isEmpty()) " " else newText
            prefs(context).edit().putString(KEY_PARAGRAPHS, current.joinToString(SEPARATOR)).apply()
        }
    }

    fun clearParagraphs(context: Context) {
        prefs(context).edit().remove(KEY_PARAGRAPHS).remove(KEY_LAST_WORD).apply()
    }

    fun getLastWord(context: Context): String? {
        return prefs(context).getString(KEY_LAST_WORD, null)
    }

    fun setLastWord(context: Context, word: String?) {
        val editor = prefs(context).edit()
        if (word == null) editor.remove(KEY_LAST_WORD) else editor.putString(KEY_LAST_WORD, word)
        editor.apply()
    }

    fun getBackgroundImageUri(context: Context): String? {
        return prefs(context).getString(KEY_BACKGROUND_URI, null)
    }

    fun setBackgroundImageUri(context: Context, uri: String?) {
        val editor = prefs(context).edit()
        if (uri == null) editor.remove(KEY_BACKGROUND_URI) else editor.putString(KEY_BACKGROUND_URI, uri)
        editor.apply()
    }

    fun getGeminiApiKey(context: Context): String {
        return prefs(context).getString(KEY_GEMINI_API_KEY, "") ?: ""
    }

    fun setGeminiApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_GEMINI_API_KEY, key).apply()
    }

    fun getAiStyleNotes(context: Context): String {
        return prefs(context).getString(KEY_AI_STYLE_NOTES, "") ?: ""
    }

    fun setAiStyleNotes(context: Context, notes: String) {
        prefs(context).edit().putString(KEY_AI_STYLE_NOTES, notes).apply()
    }

    fun getAiExamples(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_AI_EXAMPLES, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    fun addAiExample(context: Context, example: String) {
        if (example.isBlank()) return
        val current = getAiExamples(context).toMutableList()
        current.add(0, example)
        prefs(context).edit().putString(KEY_AI_EXAMPLES, current.joinToString(SEPARATOR)).apply()
    }

    fun removeAiExampleAt(context: Context, index: Int) {
        val current = getAiExamples(context).toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            prefs(context).edit().putString(KEY_AI_EXAMPLES, current.joinToString(SEPARATOR)).apply()
        }
    }

    fun getChatHistory(context: Context): List<Pair<String, String>> {
        val raw = prefs(context).getString(KEY_AI_CHAT_HISTORY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(MSG_SEPARATOR).mapNotNull { entry ->
            val idx = entry.indexOf(ROLE_SEPARATOR)
            if (idx == -1) null else entry.substring(0, idx) to entry.substring(idx + 1)
        }
    }

    fun addChatMessage(context: Context, role: String, text: String) {
        val current = getChatHistory(context).toMutableList()
        current.add(role to text)
        val serialized = current.joinToString(MSG_SEPARATOR) { "${it.first}$ROLE_SEPARATOR${it.second}" }
        prefs(context).edit().putString(KEY_AI_CHAT_HISTORY, serialized).apply()
    }

    fun clearChatHistory(context: Context) {
        prefs(context).edit().remove(KEY_AI_CHAT_HISTORY).apply()
    }

    fun getSpaceLabel(context: Context): String {
        return prefs(context).getString(KEY_SPACE_LABEL, DEFAULT_SPACE_LABEL) ?: DEFAULT_SPACE_LABEL
    }

    fun setSpaceLabel(context: Context, label: String) {
        prefs(context).edit().putString(KEY_SPACE_LABEL, label).apply()
    }

    fun isDarkMode(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_DARK_MODE, true)
    }

    fun setDarkMode(context: Context, dark: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK_MODE, dark).apply()
    }

    // چیدمان دستی حروف فارسی: یه لیست تخت از ۳۴ حرف (به همون ترتیبی که کاربر جابجاشون کرده)
    fun getCustomPersianOrder(context: Context): List<String>? {
        val raw = prefs(context).getString(KEY_CUSTOM_PERSIAN_ORDER, "") ?: ""
        if (raw.isEmpty()) return null
        val list = raw.split(SEPARATOR).filter { it.isNotEmpty() }
        val expectedSize = KeyboardLayouts.PERSIAN.flatten().size
        return if (list.size == expectedSize) list else null
    }

    fun setCustomPersianOrder(context: Context, order: List<String>) {
        prefs(context).edit().putString(KEY_CUSTOM_PERSIAN_ORDER, order.joinToString(SEPARATOR)).apply()
    }

    fun resetCustomPersianOrder(context: Context) {
        prefs(context).edit().remove(KEY_CUSTOM_PERSIAN_ORDER).apply()
    }

    // وزن عرض هر حرف (نسبت به بقیه‌ی حروفِ همون ردیف)؛ اگه حرفی تو این مپ نباشه، وزنش ۱ (پیش‌فرض) حساب می‌شه
    fun getLetterWidthWeights(context: Context): Map<String, Float> {
        val raw = prefs(context).getString(KEY_LETTER_WIDTH_WEIGHTS, "") ?: ""
        if (raw.isEmpty()) return emptyMap()
        return raw.split(SEPARATOR).mapNotNull { entry ->
            val parts = entry.split(PAIR_SEPARATOR)
            if (parts.size == 2) parts[0] to (parts[1].toFloatOrNull() ?: 1f) else null
        }.toMap()
    }

    fun setLetterWidthWeights(context: Context, weights: Map<String, Float>) {
        val serialized = weights.entries.joinToString(SEPARATOR) { "${it.key}$PAIR_SEPARATOR${it.value}" }
        prefs(context).edit().putString(KEY_LETTER_WIDTH_WEIGHTS, serialized).apply()
    }

    // وزن ارتفاع هر کدوم از ۳ ردیف حروف فارسی
    // پیش‌فرض‌های جدید، اندازه‌گیری‌شده از اسکرین‌شاتِ مرجع (نسبتِ ۱۳۰:۱۴۲:۱۳۰ پیکسل)
    fun getPersianRowHeightWeights(context: Context): List<Float> {
        val raw = prefs(context).getString(KEY_LETTER_ROW_HEIGHT_WEIGHTS, "") ?: ""
        if (raw.isEmpty()) return listOf(0.945f, 1.033f, 0.945f)
        val list = raw.split(SEPARATOR).mapNotNull { it.toFloatOrNull() }
        return if (list.size == 3) list else listOf(0.945f, 1.033f, 0.945f)
    }

    fun setPersianRowHeightWeights(context: Context, weights: List<Float>) {
        prefs(context).edit().putString(KEY_LETTER_ROW_HEIGHT_WEIGHTS, weights.joinToString(SEPARATOR)).apply()
    }

    // وزن عرض کلیدهای خاص: کلیدهای نوار بالا (میکروفون/ترجمه/تنظیمات/ایموجی/کلیپ‌بورد/شبکه)
    // و کلیدهای ردیف پایین (؟١٢٣/اتوتایپ/زبان/فاصله/توقف-ادامه/نیم‌فاصله/اینتر).
    // کلید هر آیتم یه شناسه‌ی ثابته (مثلاً "toolbar_mic" یا "space")، نه لیبل نمایشی‌ش.
    fun getSpecialKeyWidthWeights(context: Context): Map<String, Float> {
        val raw = prefs(context).getString(KEY_SPECIAL_KEY_WIDTH_WEIGHTS, "") ?: ""
        if (raw.isEmpty()) return emptyMap()
        return raw.split(SEPARATOR).mapNotNull { entry ->
            val parts = entry.split(PAIR_SEPARATOR)
            if (parts.size == 2) parts[0] to (parts[1].toFloatOrNull() ?: 1f) else null
        }.toMap()
    }

    fun setSpecialKeyWidthWeights(context: Context, weights: Map<String, Float>) {
        val serialized = weights.entries.joinToString(SEPARATOR) { "${it.key}$PAIR_SEPARATOR${it.value}" }
        prefs(context).edit().putString(KEY_SPECIAL_KEY_WIDTH_WEIGHTS, serialized).apply()
    }

    // وزن ارتفاع نوار بالا — پیش‌فرضِ جدید از رویِ اندازه‌گیریِ اسکرین‌شاتِ مرجع (≈۱.۲ برابرِ حالتِ قبلی)
    fun getToolbarHeightWeight(context: Context): Float {
        return prefs(context).getFloat(KEY_TOOLBAR_HEIGHT_WEIGHT, 1.2f)
    }

    fun setToolbarHeightWeight(context: Context, weight: Float) {
        prefs(context).edit().putFloat(KEY_TOOLBAR_HEIGHT_WEIGHT, weight).apply()
    }

    // وزن ارتفاع ردیف پایین — پیش‌فرضِ جدید از رویِ اندازه‌گیریِ اسکرین‌شاتِ مرجع
    fun getBottomRowHeightWeight(context: Context): Float {
        return prefs(context).getFloat(KEY_BOTTOM_ROW_HEIGHT_WEIGHT, 1.076f)
    }

    fun setBottomRowHeightWeight(context: Context, weight: Float) {
        prefs(context).edit().putFloat(KEY_BOTTOM_ROW_HEIGHT_WEIGHT, weight).apply()
    }

    fun resetKeyboardEditorLayout(context: Context) {
        prefs(context).edit()
            .remove(KEY_LETTER_WIDTH_WEIGHTS)
            .remove(KEY_LETTER_ROW_HEIGHT_WEIGHTS)
            .remove(KEY_SPECIAL_KEY_WIDTH_WEIGHTS)
            .remove(KEY_TOOLBAR_HEIGHT_WEIGHT)
            .remove(KEY_BOTTOM_ROW_HEIGHT_WEIGHT)
            .apply()
    }

    // ---------- بزرگ/کوچیک کردنِ کلِ کیبورد (شبیه حالت کیبورد کوچیک/شناور شیائومی) ----------
    // عرض کل کیبورد نسبت به عرض صفحه‌ی گوشی (پیش‌فرض ۱ یعنی تمام‌عرض)
    fun getKeyboardWidthScale(context: Context): Float {
        return prefs(context).getFloat(KEY_KB_WIDTH_SCALE, 1f)
    }

    fun setKeyboardWidthScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_KB_WIDTH_SCALE, scale).apply()
    }

    // ارتفاع کل کیبورد نسبت به ارتفاع پیش‌فرض (پیش‌فرض ۱)
    fun getKeyboardHeightScale(context: Context): Float {
        return prefs(context).getFloat(KEY_KB_HEIGHT_SCALE, 1f)
    }

    fun setKeyboardHeightScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_KB_HEIGHT_SCALE, scale).apply()
    }

    // وقتی کیبورد کوچیک‌تر از عرض صفحه‌ست، این می‌گه چقدر از فضای خالیِ سمت چپ/راست
    // به سمت چپ بره؛ ۰ یعنی بچسبه به یه گوشه، ۱ یعنی بچسبه به گوشه‌ی دیگه، ۰.۵ یعنی وسط
    fun getKeyboardLeftMarginFraction(context: Context): Float {
        return prefs(context).getFloat(KEY_KB_LEFT_MARGIN_FRACTION, 0.5f)
    }

    fun setKeyboardLeftMarginFraction(context: Context, fraction: Float) {
        prefs(context).edit().putFloat(KEY_KB_LEFT_MARGIN_FRACTION, fraction).apply()
    }

    fun resetKeyboardSizing(context: Context) {
        prefs(context).edit()
            .remove(KEY_KB_WIDTH_SCALE)
            .remove(KEY_KB_HEIGHT_SCALE)
            .remove(KEY_KB_LEFT_MARGIN_FRACTION)
            .apply()
    }

    // ---------- شمارنده‌ی کلماتِ تایپ‌شده، برای کلماتِ پیشنهادیِ خودآموز ----------
    // کلید = خودِ کلمه، مقدار = چند بار تایپ شده. جایی دیگه تصمیم می‌گیره از چند بار به بعد پیشنهاد بشه.
    fun getUserWordCounts(context: Context): Map<String, Int> {
        val raw = prefs(context).getString(KEY_USER_WORD_COUNTS, "") ?: ""
        if (raw.isEmpty()) return emptyMap()
        return raw.split(SEPARATOR).mapNotNull { entry ->
            val parts = entry.split(PAIR_SEPARATOR)
            if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
        }.toMap()
    }

    fun setUserWordCounts(context: Context, counts: Map<String, Int>) {
        val serialized = counts.entries.joinToString(SEPARATOR) { "${it.key}$PAIR_SEPARATOR${it.value}" }
        prefs(context).edit().putString(KEY_USER_WORD_COUNTS, serialized).apply()
    }

    fun resetUserWordCounts(context: Context) {
        prefs(context).edit().remove(KEY_USER_WORD_COUNTS).apply()
    }
}
