package com.hark.domain

/**
 * An elevated vocabulary entry in Hark's Lexicon (λέξις).
 */
data class LexiconWord(
    val id: String,
    val word: String,
    val phonetic: String,
    val pos: String, // e.g. "adjective", "noun", "verb"
    val tier: Int, // 1..5
    val tierLabel: String, // "Elevated", "Discriminating", "Literary", "Esoteric", "Legendary"
    val definition: String,
    val canonicalExample: String,
    val useWhen: String,
    val contrastWord: String,
    val contrastDistinction: String,
    val nearSynonyms: List<String>,
)
