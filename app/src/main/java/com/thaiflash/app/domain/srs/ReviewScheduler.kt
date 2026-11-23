package com.thaiflash.app.domain.srs

import com.thaiflash.app.data.model.CardRating
import com.thaiflash.app.data.model.Flashcard
import com.thaiflash.app.data.model.ReviewFocus
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * FSRS-inspired Spaced Repetition & Smart 10-Card Review Scheduler.
 *
 * Grounded in:
 * - FSRS (Stability, Difficulty, Retrievability) to prevent SM-2 "Ease Hell".
 * - Make It Stick (Retrieval Practice, Interleaving across sets, Same-session lapse retry).
 * - Desirable Difficulty (~55% Red struggling, ~25% Yellow/New learning, ~20% oldest Green retention).
 */
object ReviewScheduler {

    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val TEN_MINUTES_MS = 10L * 60L * 1000L
    private const val RECENT_COOLDOWN_MS = 3L * 60L * 1000L

    /**
     * Estimates current recall probability R in (0.0, 1.0].
     * R(t) = 0.9 ^ (elapsedDays / max(0.25, stabilityDays))
     */
    fun retrievability(card: Flashcard, nowMs: Long = System.currentTimeMillis()): Double {
        if (card.lastReviewedAt <= 0L) return 0.5
        val elapsedDays = max(0.0, (nowMs - card.lastReviewedAt).toDouble() / DAY_MS)
        val stability = max(0.25, card.stabilityDays.toDouble())
        return 0.9.pow(elapsedDays / stability).coerceIn(0.01, 1.0)
    }

    /** Updates a card's FSRS-inspired memory state after a Red (BAD), Yellow (OK), or Green (GOOD) rating. */
    fun applyRating(
        card: Flashcard,
        rating: CardRating,
        nowMs: Long = System.currentTimeMillis()
    ): Flashcard {
        return when (rating) {
            CardRating.BAD -> {
                val newDifficulty = min(10.0f, card.difficulty + 1.2f)
                val newStability = max(0.25f, card.stabilityDays * 0.4f)
                card.copy(
                    rating = CardRating.BAD,
                    difficulty = newDifficulty,
                    stabilityDays = newStability,
                    badCount = card.badCount + 1,
                    streak = 0,
                    lastReviewedAt = nowMs,
                    nextReviewAt = nowMs + TEN_MINUTES_MS
                )
            }

            CardRating.OK -> {
                val newDifficulty = (card.difficulty + 0.15f).coerceIn(1.0f, 10.0f)
                val newStability = max(1.0f, card.stabilityDays * 1.25f)
                card.copy(
                    rating = CardRating.OK,
                    difficulty = newDifficulty,
                    stabilityDays = newStability,
                    okCount = card.okCount + 1,
                    lastReviewedAt = nowMs,
                    nextReviewAt = nowMs + (newStability * DAY_MS).toLong()
                )
            }

            CardRating.GOOD -> {
                val newDifficulty = max(1.0f, card.difficulty - 0.5f)
                val growth = (2.2f - newDifficulty * 0.08f).coerceAtLeast(1.3f)
                val newStability = max(2.0f, card.stabilityDays * growth)
                card.copy(
                    rating = CardRating.GOOD,
                    difficulty = newDifficulty,
                    stabilityDays = newStability,
                    goodCount = card.goodCount + 1,
                    streak = card.streak + 1,
                    lastReviewedAt = nowMs,
                    nextReviewAt = nowMs + (newStability * DAY_MS).toLong()
                )
            }

            CardRating.NEW -> card
        }
    }

    /**
     * Builds a review session of up to [sessionSize] cards (default 10).
     */
    fun buildReviewDeck(
        allCards: List<Flashcard>,
        focus: ReviewFocus = ReviewFocus.SMART_10,
        sessionSize: Int = 10,
        nowMs: Long = System.currentTimeMillis(),
        random: Random = Random.Default
    ): List<Flashcard> {
        if (allCards.isEmpty() || sessionSize <= 0) return emptyList()

        return when (focus) {
            ReviewFocus.RED_ONLY -> {
                val reds = allCards.filter { it.rating == CardRating.BAD || isLeech(it) }
                val pool = reds.ifEmpty { allCards }
                val picked = weightedSample(
                    pool = pool,
                    count = min(sessionSize, pool.size),
                    weightFn = { redWeight(it, nowMs) },
                    random = random
                )
                interleaveBySet(picked, random)
            }

            ReviewFocus.NEW_CARDS -> {
                val newCards = allCards.filter { it.rating == CardRating.NEW }
                val pool = newCards.ifEmpty { allCards }
                interleaveBySet(pool.shuffled(random).take(sessionSize), random)
            }

            ReviewFocus.SMART_10 -> buildSmartDeck(allCards, sessionSize, nowMs, random)
        }
    }

    private fun buildSmartDeck(
        allCards: List<Flashcard>,
        sessionSize: Int,
        nowMs: Long,
        random: Random
    ): List<Flashcard> {
        if (allCards.size <= sessionSize) {
            return interleaveBySet(allCards.shuffled(random), random)
        }

        val redBucket = mutableListOf<Flashcard>()
        val learningBucket = mutableListOf<Flashcard>() // OK + NEW
        val greenBucket = mutableListOf<Flashcard>()

        allCards.forEach { card ->
            when {
                card.rating == CardRating.BAD || isLeech(card) -> redBucket.add(card)
                card.rating == CardRating.OK || card.rating == CardRating.NEW -> learningBucket.add(card)
                else -> greenBucket.add(card)
            }
        }

        // Target split for 10 cards: 6 Red (55-60%), 2 Yellow/New (20-25%), 2 Green (20%)
        val targetRed = ((sessionSize * 0.55) + 0.5).toInt().coerceAtMost(sessionSize)
        val targetGreen = (sessionSize * 0.20).toInt().coerceAtLeast(if (sessionSize >= 5) 1 else 0)
        val targetLearning = (sessionSize - targetRed - targetGreen).coerceAtLeast(0)

        val selected = mutableListOf<Flashcard>()
        val selectedIds = HashSet<String>()

        fun pickFrom(pool: List<Flashcard>, quota: Int, weightFn: (Flashcard) -> Double) {
            if (quota <= 0) return
            val available = pool.filter { it.id !in selectedIds }
            val picks = weightedSample(available, min(quota, available.size), weightFn, random)
            picks.forEach {
                selected.add(it)
                selectedIds.add(it.id)
            }
        }

        pickFrom(redBucket, targetRed) { redWeight(it, nowMs) }
        pickFrom(learningBucket, targetLearning) { learningWeight(it, nowMs) }
        pickFrom(greenBucket, targetGreen) { greenRetentionWeight(it, nowMs) }

        // Backfill any remaining slots gracefully: Red -> Learning -> Green
        val remaining = sessionSize - selected.size
        if (remaining > 0) {
            pickFrom(redBucket, sessionSize - selected.size) { redWeight(it, nowMs) }
        }
        if (selected.size < sessionSize) {
            pickFrom(learningBucket, sessionSize - selected.size) { learningWeight(it, nowMs) }
        }
        if (selected.size < sessionSize) {
            pickFrom(greenBucket, sessionSize - selected.size) { greenRetentionWeight(it, nowMs) }
        }

        val interleaved = interleaveBySet(selected, random).toMutableList()
        // Open with a confidence/momentum card (Yellow or Green) if available
        val warmStartIndex = interleaved.indexOfFirst { it.rating != CardRating.BAD }
        if (warmStartIndex > 0) {
            val warmCard = interleaved.removeAt(warmStartIndex)
            interleaved.add(0, warmCard)
        }
        return interleaved
    }

    private fun isLeech(card: Flashcard): Boolean {
        return card.rating != CardRating.GOOD && card.badCount >= 2 && card.badCount >= card.goodCount
    }

    private fun recencyMultiplier(card: Flashcard, nowMs: Long): Double {
        if (card.lastReviewedAt <= 0L) return 1.0
        val elapsed = nowMs - card.lastReviewedAt
        return if (elapsed in 0..RECENT_COOLDOWN_MS) 0.2 else 1.0
    }

    private fun redWeight(card: Flashcard, nowMs: Long): Double {
        val r = retrievability(card, nowMs)
        val lapseBoost = 1.0 + 0.35 * card.badCount
        return max(0.05, (1.5 - r) * lapseBoost * recencyMultiplier(card, nowMs))
    }

    private fun learningWeight(card: Flashcard, nowMs: Long): Double {
        val r = retrievability(card, nowMs)
        val base = when {
            card.rating == CardRating.NEW -> 1.4
            card.nextReviewAt in 1..nowMs -> 2.0 * (1.2 - r)
            else -> 0.8 * (1.2 - r)
        }
        return max(0.05, base * recencyMultiplier(card, nowMs))
    }

    private fun greenRetentionWeight(card: Flashcard, nowMs: Long): Double {
        val r = retrievability(card, nowMs)
        val daysSince = if (card.lastReviewedAt > 0L) {
            max(0.0, (nowMs - card.lastReviewedAt).toDouble() / DAY_MS)
        } else {
            1.0
        }
        // Heavily favors oldest Green cards nearing the forgetting curve
        return max(0.05, (1.15 - r) * (1.0 + daysSince * 0.35) * recencyMultiplier(card, nowMs))
    }

    private fun weightedSample(
        pool: List<Flashcard>,
        count: Int,
        weightFn: (Flashcard) -> Double,
        random: Random
    ): List<Flashcard> {
        if (count <= 0 || pool.isEmpty()) return emptyList()
        if (count >= pool.size) return pool.shuffled(random)

        val candidates = pool.map { it to max(0.001, weightFn(it)) }.toMutableList()
        val result = ArrayList<Flashcard>(count)

        repeat(count) {
            val totalWeight = candidates.sumOf { it.second }
            var threshold = random.nextDouble() * totalWeight
            var pickedIndex = candidates.lastIndex
            for (i in candidates.indices) {
                threshold -= candidates[i].second
                if (threshold <= 0.0) {
                    pickedIndex = i
                    break
                }
            }
            result.add(candidates.removeAt(pickedIndex).first)
        }
        return result
    }

    /** Interleaves cards across sets so consecutive cards come from different sets when possible. */
    private fun interleaveBySet(cards: List<Flashcard>, random: Random): List<Flashcard> {
        if (cards.size <= 2) return cards.shuffled(random)
        val remaining = cards.shuffled(random).toMutableList()
        val ordered = ArrayList<Flashcard>(cards.size)

        while (remaining.isNotEmpty()) {
            val lastSetId = ordered.lastOrNull()?.setId
            val nextIndex = remaining.indexOfFirst { it.setId != lastSetId }
            val pickIndex = if (nextIndex >= 0) nextIndex else 0
            ordered.add(remaining.removeAt(pickIndex))
        }
        return ordered
    }
}
