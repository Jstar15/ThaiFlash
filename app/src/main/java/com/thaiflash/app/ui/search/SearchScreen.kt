package com.thaiflash.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.thaiflash.app.data.repository.FlashcardRepository
import com.thaiflash.app.data.repository.SettingsStore
import com.thaiflash.app.ui.components.RatingDot
import com.thaiflash.app.ui.editor.AddEditCardSheet

@Composable
fun SearchScreen(
    repository: FlashcardRepository,
    settingsStore: SettingsStore,
    ttsManager: TtsManager
) {
    val db by repository.db.collectAsState()
    val settings by settingsStore.settings.collectAsState()

    var query by remember { mutableStateOf("") }
    var ratingFilter by remember { mutableStateOf<CardRating?>(null) }
    var editingCard by remember { mutableStateOf<Flashcard?>(null) }

    val setsById = remember(db.sets) { db.sets.associateBy { it.id } }

    val results = remember(db.cards, query, ratingFilter) {
        val q = query.trim().lowercase()
        db.cards.filter { card ->
            val matchesRating = ratingFilter == null || card.rating == ratingFilter
            val matchesText = q.isEmpty() ||
                card.english.lowercase().contains(q) ||
                card.thai.contains(q) ||
                card.romanization.lowercase().contains(q)
            matchesRating && matchesText
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = "Search All Sets",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Find any phrase by English, Thai, or romanization · tap to edit",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search English, Thai, or romanization…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Rating Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = ratingFilter == null,
                    onClick = { ratingFilter = null },
                    label = { Text("All (${db.cards.size})", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = ratingFilter == CardRating.BAD,
                    onClick = { ratingFilter = if (ratingFilter == CardRating.BAD) null else CardRating.BAD },
                    label = { Text("🔴 Red", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = ratingFilter == CardRating.OK,
                    onClick = { ratingFilter = if (ratingFilter == CardRating.OK) null else CardRating.OK },
                    label = { Text("🟡 OK", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = ratingFilter == CardRating.GOOD,
                    onClick = { ratingFilter = if (ratingFilter == CardRating.GOOD) null else CardRating.GOOD },
                    label = { Text("🟢 Good", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = ratingFilter == CardRating.NEW,
                    onClick = { ratingFilter = if (ratingFilter == CardRating.NEW) null else CardRating.NEW },
                    label = { Text("⚪ New", fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (results.isEmpty()) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No matching flashcards", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Try a different English word or clear the status filter.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.id }) { card ->
                    val set = setsById[card.setId]
                    SearchResultCard(
                        card = card,
                        setLabel = set?.let { "${it.emoji} ${it.name}" } ?: "Set",
                        showRomanization = settings.showRomanization,
                        onSpeak = { ttsManager.speak(card.thai, settings.speechRate) },
                        onClick = { editingCard = card }
                    )
                }
            }
        }
    }

    editingCard?.let { card ->
        AddEditCardSheet(
            sets = db.sets,
            initialSetId = card.setId,
            editingCard = card,
            repository = repository,
            settingsStore = settingsStore,
            ttsManager = ttsManager,
            onDismiss = { editingCard = null }
        )
    }
}

@Composable
private fun SearchResultCard(
    card: Flashcard,
    setLabel: String,
    showRomanization: Boolean,
    onSpeak: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
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
                    text = card.english,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = card.thai,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (showRomanization && card.romanization.isNotBlank()) {
                    Text(
                        text = card.romanization,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = setLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            IconButton(onClick = onSpeak) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Play Thai",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
