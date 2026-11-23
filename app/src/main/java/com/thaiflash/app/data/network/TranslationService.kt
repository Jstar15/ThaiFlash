package com.thaiflash.app.data.network

import android.icu.text.BreakIterator as AndroidIcuBreakIterator
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.thaiflash.app.data.model.WordBreakdownItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.BreakIterator as JavaBreakIterator
import java.util.Locale

data class TranslationResult(
    val thai: String,
    val english: String,
    val romanization: String,
    val isOfflineFallback: Boolean = false
)

/**
 * Bidirectional English <-> Thai auto-translation + phonetic romanization service.
 *
 * Uses Google's free public translate endpoint (`dt=t` for translation + `dt=rm` for romanization)
 * with zero API keys required, plus a deterministic offline RTGS fallback for Thai script.
 */
object TranslationService {

    /** Returns true if the input contains any Thai script characters (U+0E00..U+0E7F). */
    fun containsThai(text: String): Boolean = text.any { it in '\u0E00'..'\u0E7F' }

    /**
     * Auto-detects whether [input] is Thai or English, translates to the opposite language,
     * and returns Thai script, English meaning, and phonetic romanization.
     */
    suspend fun autoTranslate(input: String): TranslationResult = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return@withContext TranslationResult("", "", "")
        }

        val inputIsThai = containsThai(trimmed)
        val sourceLang = if (inputIsThai) "th" else "en"
        val targetLang = if (inputIsThai) "en" else "th"

        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val endpoint =
                "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&dt=rm&q=$encoded"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return@withContext offlineFallback(trimmed, inputIsThai)
            }

            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
            conn.disconnect()

            parseGoogleTranslateResponse(body, trimmed, inputIsThai)
        } catch (_: Exception) {
            offlineFallback(trimmed, inputIsThai)
        }
    }

    /**
     * Parses Google Translate `dt=t&dt=rm` JSON array.
     * Structure of root[0]:
     *   [ [translatedSegment, sourceSegment, ...], ..., [null, null, targetRoman, sourceRoman] ]
     */
    internal fun parseGoogleTranslateResponse(
        jsonBody: String,
        originalInput: String,
        inputIsThai: Boolean
    ): TranslationResult {
        val root = JsonParser.parseString(jsonBody).asJsonArray
        val segments = root.get(0).asJsonArray

        val translatedBuilder = StringBuilder()
        var targetRoman = ""
        var sourceRoman = ""

        for (element: JsonElement in segments) {
            if (!element.isJsonArray) continue
            val arr = element.asJsonArray
            val first = arr.getOrNull(0)
            if (first != null && !first.isJsonNull) {
                translatedBuilder.append(first.asString)
            } else {
                // Romanization tuple: index 2 = target transliteration, index 3 = source transliteration
                val tRom = arr.getOrNull(2)?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                val sRom = arr.getOrNull(3)?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                if (tRom.isNotBlank()) targetRoman = tRom
                if (sRom.isNotBlank()) sourceRoman = sRom
            }
        }

        val translated = translatedBuilder.toString().trim()
        val thai = if (inputIsThai) originalInput else translated
        val english = if (inputIsThai) translated else originalInput
        val roman = when {
            inputIsThai && sourceRoman.isNotBlank() -> sourceRoman
            !inputIsThai && targetRoman.isNotBlank() -> targetRoman
            thai.isNotBlank() -> fallbackRomanizeThai(thai)
            else -> ""
        }

        return TranslationResult(
            thai = thai,
            english = english,
            romanization = roman.trim(),
            isOfflineFallback = false
        )
    }

    private fun offlineFallback(input: String, inputIsThai: Boolean): TranslationResult {
        return if (inputIsThai) {
            TranslationResult(
                thai = input,
                english = "",
                romanization = fallbackRomanizeThai(input),
                isOfflineFallback = true
            )
        } else {
            TranslationResult(
                thai = "",
                english = input,
                romanization = "",
                isOfflineFallback = true
            )
        }
    }

    /**
     * Segments a continuous Thai phrase into individual words using Android's built-in
     * ICU dictionary-based Thai BreakIterator (with Java BreakIterator fallback for JVM tests).
     */
    fun segmentThaiWords(thaiPhrase: String): List<String> {
        val clean = thaiPhrase.trim()
        if (clean.isEmpty()) return emptyList()

        val tokens = mutableListOf<String>()
        val thaiLocale = Locale("th", "TH")

        try {
            val icuIterator = AndroidIcuBreakIterator.getWordInstance(thaiLocale)
            icuIterator.setText(clean)
            var start = icuIterator.first()
            var end = icuIterator.next()
            while (end != AndroidIcuBreakIterator.DONE) {
                val word = clean.substring(start, end).trim()
                if (word.isNotEmpty() && word.any { it.isLetterOrDigit() || it in '\u0E00'..'\u0E7F' }) {
                    tokens.add(word)
                }
                start = end
                end = icuIterator.next()
            }
        } catch (_: Throwable) {
            // JVM unit test fallback when android.icu is unavailable
            val javaIterator = JavaBreakIterator.getWordInstance(thaiLocale)
            javaIterator.setText(clean)
            var start = javaIterator.first()
            var end = javaIterator.next()
            while (end != JavaBreakIterator.DONE) {
                val word = clean.substring(start, end).trim()
                if (word.isNotEmpty() && word.any { it.isLetterOrDigit() || it in '\u0E00'..'\u0E7F' }) {
                    tokens.add(word)
                }
                start = end
                end = javaIterator.next()
            }
        }

        return tokens.ifEmpty { listOf(clean) }
    }

    /**
     * Breaks a Thai phrase into individual words and translates each word to English
     * (+ romanization) automatically. Uses a single newline-batched request first for speed,
     * falling back to parallel per-word requests if line counts diverge.
     */
    suspend fun buildWordBreakdown(thaiPhrase: String): List<WordBreakdownItem> = withContext(Dispatchers.IO) {
        val words = segmentThaiWords(thaiPhrase)
        if (words.isEmpty()) return@withContext emptyList()

        // Fast path: single word
        if (words.size == 1) {
            val res = autoTranslate(words.first())
            return@withContext listOf(
                WordBreakdownItem(
                    thai = words.first(),
                    english = res.english.ifBlank { "" },
                    romanization = res.romanization.ifBlank { fallbackRomanizeThai(words.first()) }
                )
            )
        }

        // Batched fast path: newline-delimited list in one call
        val batched = autoTranslate(words.joinToString("\n"))
        if (!batched.isOfflineFallback) {
            val englishLines = batched.english.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val romanLines = batched.romanization.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (englishLines.size == words.size) {
                return@withContext words.mapIndexed { idx, w ->
                    WordBreakdownItem(
                        thai = w,
                        english = englishLines[idx],
                        romanization = romanLines.getOrNull(idx).orEmpty().ifBlank { fallbackRomanizeThai(w) }
                    )
                }
            }
        }

        // Accurate fallback: translate each segmented Thai word concurrently
        coroutineScope {
            words.map { word ->
                async {
                    val res = autoTranslate(word)
                    WordBreakdownItem(
                        thai = word,
                        english = res.english,
                        romanization = res.romanization.ifBlank { fallbackRomanizeThai(word) }
                    )
                }
            }.awaitAll()
        }
    }

    /**
     * Lightweight offline RTGS-inspired Thai romanizer used only when offline
     * or if the translation response omits phonetic text.
     */
    fun fallbackRomanizeThai(thai: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < thai.length) {
            val ch = thai[i]
            val mapped = THAI_CHAR_MAP[ch]
            when {
                mapped != null -> out.append(mapped)
                ch.isWhitespace() -> out.append(' ')
                ch in '\u0E48'..'\u0E4E' -> { /* tone/diacritic marks omitted in basic RTGS */ }
                else -> out.append(ch)
            }
            i++
        }
        return out.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun com.google.gson.JsonArray.getOrNull(index: Int): JsonElement? =
        if (index in 0 until size()) get(index) else null

    private val THAI_CHAR_MAP = mapOf(
        'ก' to "k", 'ข' to "kh", 'ฃ' to "kh", 'ค' to "kh", 'ฅ' to "kh", 'ฆ' to "kh",
        'ง' to "ng", 'จ' to "ch", 'ฉ' to "ch", 'ช' to "ch", 'ซ' to "s", 'ฌ' to "ch",
        'ญ' to "y", 'ฎ' to "d", 'ฏ' to "t", 'ฐ' to "th", 'ฑ' to "th", 'ฒ' to "th",
        'ณ' to "n", 'ด' to "d", 'ต' to "t", 'ถ' to "th", 'ท' to "th", 'ธ' to "th",
        'น' to "n", 'บ' to "b", 'ป' to "p", 'ผ' to "ph", 'ฝ' to "f", 'พ' to "ph",
        'ฟ' to "f", 'ภ' to "ph", 'ม' to "m", 'ย' to "y", 'ร' to "r", 'ล' to "l",
        'ว' to "w", 'ศ' to "s", 'ษ' to "s", 'ส' to "s", 'ห' to "h", 'ฬ' to "l",
        'อ' to "o", 'ฮ' to "h",
        'ะ' to "a", 'ั' to "a", 'า' to "aa", 'ำ' to "am", 'ิ' to "i", 'ี' to "ii",
        'ึ' to "ue", 'ื' to "ue", 'ุ' to "u", 'ู' to "uu", 'เ' to "e", 'แ' to "ae",
        'โ' to "o", 'ใ' to "ai", 'ไ' to "ai", 'ฤ' to "rue", 'ๆ' to ""
    )
}
