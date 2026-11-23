package com.thaiflash.app.data.model

import java.util.UUID

enum class CardRating {
    NEW,
    BAD,   // Red
    OK,    // Yellow
    GOOD   // Green
}

enum class StudyDirection {
    TH_TO_EN,
    EN_TO_TH,
    MIXED
}

enum class ReviewFocus {
    SMART_10,
    RED_ONLY,
    NEW_CARDS
}

enum class SetStudyOrder {
    IN_ORDER,
    SHUFFLED,
    STRUGGLING_FIRST
}

data class FlashcardSet(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String = "📚",
    val includeInReview: Boolean? = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Null-safe accessor for Gson-deserialized sets created before includeInReview existed. */
    fun isActiveInReview(): Boolean = includeInReview ?: true
}

data class WordBreakdownItem(
    val thai: String,
    val english: String,
    val romanization: String = ""
)

data class Flashcard(
    val id: String = UUID.randomUUID().toString(),
    val setId: String,
    val thai: String,
    val english: String,
    val romanization: String = "",
    val wordBreakdown: List<WordBreakdownItem>? = emptyList(),
    val rating: CardRating = CardRating.NEW,
    val stabilityDays: Float = 0.5f,
    val difficulty: Float = 5.0f,
    val badCount: Int = 0,
    val okCount: Int = 0,
    val goodCount: Int = 0,
    val streak: Int = 0,
    val lastReviewedAt: Long = 0L,
    val nextReviewAt: Long = 0L,
    val bestSpeechScore: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalReviews: Int
        get() = badCount + okCount + goodCount

    /** Null-safe accessor for Gson-deserialized cards created before wordBreakdown existed. */
    fun breakdownItems(): List<WordBreakdownItem> = wordBreakdown.orEmpty()
}

data class DailyStats(
    val lastStudyDayEpoch: Long = 0L,
    val currentStreakDays: Int = 0,
    val reviewedToday: Int = 0
)

data class ThaiFlashDatabase(
    val version: Int = 1,
    val curriculumVersionLoaded: Int = 0,
    val sets: List<FlashcardSet> = emptyList(),
    val cards: List<Flashcard> = emptyList(),
    val dailyStats: DailyStats = DailyStats()
)

data class UserSettings(
    val showRomanization: Boolean = false,
    val defaultDirection: StudyDirection = StudyDirection.TH_TO_EN,
    val autoSpeak: Boolean = true,
    val retryRedInReview: Boolean = true,
    val speechRate: Float = 0.85f,
    val selectedVoice: String? = null,
    val lastUsedSetId: String? = null
)
