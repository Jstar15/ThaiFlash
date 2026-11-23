package com.thaiflash.app.ui.study

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaiflash.app.audio.TtsManager
import com.thaiflash.app.data.model.CardRating
import com.thaiflash.app.data.model.Flashcard
import com.thaiflash.app.data.model.StudyDirection
import com.thaiflash.app.data.repository.FlashcardRepository
import com.thaiflash.app.data.repository.SettingsStore
import com.thaiflash.app.ui.components.RatingColors
import com.thaiflash.app.ui.components.RatingDot
import com.thaiflash.app.ui.components.WordBreakdownDialog
import com.thaiflash.app.ui.editor.AddEditCardSheet
import kotlin.random.Random

private data class StudyEntry(
    val cardId: String,
    val direction: StudyDirection, // Always resolved to TH_TO_EN or EN_TO_TH
    val isRetry: Boolean = false
)

/**
 * Unified 3D Flip Flashcard Player for both Set Study and Smart Review sessions.
 */
@Composable
fun StudyScreen(
    title: String,
    initialCards: List<Flashcard>,
    initialDirection: StudyDirection,
    isReviewMode: Boolean,
    repository: FlashcardRepository,
    settingsStore: SettingsStore,
    ttsManager: TtsManager,
    onBack: () -> Unit,
    onReviewMore: (() -> Unit)? = null
) {
    val db by repository.db.collectAsState()
    val settings by settingsStore.settings.collectAsState()
    val haptic = LocalHapticFeedback.current

    var sessionDirection by remember { mutableStateOf(initialDirection) }
    val queue = remember(initialCards) {
        mutableStateListOf<StudyEntry>().apply {
            initialCards.forEach { card ->
                val resolved = resolveDirection(initialDirection)
                add(StudyEntry(cardId = card.id, direction = resolved))
            }
        }
    }
    val retriedIds = remember(initialCards) { HashSet<String>() }

    var currentIndex by remember(initialCards) { mutableIntStateOf(0) }
    var isFlipped by remember(initialCards) { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<Flashcard?>(null) }
    var breakdownCardId by remember { mutableStateOf<String?>(null) }

    // Session summary counters
    var badRated by remember(initialCards) { mutableIntStateOf(0) }
    var okRated by remember(initialCards) { mutableIntStateOf(0) }
    var goodRated by remember(initialCards) { mutableIntStateOf(0) }
    var newlyMastered by remember(initialCards) { mutableIntStateOf(0) }

    val cardsById = remember(db.cards) { db.cards.associateBy { it.id } }
    val setsById = remember(db.sets) { db.sets.associateBy { it.id } }

    val currentEntry = queue.getOrNull(currentIndex)
    val currentCard = currentEntry?.let { cardsById[it.cardId] }
    val isComplete = queue.isEmpty() || currentIndex >= queue.size

    // Effective direction for the current card (allows instant toggle mid-session)
    val activeDirection = when (sessionDirection) {
        StudyDirection.TH_TO_EN -> StudyDirection.TH_TO_EN
        StudyDirection.EN_TO_TH -> StudyDirection.EN_TO_TH
        StudyDirection.MIXED -> currentEntry?.direction ?: StudyDirection.TH_TO_EN
    }

    // Auto-speak Thai when card appears (TH->EN) or when flipped to reveal Thai (EN->TH)
    LaunchedEffect(currentCard?.id, isFlipped, activeDirection, settings.autoSpeak) {
        val card = currentCard ?: return@LaunchedEffect
        if (!settings.autoSpeak) return@LaunchedEffect
        val shouldSpeak = (activeDirection == StudyDirection.TH_TO_EN && !isFlipped) ||
            (activeDirection == StudyDirection.EN_TO_TH && isFlipped)
        if (shouldSpeak) {
            ttsManager.speak(card.thai, settings.speechRate)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                ttsManager.stop()
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (!isComplete) {
                    val progressLabel = buildString {
                        append("Card ${currentIndex + 1} of ${queue.size}")
                        if (currentEntry?.isRetry == true) append(" · Retry")
                    }
                    Text(
                        text = progressLabel,
                        fontSize = 11.sp,
                        color = if (currentEntry?.isRetry == true) RatingColors.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isComplete) {
                // Quick TH->EN / EN->TH Direction Toggle Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            sessionDirection = when (sessionDirection) {
                                StudyDirection.TH_TO_EN -> StudyDirection.EN_TO_TH
                                StudyDirection.EN_TO_TH -> StudyDirection.TH_TO_EN
                                StudyDirection.MIXED -> StudyDirection.TH_TO_EN
                            }
                            isFlipped = false
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "Switch direction",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (sessionDirection) {
                                StudyDirection.TH_TO_EN -> "TH → EN"
                                StudyDirection.EN_TO_TH -> "EN → TH"
                                StudyDirection.MIXED -> "Mixed"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        if (!isComplete && queue.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (currentIndex.toFloat() / queue.size.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isComplete) {
            SessionSummaryView(
                totalReviewed = badRated + okRated + goodRated,
                badCount = badRated,
                okCount = okRated,
                goodCount = goodRated,
                newlyMastered = newlyMastered,
                streakDays = db.dailyStats.currentStreakDays,
                onReviewMore = onReviewMore,
                onDone = onBack
            )
        } else if (currentCard != null) {
            val setLabel = setsById[currentCard.setId]?.let { "${it.emoji} ${it.name}" }.orEmpty()

            // 3D Flip Card
            val rotation by animateFloatAsState(
                targetValue = if (isFlipped) 180f else 0f,
                animationSpec = tween(durationMillis = 380),
                label = "ThaiFlashFlip"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                    },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                if (rotation <= 90f) {
                    CardFrontFace(
                        card = currentCard,
                        setLabel = setLabel,
                        direction = activeDirection,
                        showRomanization = settings.showRomanization,
                        onSpeak = { rate -> ttsManager.speak(currentCard.thai, rate) },
                        normalRate = settings.speechRate,
                        onOpenBreakdown = { breakdownCardId = currentCard.id },
                        onFlip = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isFlipped = true
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationY = 180f }
                    ) {
                        CardBackFace(
                            card = currentCard,
                            setLabel = setLabel,
                            direction = activeDirection,
                            showRomanization = settings.showRomanization,
                            normalRate = settings.speechRate,
                            onSpeak = { rate -> ttsManager.speak(currentCard.thai, rate) },
                            onStopSpeaking = { ttsManager.stop() },
                            onScoreRecorded = { score -> repository.recordSpeechScore(currentCard.id, score) },
                            onOpenBreakdown = { breakdownCardId = currentCard.id },
                            onFlipToFront = { isFlipped = false },
                            onEdit = { editingCard = currentCard }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Red / Yellow / Green Rating Bar
            Text(
                text = if (isFlipped) "How well did you know this?" else "Tap card to reveal, or rate now",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RatingActionButton(
                    label = "Bad",
                    sublabel = "Red",
                    color = RatingColors.Red,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        ttsManager.stop()
                        badRated++
                        repository.recordRating(currentCard.id, CardRating.BAD)
                        if (isReviewMode && settings.retryRedInReview && retriedIds.add(currentCard.id)) {
                            queue.add(
                                StudyEntry(
                                    cardId = currentCard.id,
                                    direction = activeDirection,
                                    isRetry = true
                                )
                            )
                        }
                        isFlipped = false
                        currentIndex++
                    },
                    modifier = Modifier.weight(1f)
                )

                RatingActionButton(
                    label = "OK",
                    sublabel = "Yellow",
                    color = RatingColors.Yellow,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        ttsManager.stop()
                        okRated++
                        repository.recordRating(currentCard.id, CardRating.OK)
                        isFlipped = false
                        currentIndex++
                    },
                    modifier = Modifier.weight(1f)
                )

                RatingActionButton(
                    label = "Good",
                    sublabel = "Green",
                    color = RatingColors.Green,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        ttsManager.stop()
                        if (currentCard.rating != CardRating.GOOD) newlyMastered++
                        goodRated++
                        repository.recordRating(currentCard.id, CardRating.GOOD)
                        isFlipped = false
                        currentIndex++
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    editingCard?.let { cardToEdit ->
        AddEditCardSheet(
            sets = db.sets,
            initialSetId = cardToEdit.setId,
            editingCard = cardToEdit,
            repository = repository,
            settingsStore = settingsStore,
            ttsManager = ttsManager,
            onDismiss = { editingCard = null }
        )
    }

    breakdownCardId?.let { id ->
        val cardForBreakdown = cardsById[id]
        if (cardForBreakdown != null) {
            WordBreakdownDialog(
                card = cardForBreakdown,
                showRomanization = settings.showRomanization,
                normalRate = settings.speechRate,
                onPlayAudio = { text, rate -> ttsManager.speak(text, rate) },
                onBreakdownLoaded = { list -> repository.updateCardBreakdown(cardForBreakdown.id, list) },
                onDismiss = { breakdownCardId = null }
            )
        }
    }
}

@Composable
private fun CardFrontFace(
    card: Flashcard,
    setLabel: String,
    direction: StudyDirection,
    showRomanization: Boolean,
    normalRate: Float,
    onSpeak: (Float) -> Unit,
    onOpenBreakdown: () -> Unit,
    onFlip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFlip
            )
            .padding(22.dp)
    ) {
        // Top row: Set pill + status dot + Audio & Breakdown buttons (in TH->EN mode)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RatingDot(rating = card.rating, size = 10.dp)
                if (setLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = setLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (direction == StudyDirection.TH_TO_EN) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenBreakdown) {
                        Icon(
                            Icons.Default.FormatListBulleted,
                            contentDescription = "Word breakdown",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(onClick = { onSpeak(normalRate) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Listen",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { onSpeak(0.6f) }) {
                        Text(text = "🐢", fontSize = 16.sp)
                    }
                }
            }
        }

        // Center content
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (direction == StudyDirection.TH_TO_EN) {
                Text(
                    text = card.thai,
                    fontSize = dynamicThaiFontSize(card.thai),
                    lineHeight = dynamicThaiLineHeight(card.thai),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenBreakdown)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                if (showRomanization && card.romanization.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = card.romanization,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "TRANSLATE TO THAI",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = card.english,
                    fontSize = dynamicEnglishFontSize(card.english),
                    lineHeight = dynamicEnglishLineHeight(card.english),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Text(
            text = if (direction == StudyDirection.TH_TO_EN) {
                "Tap Thai for word breakdown · tap card to flip"
            } else {
                "Tap card to reveal answer"
            },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CardBackFace(
    card: Flashcard,
    setLabel: String,
    direction: StudyDirection,
    showRomanization: Boolean,
    normalRate: Float,
    onSpeak: (Float) -> Unit,
    onStopSpeaking: () -> Unit,
    onScoreRecorded: (Int) -> Unit,
    onOpenBreakdown: () -> Unit,
    onFlipToFront: () -> Unit,
    onEdit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top actions row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = setLabel.ifBlank { "Answer" },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenBreakdown, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.FormatListBulleted,
                        contentDescription = "Word breakdown",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit card",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                TextButton(onClick = onFlipToFront) {
                    Text(text = "Show Front", fontSize = 12.sp)
                }
            }
        }

        // Main revealed content — tapping the Thai text opens the word-by-word breakdown popup
        if (direction == StudyDirection.TH_TO_EN) {
            Text(
                text = card.english,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenBreakdown)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = card.thai,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap for word-by-word breakdown",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenBreakdown)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = card.thai,
                        fontSize = dynamicThaiFontSize(card.thai, compact = true),
                        lineHeight = dynamicThaiLineHeight(card.thai, compact = true),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tap for word-by-word breakdown",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = card.english,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showRomanization && card.romanization.isNotBlank()) {
            Text(
                text = card.romanization,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

        // Listen & Slow Audio Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onSpeak(normalRate) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Listen", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { onSpeak(0.6f) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🐢 Slow", fontSize = 12.sp)
            }
        }

        // "Say It" Pronunciation Match Card (Reused from LearnThai)
        SpeechCheckCard(
            targetThai = card.thai,
            onStopSpeaking = onStopSpeaking,
            onScoreRecorded = onScoreRecorded
        )
    }
}

@Composable
private fun RatingActionButton(
    label: String,
    sublabel: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.height(54.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = sublabel, fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun SessionSummaryView(
    totalReviewed: Int,
    badCount: Int,
    okCount: Int,
    goodCount: Int,
    newlyMastered: Int,
    streakDays: Int,
    onReviewMore: (() -> Unit)?,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = RatingColors.Green,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Session Complete!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "You reviewed $totalReviewed cards · 🔥 $streakDays-day streak",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (newlyMastered > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = RatingColors.Green.copy(alpha = 0.14f)
            ) {
                Text(
                    text = "+$newlyMastered newly mastered (Green)!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RatingColors.Green,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryStatPill("Bad", badCount, RatingColors.Red, Modifier.weight(1f))
            SummaryStatPill("OK", okCount, RatingColors.Yellow, Modifier.weight(1f))
            SummaryStatPill("Good", goodCount, RatingColors.Green, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (onReviewMore != null) {
            Button(
                onClick = onReviewMore,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Review 10 More", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        OutlinedButton(
            onClick = onDone,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Done", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SummaryStatPill(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = count.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

private fun resolveDirection(dir: StudyDirection): StudyDirection = when (dir) {
    StudyDirection.TH_TO_EN -> StudyDirection.TH_TO_EN
    StudyDirection.EN_TO_TH -> StudyDirection.EN_TO_TH
    StudyDirection.MIXED -> if (Random.nextBoolean()) StudyDirection.TH_TO_EN else StudyDirection.EN_TO_TH
}

private fun dynamicThaiFontSize(text: String, compact: Boolean = false): TextUnit = when {
    text.length <= 12 -> if (compact) 34.sp else 46.sp
    text.length <= 26 -> if (compact) 26.sp else 34.sp
    else -> if (compact) 22.sp else 26.sp
}

private fun dynamicThaiLineHeight(text: String, compact: Boolean = false): TextUnit = when {
    text.length <= 12 -> if (compact) 42.sp else 56.sp
    text.length <= 26 -> if (compact) 34.sp else 44.sp
    else -> if (compact) 30.sp else 34.sp
}

private fun dynamicEnglishFontSize(text: String): TextUnit = when {
    text.length <= 20 -> 30.sp
    text.length <= 45 -> 24.sp
    else -> 20.sp
}

private fun dynamicEnglishLineHeight(text: String): TextUnit = when {
    text.length <= 20 -> 38.sp
    text.length <= 45 -> 30.sp
    else -> 26.sp
}
