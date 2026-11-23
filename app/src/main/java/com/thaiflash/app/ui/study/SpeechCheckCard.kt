package com.thaiflash.app.ui.study

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.thaiflash.app.domain.speech.TranscriptAlignment
import com.thaiflash.app.domain.speech.TranscriptScorer
import com.thaiflash.app.ui.components.RatingColors

private enum class SpeechCheckState { IDLE, LISTENING, DONE, ERROR }

/**
 * "Say It" pronunciation check reused from LearnThai.
 * Transcribes the user's spoken Thai (`th-TH`), scores 0–100% match via [TranscriptScorer],
 * and highlights matched vs. missed/different characters.
 */
@Composable
fun SpeechCheckCard(
    targetThai: String,
    onStopSpeaking: () -> Unit,
    onScoreRecorded: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var state by remember(targetThai) { mutableStateOf(SpeechCheckState.IDLE) }
    var transcript by remember(targetThai) { mutableStateOf("") }
    var score by remember(targetThai) { mutableIntStateOf(0) }
    var alignment by remember(targetThai) { mutableStateOf<TranscriptAlignment?>(null) }
    var message by remember(targetThai) { mutableStateOf<String?>(null) }
    val recognitionAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val beginListening: () -> Unit = {
        val engine = recognizer
        if (engine == null) {
            state = SpeechCheckState.ERROR
            message = "Speech recognition isn't available on this device."
        } else {
            onStopSpeaking()
            transcript = ""
            alignment = null
            message = null
            state = SpeechCheckState.LISTENING
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "th-TH")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            }
            try {
                engine.startListening(intent)
            } catch (_: Exception) {
                state = SpeechCheckState.ERROR
                message = "Couldn't start the mic — tap to try again."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginListening()
        } else {
            state = SpeechCheckState.ERROR
            message = "Microphone permission is needed to check pronunciation."
        }
    }

    fun requestStart() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) beginListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    DisposableEffect(Unit) {
        if (recognitionAvailable) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        state = SpeechCheckState.LISTENING
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        if (state == SpeechCheckState.LISTENING) {
                            state = SpeechCheckState.ERROR
                            message = friendlyError(error)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val heard = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        transcript = heard
                        val computedAlignment = TranscriptScorer.align(targetThai, heard)
                        val computedScore = TranscriptScorer.score(targetThai, heard)
                        alignment = computedAlignment
                        score = computedScore
                        message = null
                        state = SpeechCheckState.DONE
                        onScoreRecorded(computedScore)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (!partial.isNullOrBlank()) transcript = partial
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } else {
            state = SpeechCheckState.ERROR
            message = "Speech recognition isn't available on this device."
        }

        onDispose {
            runCatching { recognizer?.destroy() }
            recognizer = null
        }
    }

    LaunchedEffect(targetThai) {
        state = SpeechCheckState.IDLE
        transcript = ""
        alignment = null
        score = 0
        message = null
    }

    val mismatchColor = MaterialTheme.colorScheme.error
    val mismatchBackground = mismatchColor.copy(alpha = 0.12f)
    val missedColor = Color(0xFF8A5A00)
    val missedBackground = Color(0xFFFFE0B2).copy(alpha = 0.55f)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SAY IT IN THAI",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "pronunciation match",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (state) {
                SpeechCheckState.IDLE -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = recognitionAvailable) { requestStart() }
                        .padding(vertical = 2.dp)
                ) {
                    MicCircleButton(enabled = recognitionAvailable, onClick = { requestStart() })
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Tap mic & say this phrase",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Scores how closely your Thai matched",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SpeechCheckState.LISTENING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Listening (Thai)…",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (transcript.isNotBlank()) {
                            Text(
                                text = transcript,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SpeechCheckState.DONE -> Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$score%",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor(score)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = scoreLabel(score),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }

                    val alignmentNow = alignment
                    if (alignmentNow != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "TARGET",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        HighlightedThaiText(
                            text = alignmentNow.target,
                            matched = alignmentNow.targetMatched,
                            highlightColor = missedColor,
                            highlightBackground = missedBackground,
                            fontSize = 16.sp,
                            lineHeight = 22.sp
                        )

                        if (alignmentNow.heard.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "YOU SAID",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            HighlightedThaiText(
                                text = alignmentNow.heard,
                                matched = alignmentNow.heardMatched,
                                highlightColor = mismatchColor,
                                highlightBackground = mismatchBackground,
                                fontSize = 16.sp,
                                lineHeight = 22.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "red = different · amber = missed",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    if (score >= 95) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Every word matched ✓",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RatingColors.Green
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { requestStart() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Try again", fontSize = 12.sp)
                    }
                }

                SpeechCheckState.ERROR -> Column {
                    Text(
                        text = message ?: "Couldn't hear that — try again.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { requestStart() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Try again", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightedThaiText(
    text: String,
    matched: BooleanArray,
    highlightColor: Color,
    highlightBackground: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit
) {
    val annotated = buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val isMatch = matched.getOrElse(index) { true }
            var end = index
            while (end < text.length && matched.getOrElse(end) { true } == isMatch) end++
            val run = text.substring(index, end)
            if (isMatch) {
                append(run)
            } else {
                withStyle(
                    SpanStyle(
                        color = highlightColor,
                        background = highlightBackground,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(run)
                }
            }
            index = end
        }
    }

    Text(
        text = annotated,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun MicCircleButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Speak Thai",
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun scoreColor(score: Int): Color = when {
    score >= 85 -> RatingColors.Green
    score >= 60 -> RatingColors.Yellow
    else -> RatingColors.Red
}

private fun scoreLabel(score: Int): String = when {
    score >= 95 -> "spot on!"
    score >= 85 -> "great match"
    score >= 60 -> "close — check highlights"
    else -> "try again slowly"
}

private fun friendlyError(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't hear speech — tap and speak clearly."
    SpeechRecognizer.ERROR_NO_MATCH -> "Couldn't transcribe that — try a little slower."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
        "Network issue — check your connection."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser busy — tap again."
    SpeechRecognizer.ERROR_AUDIO -> "Mic busy — tap again."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow microphone access to use this."
    else -> "Couldn't hear that — tap to try again."
}
