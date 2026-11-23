package com.thaiflash.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationServiceTest {

    @Test
    fun detectsThaiUnicodeScriptAccurately() {
        assertTrue(TranslationService.containsThai("ขอเช็คบิลหน่อยครับ"))
        assertTrue(TranslationService.containsThai("Hello สวัสดี"))
        assertFalse(TranslationService.containsThai("Can I get the bill please?"))
    }

    @Test
    fun parsesGoogleTranslateJsonWithRomanization() {
        val mockJson = """
            [
              [
                ["สวัสดีครับ", "Hello", null, null, 10],
                [null, null, "S̄wạs̄dī khrạb", null]
              ],
              null,
              "en"
            ]
        """.trimIndent()

        val result = TranslationService.parseGoogleTranslateResponse(
            jsonBody = mockJson,
            originalInput = "Hello",
            inputIsThai = false
        )

        assertEquals("สวัสดีครับ", result.thai)
        assertEquals("Hello", result.english)
        assertEquals("S̄wạs̄dī khrạb", result.romanization)
        assertFalse(result.isOfflineFallback)
    }

    @Test
    fun fallbackRomanizerProducesReadableOutput() {
        val roman = TranslationService.fallbackRomanizeThai("สวัสดี")
        assertTrue(roman.isNotBlank())
    }

    @Test
    fun segmentsThaiSentenceIntoWords() {
        val words = TranslationService.segmentThaiWords("ขอเช็คบิลหน่อยครับ")
        assertTrue("Expected at least 1 word, got $words", words.isNotEmpty())
        assertEquals("ขอเช็คบิลหน่อยครับ", words.joinToString(""))
    }
}
