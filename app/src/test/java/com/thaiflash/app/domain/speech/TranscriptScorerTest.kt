package com.thaiflash.app.domain.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptScorerTest {

    @Test
    fun exactMatchScores100AndIgnoresWhitespaceAndPunctuation() {
        assertEquals(100, TranscriptScorer.score("สวัสดีครับ!", "สวัสดี ครับ"))
    }

    @Test
    fun preservesThaiCombiningVowelsAndToneMarks() {
        val norm = TranscriptScorer.normalize("กินข้าว")
        assertEquals("กินข้าว", norm)
        assertTrue(TranscriptScorer.score("กิน", "กน") < 100)
    }

    @Test
    fun partialMatchProducesAlignmentWithUnmatchedChars() {
        val alignment = TranscriptScorer.align("ขอบคุณมากครับ", "ขอบคุณครับ")
        assertEquals("ขอบคุณมากครับ", alignment.target)
        assertEquals("ขอบคุณครับ", alignment.heard)
        assertTrue(alignment.targetMatched.any { !it })
        assertTrue(alignment.targetMatched.any { it })
    }
}
