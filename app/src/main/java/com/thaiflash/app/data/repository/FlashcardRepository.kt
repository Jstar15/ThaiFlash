package com.thaiflash.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.thaiflash.app.data.model.CardRating
import com.thaiflash.app.data.model.DailyStats
import com.thaiflash.app.data.model.Flashcard
import com.thaiflash.app.data.model.FlashcardSet
import com.thaiflash.app.data.model.ThaiFlashDatabase
import com.thaiflash.app.data.model.WordBreakdownItem
import com.thaiflash.app.data.network.TranslationService
import com.thaiflash.app.domain.srs.ReviewScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Single source of truth for all Flashcard Sets, Cards, SRS ratings, daily streak,
 * and JSON backup/restore. Uses atomic file writes (write temp -> rename) to prevent corruption.
 */
class FlashcardRepository(context: Context) {

    private val appContext = context.applicationContext
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dataFile = File(appContext.filesDir, "thaiflash_data.json")
    private val tempFile = File(appContext.filesDir, "thaiflash_data.json.tmp")
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _db = MutableStateFlow(loadOrSeed())
    val db: StateFlow<ThaiFlashDatabase> = _db.asStateFlow()

    // --- Sets CRUD ---

    fun createSet(name: String, emoji: String = "📚"): FlashcardSet {
        val cleanName = name.trim().ifBlank { "Untitled Set" }
        val newSet = FlashcardSet(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            emoji = emoji.ifBlank { "📚" }
        )
        mutate { it.copy(sets = it.sets + newSet) }
        return newSet
    }

    fun updateSet(setId: String, name: String, emoji: String) {
        val cleanName = name.trim().ifBlank { return }
        mutate { db ->
            db.copy(
                sets = db.sets.map { s ->
                    if (s.id == setId) s.copy(name = cleanName, emoji = emoji.ifBlank { s.emoji }) else s
                }
            )
        }
    }

    fun deleteSet(setId: String) {
        mutate { db ->
            db.copy(
                sets = db.sets.filterNot { it.id == setId },
                cards = db.cards.filterNot { it.setId == setId }
            )
        }
    }

    fun setIncludeInReview(setId: String, include: Boolean) {
        mutate { db ->
            db.copy(
                sets = db.sets.map { s ->
                    if (s.id == setId) s.copy(includeInReview = include) else s
                }
            )
        }
    }

    // --- Cards CRUD ---

    fun addCard(
        setId: String,
        thai: String,
        english: String,
        romanization: String,
        wordBreakdown: List<WordBreakdownItem> = emptyList()
    ): Flashcard {
        val card = Flashcard(
            id = UUID.randomUUID().toString(),
            setId = setId,
            thai = thai.trim(),
            english = english.trim(),
            romanization = romanization.trim(),
            wordBreakdown = wordBreakdown
        )
        mutate { db -> db.copy(cards = listOf(card) + db.cards) }
        if (card.breakdownItems().isEmpty() && card.thai.isNotBlank()) {
            ioScope.launch {
                val breakdown = TranslationService.buildWordBreakdown(card.thai)
                if (breakdown.isNotEmpty()) {
                    updateCardBreakdown(card.id, breakdown)
                }
            }
        }
        return card
    }

    fun updateCard(
        cardId: String,
        setId: String,
        thai: String,
        english: String,
        romanization: String,
        wordBreakdown: List<WordBreakdownItem>? = null
    ) {
        var needsBreakdownRefresh = false
        mutate { db ->
            db.copy(
                cards = db.cards.map { c ->
                    if (c.id == cardId) {
                        val thaiChanged = c.thai.trim() != thai.trim()
                        val nextBreakdown = when {
                            wordBreakdown != null -> wordBreakdown
                            thaiChanged -> emptyList()
                            else -> c.breakdownItems()
                        }
                        if (nextBreakdown.isEmpty() && thai.isNotBlank()) {
                            needsBreakdownRefresh = true
                        }
                        c.copy(
                            setId = setId,
                            thai = thai.trim(),
                            english = english.trim(),
                            romanization = romanization.trim(),
                            wordBreakdown = nextBreakdown
                        )
                    } else c
                }
            )
        }
        if (needsBreakdownRefresh) {
            val cleanThai = thai.trim()
            ioScope.launch {
                val breakdown = TranslationService.buildWordBreakdown(cleanThai)
                if (breakdown.isNotEmpty()) {
                    updateCardBreakdown(cardId, breakdown)
                }
            }
        }
    }

    fun updateCardBreakdown(cardId: String, breakdown: List<WordBreakdownItem>) {
        mutate { db ->
            db.copy(
                cards = db.cards.map { c ->
                    if (c.id == cardId) c.copy(wordBreakdown = breakdown) else c
                }
            )
        }
    }

    fun deleteCard(cardId: String) {
        mutate { db -> db.copy(cards = db.cards.filterNot { it.id == cardId }) }
    }

    fun resetCardProgress(cardId: String) {
        mutate { db ->
            db.copy(
                cards = db.cards.map { c ->
                    if (c.id == cardId) {
                        c.copy(
                            rating = CardRating.NEW,
                            stabilityDays = 0.5f,
                            difficulty = 5.0f,
                            badCount = 0,
                            okCount = 0,
                            goodCount = 0,
                            streak = 0,
                            lastReviewedAt = 0L,
                            nextReviewAt = 0L
                        )
                    } else c
                }
            )
        }
    }

    // --- Study & SRS Updates ---

    fun recordRating(cardId: String, rating: CardRating, nowMs: Long = System.currentTimeMillis()) {
        mutate { db ->
            var ratedSetId: String? = null
            val updatedCards = db.cards.map { card ->
                if (card.id == cardId) {
                    ratedSetId = card.setId
                    ReviewScheduler.applyRating(card, rating, nowMs)
                } else card
            }
            // Studying & rating a card automatically activates its set in Smart Review
            val updatedSets = if (ratedSetId != null) {
                db.sets.map { s ->
                    if (s.id == ratedSetId && !s.isActiveInReview()) s.copy(includeInReview = true) else s
                }
            } else db.sets
            val updatedStats = updateDailyStats(db.dailyStats, nowMs)
            db.copy(sets = updatedSets, cards = updatedCards, dailyStats = updatedStats)
        }
    }

    fun recordSpeechScore(cardId: String, score: Int) {
        mutate { db ->
            db.copy(
                cards = db.cards.map { card ->
                    if (card.id == cardId) {
                        val best = maxOf(card.bestSpeechScore ?: 0, score.coerceIn(0, 100))
                        card.copy(bestSpeechScore = best)
                    } else card
                }
            )
        }
    }

    fun findDuplicate(thai: String, english: String, excludeCardId: String? = null): Flashcard? {
        val normThai = thai.trim()
        val normEn = english.trim().lowercase()
        return _db.value.cards.firstOrNull { card ->
            card.id != excludeCardId && (
                (normThai.isNotEmpty() && card.thai.trim() == normThai) ||
                    (normEn.isNotEmpty() && card.english.trim().lowercase() == normEn)
                )
        }
    }

    // --- Backup & Restore ---

    fun exportToJson(): String = gson.toJson(_db.value)

    fun importFromJson(json: String, merge: Boolean = true): Result<Int> {
        return try {
            val imported = gson.fromJson(json, ThaiFlashDatabase::class.java)
                ?: return Result.failure(IllegalArgumentException("Invalid backup file"))
            if (merge) {
                mutate { current ->
                    val existingSetIds = current.sets.map { it.id }.toSet()
                    val mergedSets = current.sets + imported.sets.filter { it.id !in existingSetIds }
                    val existingCardIds = current.cards.map { it.id }.toSet()
                    val mergedCards = current.cards + imported.cards.filter { it.id !in existingCardIds }
                    current.copy(sets = mergedSets, cards = mergedCards)
                }
            } else {
                mutate { imported }
            }
            Result.success(imported.cards.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Internals ---

    private fun updateDailyStats(stats: DailyStats, nowMs: Long): DailyStats {
        val todayEpochDay = nowMs / DAY_MS
        return when {
            stats.lastStudyDayEpoch == todayEpochDay -> stats.copy(reviewedToday = stats.reviewedToday + 1)
            stats.lastStudyDayEpoch == todayEpochDay - 1L -> DailyStats(
                lastStudyDayEpoch = todayEpochDay,
                currentStreakDays = stats.currentStreakDays + 1,
                reviewedToday = 1
            )
            else -> DailyStats(
                lastStudyDayEpoch = todayEpochDay,
                currentStreakDays = 1,
                reviewedToday = 1
            )
        }
    }

    private fun mutate(transform: (ThaiFlashDatabase) -> ThaiFlashDatabase) {
        val next = transform(_db.value)
        _db.value = next
        persistAsync(next)
    }

    private fun persistAsync(snapshot: ThaiFlashDatabase) {
        ioScope.launch {
            try {
                tempFile.writeText(gson.toJson(snapshot))
                if (tempFile.exists()) {
                    if (dataFile.exists()) dataFile.delete()
                    tempFile.renameTo(dataFile)
                }
            } catch (_: Exception) {
                // Fallback direct write if rename failed
                runCatching { dataFile.writeText(gson.toJson(snapshot)) }
            }
        }
    }

    private fun loadOrSeed(): ThaiFlashDatabase {
        val loaded = if (dataFile.exists()) {
            try {
                val parsed = gson.fromJson(dataFile.readText(), ThaiFlashDatabase::class.java)
                parsed?.copy(
                    sets = parsed.sets.map { s ->
                        if (s.includeInReview == null) s.copy(includeInReview = true) else s
                    },
                    cards = parsed.cards.map { card ->
                        if (card.wordBreakdown == null) card.copy(wordBreakdown = emptyList()) else card
                    }
                )
            } catch (_: Exception) {
                null
            }
        } else null

        val base = loaded ?: ThaiFlashDatabase(version = 1, curriculumVersionLoaded = 0)
        if (base.curriculumVersionLoaded >= CURRENT_CURRICULUM_VERSION) {
            return base
        }

        val merged = mergeCurriculumFromAssets(base)
        persistAsync(merged)
        return merged
    }

    private data class CurriculumBundle(
        val sets: List<FlashcardSet> = emptyList(),
        val cards: List<Flashcard> = emptyList()
    )

    private fun mergeCurriculumFromAssets(current: ThaiFlashDatabase): ThaiFlashDatabase {
        return try {
            val json = appContext.assets.open("thai_curriculum_17_sets.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val bundle = gson.fromJson(json, CurriculumBundle::class.java) ?: return current

            // Keep all user-created sets & cards; drop old unrated placeholder starter set if superseded
            val hasRatedStarter = current.cards.any { it.setId == "starter-essentials" && it.totalReviews > 0 }
            val keptSets = if (hasRatedStarter) {
                current.sets
            } else {
                current.sets.filterNot { it.id == "starter-essentials" }
            }
            val keptCards = if (hasRatedStarter) {
                current.cards
            } else {
                current.cards.filterNot { it.setId == "starter-essentials" }
            }

            val existingSetIds = keptSets.map { it.id }.toSet()
            val existingCardIds = keptCards.map { it.id }.toSet()

            val newSets = bundle.sets.filter { it.id !in existingSetIds }
            val newCards = bundle.cards.filter { it.id !in existingCardIds }

            current.copy(
                curriculumVersionLoaded = CURRENT_CURRICULUM_VERSION,
                sets = keptSets + newSets,
                cards = keptCards + newCards
            )
        } catch (_: Exception) {
            current
        }
    }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val CURRENT_CURRICULUM_VERSION = 5
    }
}
