package com.thaiflash.app.ui.sets

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaiflash.app.audio.TtsManager
import com.thaiflash.app.data.model.CardRating
import com.thaiflash.app.data.model.Flashcard
import com.thaiflash.app.data.model.FlashcardSet
import com.thaiflash.app.data.repository.FlashcardRepository
import com.thaiflash.app.data.repository.SettingsStore
import com.thaiflash.app.ui.components.MasteryBar
import com.thaiflash.app.ui.components.RatingColors
import com.thaiflash.app.ui.editor.AddEditCardSheet

private val SET_EMOJIS = listOf("🇹🇭", "🍜", "🚕", "💬", "🛍️", "🏥", "✈️", "💼", "❤️", "📚")

@Composable
fun SetsScreen(
    repository: FlashcardRepository,
    settingsStore: SettingsStore,
    ttsManager: TtsManager,
    onOpenSet: (String) -> Unit,
    onQuickStudySet: (String) -> Unit
) {
    val db by repository.db.collectAsState()
    var showCreateSetDialog by remember { mutableStateOf(false) }
    var renamingSet by remember { mutableStateOf<FlashcardSet?>(null) }
    var deletingSet by remember { mutableStateOf<FlashcardSet?>(null) }
    var showAddCardSheet by remember { mutableStateOf(false) }

    val cardsBySet = remember(db.cards) { db.cards.groupBy { it.setId } }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ThaiFlash",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${db.sets.size} sets · ${db.cards.size} cards",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = { showCreateSetDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Set", fontSize = 13.sp)
                    }
                }
            }

            if (db.sets.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No flashcard sets yet", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Create your first set to start adding Thai words & phrases on the fly.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(onClick = { showCreateSetDialog = true }) {
                                Text("Create First Set")
                            }
                        }
                    }
                }
            } else {
                items(db.sets, key = { it.id }) { set ->
                    val setCards = cardsBySet[set.id].orEmpty()
                    SetOverviewCard(
                        set = set,
                        cards = setCards,
                        onOpen = { onOpenSet(set.id) },
                        onStudy = { if (setCards.isNotEmpty()) onQuickStudySet(set.id) else onOpenSet(set.id) },
                        onToggleReview = { repository.setIncludeInReview(set.id, !set.isActiveInReview()) },
                        onRename = { renamingSet = set },
                        onDelete = { deletingSet = set }
                    )
                }
            }
        }

        // Quick Add Card FAB
        if (db.sets.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showAddCardSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Flashcard")
            }
        }
    }

    if (showCreateSetDialog) {
        SetNameDialog(
            title = "Create New Set",
            initialName = "",
            initialEmoji = "📚",
            confirmLabel = "Create",
            onDismiss = { showCreateSetDialog = false },
            onConfirm = { name, emoji ->
                val created = repository.createSet(name, emoji)
                settingsStore.setLastUsedSetId(created.id)
                showCreateSetDialog = false
            }
        )
    }

    renamingSet?.let { set ->
        SetNameDialog(
            title = "Edit Set",
            initialName = set.name,
            initialEmoji = set.emoji,
            confirmLabel = "Save",
            onDismiss = { renamingSet = null },
            onConfirm = { name, emoji ->
                repository.updateSet(set.id, name, emoji)
                renamingSet = null
            }
        )
    }

    deletingSet?.let { set ->
        val count = cardsBySet[set.id]?.size ?: 0
        AlertDialog(
            onDismissRequest = { deletingSet = null },
            title = { Text("Delete \"${set.name}\"?") },
            text = { Text("This will permanently remove this set and its $count flashcards.") },
            confirmButton = {
                Button(
                    onClick = {
                        repository.deleteSet(set.id)
                        deletingSet = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSet = null }) { Text("Cancel") }
            }
        )
    }

    if (showAddCardSheet) {
        AddEditCardSheet(
            sets = db.sets,
            initialSetId = null,
            editingCard = null,
            repository = repository,
            settingsStore = settingsStore,
            ttsManager = ttsManager,
            onDismiss = { showAddCardSheet = false }
        )
    }
}

@Composable
private fun SetOverviewCard(
    set: FlashcardSet,
    cards: List<Flashcard>,
    onOpen: () -> Unit,
    onStudy: () -> Unit,
    onToggleReview: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val goodCount = cards.count { it.rating == CardRating.GOOD }
    val badCount = cards.count { it.rating == CardRating.BAD }
    val masteryPct = if (cards.isEmpty()) 0 else (goodCount * 100) / cards.size
    val activeInReview = set.isActiveInReview()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = set.emoji, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = set.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${cards.size} cards · $masteryPct% mastered" +
                            if (badCount > 0) " · $badCount red" else "",
                        fontSize = 12.sp,
                        color = if (badCount > 0) RatingColors.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (cards.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(onClick = onStudy),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Study set",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Set options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename / Icon") },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Set", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            MasteryBar(cards = cards, height = 7.dp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (activeInReview) {
                        RatingColors.Green.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onToggleReview)
                ) {
                    Text(
                        text = if (activeInReview) "In Smart Review ✓" else "+ Add to Smart Review",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (activeInReview) RatingColors.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SetNameDialog(
    title: String,
    initialName: String,
    initialEmoji: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Set Name (e.g. Street Food)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Icon", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SET_EMOJIS) { item ->
                        val selected = item == emoji
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { emoji = item }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(item, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, emoji) },
                enabled = name.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
