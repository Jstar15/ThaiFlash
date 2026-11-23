package com.thaiflash.app.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaiflash.app.data.model.CardRating
import com.thaiflash.app.data.model.ReviewFocus
import com.thaiflash.app.data.model.StudyDirection
import com.thaiflash.app.data.repository.FlashcardRepository
import com.thaiflash.app.data.repository.SettingsStore
import com.thaiflash.app.ui.components.MasteryBar
import com.thaiflash.app.ui.components.RatingColors

@Composable
fun ReviewHubScreen(
    repository: FlashcardRepository,
    settingsStore: SettingsStore,
    onStartReview: (ReviewFocus, StudyDirection) -> Unit
) {
    val db by repository.db.collectAsState()
    val settings by settingsStore.settings.collectAsState()

    var selectedFocus by remember { mutableStateOf(ReviewFocus.SMART_10) }
    var selectedDirection by remember(settings.defaultDirection) {
        mutableStateOf(settings.defaultDirection)
    }
    var showManageSetsDialog by remember { mutableStateOf(false) }

    val activeSets = remember(db.sets) { db.sets.filter { it.isActiveInReview() } }
    val activeSetIds = remember(activeSets) { activeSets.map { it.id }.toSet() }
    val cards = remember(db.cards, activeSetIds) { db.cards.filter { it.setId in activeSetIds } }

    val redCount = cards.count { it.rating == CardRating.BAD }
    val yellowCount = cards.count { it.rating == CardRating.OK }
    val greenCount = cards.count { it.rating == CardRating.GOOD }
    val newCount = cards.count { it.rating == CardRating.NEW }
    val masteredPct = if (cards.isEmpty()) 0 else (greenCount * 100) / cards.size

    val sessionCardCount = when (selectedFocus) {
        ReviewFocus.SMART_10 -> minOf(10, cards.size)
        ReviewFocus.RED_ONLY -> minOf(10, if (redCount > 0) redCount else cards.size)
        ReviewFocus.NEW_CARDS -> minOf(10, if (newCount > 0) newCount else cards.size)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Smart Review",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Active sets: ${activeSets.size} of ${db.sets.size} · ${cards.size} cards",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { showManageSetsDialog = true },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sets", fontSize = 12.sp)
            }
        }

        // Mastery Overview Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$masteredPct%",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Mastered across ${cards.size} cards",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🔥 ${db.dailyStats.currentStreakDays}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${db.dailyStats.reviewedToday} today",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                MasteryBar(cards = cards, height = 10.dp)
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BucketStatPill("Red", redCount, RatingColors.Red, Modifier.weight(1f))
                    BucketStatPill("Yellow", yellowCount, RatingColors.Yellow, Modifier.weight(1f))
                    BucketStatPill("Green", greenCount, RatingColors.Green, Modifier.weight(1f))
                    BucketStatPill("New", newCount, RatingColors.New, Modifier.weight(1f))
                }
            }
        }

        // Session Focus Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Session Focus",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFocus == ReviewFocus.SMART_10,
                        onClick = { selectedFocus = ReviewFocus.SMART_10 },
                        label = { Text("Smart 10", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = selectedFocus == ReviewFocus.RED_ONLY,
                        onClick = { selectedFocus = ReviewFocus.RED_ONLY },
                        label = { Text("Red Only", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = selectedFocus == ReviewFocus.NEW_CARDS,
                        onClick = { selectedFocus = ReviewFocus.NEW_CARDS },
                        label = { Text("New Cards", fontSize = 12.sp) }
                    )
                }

                Text(
                    text = when (selectedFocus) {
                        ReviewFocus.SMART_10 ->
                            "Picks up to 10 cards: ~55% struggling Red, ~25% Yellow/New, plus ~20% oldest Green cards to lock in long-term retention."
                        ReviewFocus.RED_ONLY ->
                            "Drills your Red & frequently missed cards until they turn Yellow or Green."
                        ReviewFocus.NEW_CARDS ->
                            "Introduces freshly added cards you haven't rated yet."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }

        // Direction Selector
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Card Front Side",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedDirection == StudyDirection.TH_TO_EN,
                        onClick = { selectedDirection = StudyDirection.TH_TO_EN },
                        label = { Text("Thai → English", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = selectedDirection == StudyDirection.EN_TO_TH,
                        onClick = { selectedDirection = StudyDirection.EN_TO_TH },
                        label = { Text("English → Thai", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = selectedDirection == StudyDirection.MIXED,
                        onClick = { selectedDirection = StudyDirection.MIXED },
                        label = { Text("Mixed", fontSize = 12.sp) }
                    )
                }
            }
        }

        // Start Review CTA
        Button(
            onClick = { onStartReview(selectedFocus, selectedDirection) },
            enabled = cards.isNotEmpty(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (cards.isEmpty()) {
                    "Add Cards First to Start Review"
                } else {
                    "Start Review ($sessionCardCount Cards)"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showManageSetsDialog) {
        val cardCounts = remember(db.cards) { db.cards.groupingBy { it.setId }.eachCount() }
        AlertDialog(
            onDismissRequest = { showManageSetsDialog = false },
            title = { Text("Active Sets in Smart Review", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Only checked sets appear in Smart Review. Studying a set also activates it automatically.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    db.sets.forEach { set ->
                        val active = set.isActiveInReview()
                        val count = cardCounts[set.id] ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { repository.setIncludeInReview(set.id, !active) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = active,
                                onCheckedChange = { repository.setIncludeInReview(set.id, it) }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${set.emoji} ${set.name}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$count cards",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageSetsDialog = false }) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun BucketStatPill(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.13f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = count.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}
