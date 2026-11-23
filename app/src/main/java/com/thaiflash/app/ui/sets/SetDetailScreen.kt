package com.thaiflash.app.ui.sets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaiflash.app.audio.TtsManager
import com.thaiflash.app.data.model.CardRating
import com.thaiflash.app.data.model.Flashcard
import com.thaiflash.app.data.model.SetStudyOrder
import com.thaiflash.app.data.repository.FlashcardRepository
import com.thaiflash.app.data.repository.SettingsStore
import com.thaiflash.app.ui.components.MasteryBar
import com.thaiflash.app.ui.components.RatingDot
import com.thaiflash.app.ui.components.WordBreakdownDialog
import com.thaiflash.app.ui.editor.AddEditCardSheet

@Composable
fun SetDetailScreen(
    setId: String,
    repository: FlashcardRepository,
    settingsStore: SettingsStore,
    ttsManager: TtsManager,
    onBack: () -> Unit,
    onStudySet: (SetStudyOrder) -> Unit,
    onListenSet: (SetStudyOrder) -> Unit
) {
    val db by repository.db.collectAsState()
    val settings by settingsStore.settings.collectAsState()

    val currentSet = db.sets.firstOrNull { it.id == setId }
    val cards = remember(db.cards, setId) { db.cards.filter { it.setId == setId } }

    var selectedOrder by remember { mutableStateOf(SetStudyOrder.IN_ORDER) }
    var showAddSheet by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<Flashcard?>(null) }
    var breakdownCardId by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSet?.let { "${it.emoji} ${it.name}" } ?: "Set",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val good = cards.count { it.rating == CardRating.GOOD }
                        val ok = cards.count { it.rating == CardRating.OK }
                        val bad = cards.count { it.rating == CardRating.BAD }
                        Text(
                            text = "${cards.size} cards · $good green · $ok yellow · $bad red",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                MasteryBar(cards = cards, height = 8.dp)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedOrder == SetStudyOrder.IN_ORDER,
                                onClick = { selectedOrder = SetStudyOrder.IN_ORDER },
                                label = { Text("In Order", fontSize = 12.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedOrder == SetStudyOrder.SHUFFLED,
                                onClick = { selectedOrder = SetStudyOrder.SHUFFLED },
                                label = { Text("Shuffled", fontSize = 12.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedOrder == SetStudyOrder.STRUGGLING_FIRST,
                                onClick = { selectedOrder = SetStudyOrder.STRUGGLING_FIRST },
                                label = { Text("Red First", fontSize = 12.sp) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onStudySet(selectedOrder) },
                            enabled = cards.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (cards.isEmpty()) "Study Set" else "Study (${cards.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { onListenSet(selectedOrder) },
                            enabled = cards.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Listen Set",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (cards.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("This set is empty", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Tap + to type, paste, or speak your first Thai or English phrase.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(cards, key = { it.id }) { card ->
                    CardListRow(
                        card = card,
                        showRomanization = settings.showRomanization,
                        onSpeak = { ttsManager.speak(card.thai, settings.speechRate) },
                        onBreakdown = { breakdownCardId = card.id },
                        onClick = { editingCard = card }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddSheet = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add card to set")
        }
    }

    if (showAddSheet) {
        AddEditCardSheet(
            sets = db.sets,
            initialSetId = setId,
            editingCard = null,
            repository = repository,
            settingsStore = settingsStore,
            ttsManager = ttsManager,
            onDismiss = { showAddSheet = false }
        )
    }

    editingCard?.let { card ->
        AddEditCardSheet(
            sets = db.sets,
            initialSetId = setId,
            editingCard = card,
            repository = repository,
            settingsStore = settingsStore,
            ttsManager = ttsManager,
            onDismiss = { editingCard = null }
        )
    }

    breakdownCardId?.let { id ->
        val card = cards.firstOrNull { it.id == id }
        if (card != null) {
            WordBreakdownDialog(
                card = card,
                showRomanization = settings.showRomanization,
                normalRate = settings.speechRate,
                onPlayAudio = { text, rate -> ttsManager.speak(text, rate) },
                onBreakdownLoaded = { list -> repository.updateCardBreakdown(card.id, list) },
                onDismiss = { breakdownCardId = null }
            )
        }
    }
}

@Composable
private fun CardListRow(
    card: Flashcard,
    showRomanization: Boolean,
    onSpeak: () -> Unit,
    onBreakdown: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RatingDot(rating = card.rating, size = 10.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.thai,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (showRomanization && card.romanization.isNotBlank()) {
                    Text(
                        text = card.romanization,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = card.english,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (card.bestSpeechScore != null) {
                    Text(
                        text = "Best speech: ${card.bestSpeechScore}%",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            IconButton(onClick = onBreakdown) {
                Icon(
                    Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = "Word breakdown",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = onSpeak) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Listen",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
