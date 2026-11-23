package com.thaiflash.app.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaiflash.app.audio.TtsManager
import com.thaiflash.app.data.model.Flashcard
import com.thaiflash.app.data.model.StudyDirection
import com.thaiflash.app.data.repository.SettingsStore
import kotlinx.coroutines.delay

/**
 * Hands-free Pimsleur-style audio player for a full flashcard set.
 * Plays each phrase twice (Thai -> English or English -> Thai) with natural repeat pauses,
 * then automatically advances through the entire set.
 */
@Composable
fun ListenSetScreen(
    title: String,
    cards: List<Flashcard>,
    settingsStore: SettingsStore,
    ttsManager: TtsManager,
    onBack: () -> Unit
) {
    val settings by settingsStore.settings.collectAsState()
    val view = LocalView.current

    var currentIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var direction by remember {
        mutableStateOf(
            if (settings.defaultDirection == StudyDirection.EN_TO_TH) StudyDirection.EN_TO_TH
            else StudyDirection.TH_TO_EN
        )
    }
    var slowMode by remember { mutableStateOf(false) }
    var extraTime by remember { mutableStateOf(false) }
    var loopSet by remember { mutableStateOf(true) }

    var passNumber by remember { mutableIntStateOf(1) } // 1 or 2
    var statusLabel by remember { mutableStateOf("Ready") }

    // Keep screen on gently while hands-free listening is active
    DisposableEffect(isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose {
            view.keepScreenOn = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.stop()
        }
    }

    val currentCard = cards.getOrNull(currentIndex)
    val thaiRate = if (slowMode) 0.60f else minOf(settings.speechRate, 0.80f)

    // Pimsleur hands-free audio loop with generous dynamic pauses scaled to phrase length
    LaunchedEffect(isPlaying, currentIndex, direction, slowMode, extraTime, cards) {
        if (!isPlaying || cards.isEmpty()) {
            ttsManager.stop()
            statusLabel = if (cards.isEmpty()) "No cards in set" else "Paused"
            return@LaunchedEffect
        }

        val card = cards.getOrNull(currentIndex) ?: return@LaunchedEffect
        val bonusMs = if (extraTime) 2000L else 0L
        val repeatPauseMs = (2800L + card.thai.length * 120L).coerceIn(3500L, 7000L) + bonusMs
        val recallPauseMs = repeatPauseMs + 1000L

        // Calm settle delay before starting the card
        delay(600)

        for (pass in 1..2) {
            passNumber = pass
            if (direction == StudyDirection.TH_TO_EN) {
                statusLabel = "Pass $pass/2 · Speaking Thai"
                ttsManager.speakAndWait(card.thai, isThai = true, rate = thaiRate)
                statusLabel = "Pass $pass/2 · Repeat Thai out loud…"
                delay(repeatPauseMs)

                statusLabel = "Pass $pass/2 · English meaning"
                ttsManager.speakAndWait(card.english, isThai = false, rate = 0.90f)
                statusLabel = "Pass $pass/2 · Say it again…"
                delay(repeatPauseMs)
            } else {
                statusLabel = "Pass $pass/2 · English prompt"
                ttsManager.speakAndWait(card.english, isThai = false, rate = 0.90f)
                statusLabel = "Pass $pass/2 · Try saying it in Thai…"
                delay(recallPauseMs)

                statusLabel = "Pass $pass/2 · Thai answer"
                ttsManager.speakAndWait(card.thai, isThai = true, rate = thaiRate)
                statusLabel = "Pass $pass/2 · Repeat Thai out loud…"
                delay(repeatPauseMs)
            }
        }

        statusLabel = "Next phrase…"
        delay(1200)

        // Advance to next card automatically
        if (currentIndex + 1 < cards.size) {
            currentIndex++
        } else if (loopSet) {
            currentIndex = 0
        } else {
            isPlaying = false
            statusLabel = "Finished set ✓"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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
                Text(
                    text = if (cards.isNotEmpty()) {
                        "Hands-Free Listen · ${currentIndex + 1} of ${cards.size}"
                    } else "Empty set",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Direction Toggle Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        ttsManager.stop()
                        direction = if (direction == StudyDirection.TH_TO_EN) {
                            StudyDirection.EN_TO_TH
                        } else {
                            StudyDirection.TH_TO_EN
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = "Flip order",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (direction == StudyDirection.TH_TO_EN) "TH → EN" else "EN → TH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (cards.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { ((currentIndex + 1).toFloat() / cards.size.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Hands-Free Display Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Top Status Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Center Phrase Content
                if (currentCard != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentCard.thai,
                            fontSize = if (currentCard.thai.length <= 18) 38.sp else 28.sp,
                            lineHeight = if (currentCard.thai.length <= 18) 48.sp else 36.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (settings.showRomanization && currentCard.romanization.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentCard.romanization,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = currentCard.english,
                            fontSize = 22.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Bottom Pimsleur Mode Hint
                Text(
                    text = if (direction == StudyDirection.TH_TO_EN) {
                        "Repeats Thai → English twice per card"
                    } else {
                        "Pimsleur Recall: English → pause → Thai (×2)"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Speed, Extra Pause & Loop Chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = slowMode,
                onClick = { slowMode = !slowMode },
                label = { Text(if (slowMode) "🐢 0.6x" else "🐢 Slow", fontSize = 12.sp) }
            )
            FilterChip(
                selected = extraTime,
                onClick = { extraTime = !extraTime },
                label = { Text(if (extraTime) "⏱ +2s ON" else "⏱ +2s Pause", fontSize = 12.sp) }
            )
            FilterChip(
                selected = loopSet,
                onClick = { loopSet = !loopSet },
                leadingIcon = {
                    Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(15.dp))
                },
                label = { Text(if (loopSet) "Loop" else "Once", fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Big Thumb-Friendly Media Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    ttsManager.stop()
                    if (cards.isNotEmpty()) {
                        currentIndex = if (currentIndex > 0) currentIndex - 1 else cards.lastIndex
                    }
                },
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(28.dp))
            }

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        if (isPlaying) {
                            ttsManager.stop()
                            isPlaying = false
                        } else {
                            isPlaying = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = {
                    ttsManager.stop()
                    if (cards.isNotEmpty()) {
                        currentIndex = (currentIndex + 1) % cards.size
                    }
                },
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(28.dp))
            }
        }
    }
}
