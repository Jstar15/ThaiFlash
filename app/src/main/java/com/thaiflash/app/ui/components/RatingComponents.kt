package com.thaiflash.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thaiflash.app.data.model.CardRating
import com.thaiflash.app.data.model.Flashcard

object RatingColors {
    val Red = Color(0xFFD32F2F)
    val Yellow = Color(0xFFF57C00)
    val Green = Color(0xFF2E7D32)
    val New = Color(0xFF9E9E9E)
}

fun CardRating.color(): Color = when (this) {
    CardRating.BAD -> RatingColors.Red
    CardRating.OK -> RatingColors.Yellow
    CardRating.GOOD -> RatingColors.Green
    CardRating.NEW -> RatingColors.New
}

fun CardRating.label(): String = when (this) {
    CardRating.BAD -> "Bad"
    CardRating.OK -> "OK"
    CardRating.GOOD -> "Good"
    CardRating.NEW -> "New"
}

@Composable
fun RatingDot(
    rating: CardRating,
    size: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(rating.color())
    )
}

/**
 * Segmented Red / Yellow / Green / New mastery bar for sets and the review dashboard.
 */
@Composable
fun MasteryBar(
    cards: List<Flashcard>,
    height: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val total = cards.size
    val good = cards.count { it.rating == CardRating.GOOD }
    val ok = cards.count { it.rating == CardRating.OK }
    val bad = cards.count { it.rating == CardRating.BAD }
    val newCount = (total - good - ok - bad).coerceAtLeast(0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (total == 0) return@Row
        if (good > 0) {
            Box(
                modifier = Modifier
                    .weight(good.toFloat())
                    .fillMaxHeight()
                    .background(RatingColors.Green)
            )
        }
        if (ok > 0) {
            Box(
                modifier = Modifier
                    .weight(ok.toFloat())
                    .fillMaxHeight()
                    .background(RatingColors.Yellow)
            )
        }
        if (bad > 0) {
            Box(
                modifier = Modifier
                    .weight(bad.toFloat())
                    .fillMaxHeight()
                    .background(RatingColors.Red)
            )
        }
        if (newCount > 0) {
            Box(
                modifier = Modifier
                    .weight(newCount.toFloat())
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            )
        }
    }
}
