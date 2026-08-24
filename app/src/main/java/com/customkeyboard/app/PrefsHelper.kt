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
        prefs(context).edit()
            .putString(KEY_WORDLIST_NAMES, names.joinToString(SEPARATOR))
            .remove(WORDLIST_PREFIX + name)
            .apply()
    }

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

    fun addParagraph(context: Context, text: String) {
        val current = getParagraphs(context).toMutableList()
        current.add(text)
        prefs(context).edit().putString(KEY_PARAGRAPHS, current.joinToString(SEPARATOR)).apply()
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
}
