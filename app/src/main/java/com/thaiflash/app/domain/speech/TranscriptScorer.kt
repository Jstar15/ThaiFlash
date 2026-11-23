package com.thaiflash.app.domain.speech

/** Result of aligning the target Thai line against what the speech recogniser heard. */
data class TranscriptAlignment(
    val target: String,
    val heard: String,
    val targetMatched: BooleanArray,
    val heardMatched: BooleanArray,
    val targetSourceIndex: IntArray
)

/**
 * Compares a Thai speech-recognition transcript with the target Thai phrase.
 * Preserves Thai combining vowel and tone marks while stripping whitespace and punctuation.
 */
object TranscriptScorer {

    private const val PUNCTUATION = ".,!?…;:\"'()[]{}<>-–—“”‘’"

    fun normalize(text: String): String =
        text.filterNot { it.isWhitespace() || PUNCTUATION.indexOf(it) >= 0 }

    /** 0–100 similarity between the target line and what was heard. */
    fun score(target: String, heard: String): Int {
        val a = normalize(target)
        val b = normalize(heard)
        if (a.isEmpty() || b.isEmpty()) return 0
        if (a == b) return 100

        val distance = levenshtein(a, b)
        val ratio = 1.0 - distance.toDouble() / maxOf(a.length, b.length)
        return (ratio * 100).toInt().coerceIn(0, 100)
    }

    fun align(targetRaw: String, heardRaw: String): TranscriptAlignment {
        val target = normalizeWithMap(targetRaw)
        val heard = normalizeWithMap(heardRaw)

        val rows = target.text.length
        val cols = heard.text.length
        val targetMatched = BooleanArray(rows)
        val heardMatched = BooleanArray(cols)

        if (rows == 0 || cols == 0) {
            return TranscriptAlignment(
                target = target.text,
                heard = heard.text,
                targetMatched = targetMatched,
                heardMatched = heardMatched,
                targetSourceIndex = target.sourceIndex
            )
        }

        val directions = Array(rows + 1) { ByteArray(cols + 1) }
        var previous = IntArray(cols + 1) { it }
        var current = IntArray(cols + 1)
        for (i in 1..rows) directions[i][0] = 2
        for (j in 1..cols) directions[0][j] = 3

        for (i in 1..rows) {
            current[0] = i
            for (j in 1..cols) {
                val same = target.text[i - 1] == heard.text[j - 1]
                val substitution = previous[j - 1] + if (same) 0 else 1
                val deletion = previous[j] + 1
                val insertion = current[j - 1] + 1

                var best = substitution
                var dir: Byte = if (same) 0 else 1
                if (deletion < best) {
                    best = deletion
                    dir = 2
                }
                if (insertion < best) {
                    best = insertion
                    dir = 3
                }
                current[j] = best
                directions[i][j] = dir
            }
            val swap = previous
            previous = current
            current = swap
        }

        var i = rows
        var j = cols
        while (i > 0 || j > 0) {
            when (directions[i][j].toInt()) {
                0 -> {
                    targetMatched[i - 1] = true
                    heardMatched[j - 1] = true
                    i--
                    j--
                }
                1 -> {
                    i--
                    j--
                }
                2 -> i--
                else -> j--
            }
        }

        return TranscriptAlignment(
            target = target.text,
            heard = heard.text,
            targetMatched = targetMatched,
            heardMatched = heardMatched,
            targetSourceIndex = target.sourceIndex
        )
    }

    private data class NormalizedText(val text: String, val sourceIndex: IntArray)

    private fun normalizeWithMap(raw: String): NormalizedText {
        val builder = StringBuilder(raw.length)
        val indices = ArrayList<Int>(raw.length)
        raw.forEachIndexed { index, char ->
            if (!char.isWhitespace() && PUNCTUATION.indexOf(char) < 0) {
                builder.append(char)
                indices.add(index)
            }
        }
        return NormalizedText(builder.toString(), indices.toIntArray())
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    substitution
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
