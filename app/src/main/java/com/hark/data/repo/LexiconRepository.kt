package com.hark.data.repo

import android.content.Context
import com.hark.domain.LexiconWord
import org.json.JSONArray
import java.time.LocalDate
import kotlin.math.abs

class LexiconRepository(private val context: Context) {

    private val words: List<LexiconWord> by lazy {
        loadWordsFromAssets()
    }

    private fun loadWordsFromAssets(): List<LexiconWord> {
        return try {
            val jsonString = context.assets.open("lexicon.json").bufferedReader().use { it.readText() }
            val array = JSONArray(jsonString)
            val list = mutableListOf<LexiconWord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val synonymsArray = obj.optJSONArray("nearSynonyms")
                val synonyms = mutableListOf<String>()
                if (synonymsArray != null) {
                    for (j in 0 until synonymsArray.length()) {
                        synonyms.add(synonymsArray.getString(j))
                    }
                }

                list.add(
                    LexiconWord(
                        id = obj.getString("id"),
                        word = obj.getString("word"),
                        phonetic = obj.optString("phonetic", ""),
                        pos = obj.optString("pos", "adjective"),
                        tier = obj.optInt("tier", 1),
                        tierLabel = obj.optString("tierLabel", "Elevated"),
                        definition = obj.getString("definition"),
                        canonicalExample = obj.getString("canonicalExample"),
                        useWhen = obj.getString("useWhen"),
                        contrastWord = obj.optString("contrastWord", ""),
                        contrastDistinction = obj.optString("contrastDistinction", ""),
                        nearSynonyms = synonyms,
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Deterministically select a Word of the Day for any given calendar date.
     */
    fun getWordForDate(date: LocalDate = LocalDate.now()): LexiconWord? {
        if (words.isEmpty()) return null
        val epochDay = date.toEpochDay()
        // Simple linear congruential pseudo-random index mapping for smooth shuffling
        val pseudoRandomHash = abs((epochDay * 1103515245L + 12345L).toInt())
        val index = (pseudoRandomHash % words.size)
        return words[index]
    }

    /**
     * Retrieve all words in the catalog.
     */
    fun getAllWords(): List<LexiconWord> = words

    /**
     * Words revealed so far: the Word of the Day for each date from [LAUNCH_DATE] through
     * [today], newest first and deduped. The archive grows by one entry per day rather than
     * showing the whole catalog up front.
     */
    fun getUnlockedWords(today: LocalDate = LocalDate.now()): List<LexiconWord> {
        if (words.isEmpty()) return emptyList()
        val start = if (LAUNCH_DATE.isBefore(today)) LAUNCH_DATE else today // always include today
        val unlocked = LinkedHashSet<LexiconWord>()
        var d = today
        while (!d.isBefore(start)) {
            getWordForDate(d)?.let { unlocked.add(it) }
            d = d.minusDays(1)
        }
        return unlocked.toList()
    }

    /**
     * Search words across term, definition, nuance, and near-synonyms.
     */
    fun search(query: String, tierFilter: Int? = null, source: List<LexiconWord> = words): List<LexiconWord> {
        val q = query.trim().lowercase()
        return source.filter { item ->
            val matchesTier = tierFilter == null || item.tier == tierFilter
            val matchesQuery = q.isEmpty() ||
                item.word.lowercase().contains(q) ||
                item.definition.lowercase().contains(q) ||
                item.useWhen.lowercase().contains(q) ||
                item.contrastWord.lowercase().contains(q) ||
                item.nearSynonyms.any { it.lowercase().contains(q) }
            matchesTier && matchesQuery
        }
    }

    fun getWordById(id: String): LexiconWord? {
        return words.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    companion object {
        // The day the Lexicon began revealing words; the archive unlocks from here forward.
        // Move this earlier to seed more back-history into the archive.
        private val LAUNCH_DATE: LocalDate = LocalDate.of(2026, 8, 27)
    }
}
