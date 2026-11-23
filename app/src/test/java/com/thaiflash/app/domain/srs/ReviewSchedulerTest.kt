package com.thaiflash.app.domain.srs

import com.thaiflash.app.data.model.CardRating
import com.thaiflash.app.data.model.Flashcard
import com.thaiflash.app.data.model.ReviewFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReviewSchedulerTest {

    private val now = 1_750_000_000_000L
    private val dayMs = 24L * 60L * 60L * 1000L

    @Test
    fun applyRatingUpdatesStabilityDifficultyAndCounts() {
        val base = Flashcard(id = "c1", setId = "s1", thai = "สวัสดี", english = "Hello")

        val afterGood = ReviewScheduler.applyRating(base, CardRating.GOOD, now)
        assertEquals(CardRating.GOOD, afterGood.rating)
        assertEquals(1, afterGood.goodCount)
        assertEquals(1, afterGood.streak)
        assertTrue(afterGood.stabilityDays >= 2.0f)
        assertTrue(afterGood.difficulty < base.difficulty)

        val afterBad = ReviewScheduler.applyRating(afterGood, CardRating.BAD, now + dayMs)
        assertEquals(CardRating.BAD, afterBad.rating)
        assertEquals(1, afterBad.badCount)
        assertEquals(0, afterBad.streak)
        assertTrue(afterBad.stabilityDays < afterGood.stabilityDays)
        assertTrue(afterBad.difficulty > afterGood.difficulty)
    }

    @Test
    fun smart10LeansTowardRedWhileMixingInGreenRetention() {
        val reds = (1..8).map { i ->
            Flashcard(
                id = "r$i", setId = "s1", thai = "ท$i", english = "Red $i",
                rating = CardRating.BAD, badCount = 3, lastReviewedAt = now - dayMs
            )
        }
        val yellows = (1..5).map { i ->
            Flashcard(
                id = "y$i", setId = "s2", thai = "ย$i", english = "Yellow $i",
                rating = CardRating.OK, okCount = 1, lastReviewedAt = now - 2 * dayMs
            )
        }
        val greens = (1..6).map { i ->
            Flashcard(
                id = "g$i", setId = "s3", thai = "ก$i", english = "Green $i",
                rating = CardRating.GOOD, goodCount = 3, stabilityDays = 4f,
                lastReviewedAt = now - (i * 3L) * dayMs
            )
        }

        val deck = ReviewScheduler.buildReviewDeck(
            allCards = reds + yellows + greens,
            focus = ReviewFocus.SMART_10,
            sessionSize = 10,
            nowMs = now,
            random = Random(42)
        )

        assertEquals(10, deck.size)
        val redPicked = deck.count { it.rating == CardRating.BAD }
        val greenPicked = deck.count { it.rating == CardRating.GOOD }
        assertTrue("Expected at least 5 red cards, got $redPicked", redPicked >= 5)
        assertTrue("Expected at least 1 green retention card, got $greenPicked", greenPicked >= 1)
    }

    @Test
    fun smallDeckReturnsAllAvailableCardsWithoutCrash() {
        val cards = listOf(
            Flashcard(id = "1", setId = "s1", thai = "กิน", english = "Eat"),
            Flashcard(id = "2", setId = "s1", thai = "นอน", english = "Sleep")
        )
        val deck = ReviewScheduler.buildReviewDeck(cards, ReviewFocus.SMART_10, 10, now, Random(1))
        assertEquals(2, deck.size)
    }
}
