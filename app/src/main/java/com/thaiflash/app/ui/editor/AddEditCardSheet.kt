package com.thaiflash.app.ui.editor

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.thaiflash.app.audio.TtsManager
import com.thaiflash.app.data.model.Flashcard
import com.thaiflash.app.data.model.FlashcardSet
import com.thaiflash.app.data.network.TranslationService
import com.thaiflash.app.data.repository.FlashcardRepository
import com.thaiflash.app.data.repository.SettingsStore
import com.thaiflash.app.ui.components.RatingColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lightning-fast Add / Edit Card bottom sheet.
 * Supports typing, pasting, or speaking English OR Thai -> instant auto-translation +
 * phonetic romanization preview that the user can freely edit before saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCardSheet(
    sets: List<FlashcardSet>,
    initialSetId: String?,
    editingCard: Flashcard?,
    repository: FlashcardRepository,
    settingsStore: SettingsStore,
    ttsManager: TtsManager,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val defaultSetId = editingCard?.setId
        ?: initialSetId
        ?: settingsStore.current.lastUsedSetId?.takeIf { id -> sets.any { it.id == id } }
        ?: sets.firstOrNull()?.id.orEmpty()

    var selectedSetId by remember(editingCard?.id, initialSetId) { mutableStateOf(defaultSetId) }
    var showSetDropdown by remember { mutableStateOf(false) }

    var english by remember(editingCard?.id) { mutableStateOf(editingCard?.english.orEmpty()) }
    var thai by remember(editingCard?.id) { mutableStateOf(editingCard?.thai.orEmpty()) }
    var romanization by remember(editingCard?.id) { mutableStateOf(editingCard?.romanization.orEmpty()) }

    // Track which side the user is actively driving so auto-translate never clobbers manual overrides
    var activeSource by remember(editingCard?.id) { mutableStateOf<String?>(null) } // "EN" or "TH"
    var manualOverrideTarget by remember(editingCard?.id) { mutableStateOf(editingCard != null) }

    var isTranslating by remember { mutableStateOf(false) }
    var translateStatus by remember { mutableStateOf<String?>(null) }
    var listeningLang by remember { mutableStateOf<String?>(null) } // "en-US" or "th-TH"
    var speechError by remember { mutableStateOf<String?>(null) }
    var pendingSpeechLang by remember { mutableStateOf<String?>(null) }

    val recognitionAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun triggerTranslation(sourceText: String, fromThai: Boolean) {
        if (sourceText.isBlank()) return
        scope.launch {
            isTranslating = true
            translateStatus = null
            val res = TranslationService.autoTranslate(sourceText)
            if (fromThai) {
                if (res.english.isNotBlank()) english = res.english
                if (res.romanization.isNotBlank()) romanization = res.romanization
            } else {
                if (res.thai.isNotBlank()) thai = res.thai
                if (res.romanization.isNotBlank()) romanization = res.romanization
            }
            translateStatus = if (res.isOfflineFallback) {
                "Offline — enter or edit translation manually"
            } else {
                "Auto-translated ✓ (tap any field to edit)"
            }
            isTranslating = false
        }
    }

    val startVoiceInput: (String) -> Unit = { langTag ->
        val engine = recognizer
        if (engine == null) {
            speechError = "Speech recognition isn't available on this device."
        } else {
            ttsManager.stop()
            speechError = null
            listeningLang = langTag
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            try {
                engine.startListening(intent)
            } catch (_: Exception) {
                listeningLang = null
                speechError = "Couldn't start mic — tap to try again."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val lang = pendingSpeechLang
        pendingSpeechLang = null
        if (granted && lang != null) {
            startVoiceInput(lang)
        } else if (!granted) {
            speechError = "Allow microphone access to speak new phrases."
        }
    }

    fun requestVoice(langTag: String) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startVoiceInput(langTag)
        } else {
            pendingSpeechLang = langTag
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        if (recognitionAvailable) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        listeningLang = null
                        speechError = "Didn't catch that — tap mic to try again."
                    }

                    override fun onResults(results: Bundle?) {
                        val heard = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                            .trim()
                        val wasThai = listeningLang == "th-TH" || TranslationService.containsThai(heard)
                        listeningLang = null
                        if (heard.isNotBlank()) {
                            manualOverrideTarget = false
                            if (wasThai) {
                                thai = heard
                                activeSource = "TH"
                                triggerTranslation(heard, fromThai = true)
                            } else {
                                english = heard
                                activeSource = "EN"
                                triggerTranslation(heard, fromThai = false)
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (!partial.isNullOrBlank()) {
                            if (listeningLang == "th-TH") thai = partial else english = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
        onDispose {
            runCatching { recognizer?.destroy() }
            recognizer = null
        }
    }

    // Debounced auto-translate when typing or pasting
    LaunchedEffect(english, activeSource, manualOverrideTarget) {
        if (activeSource == "EN" && !manualOverrideTarget && english.trim().length >= 2) {
            delay(550)
            val inputIsThai = TranslationService.containsThai(english)
            if (inputIsThai) {
                // User pasted Thai into the English box — smartly move it!
                val pastedThai = english.trim()
                thai = pastedThai
                english = ""
                activeSource = "TH"
                triggerTranslation(pastedThai, fromThai = true)
            } else {
                triggerTranslation(english, fromThai = false)
            }
        }
    }

    LaunchedEffect(thai, activeSource, manualOverrideTarget) {
        if (activeSource == "TH" && !manualOverrideTarget && thai.trim().isNotEmpty()) {
            delay(550)
            triggerTranslation(thai, fromThai = true)
        }
    }

    val duplicate = remember(thai, english, editingCard?.id) {
        if (thai.isNotBlank() || english.isNotBlank()) {
            repository.findDuplicate(thai, english, excludeCardId = editingCard?.id)
        } else null
    }
    val duplicateSetName = remember(duplicate, sets) {
        duplicate?.let { dup -> sets.firstOrNull { it.id == dup.setId }?.name }
    }

    val selectedSet = sets.firstOrNull { it.id == selectedSetId }
    val canSave = thai.isNotBlank() && english.isNotBlank() && selectedSetId.isNotBlank()

    fun saveCard(keepOpenForAnother: Boolean) {
        if (!canSave) return
        settingsStore.setLastUsedSetId(selectedSetId)
        if (editingCard != null) {
            repository.updateCard(
                cardId = editingCard.id,
                setId = selectedSetId,
                thai = thai,
                english = english,
                romanization = romanization
            )
            onSaved()
            onDismiss()
        } else {
            repository.addCard(
                setId = selectedSetId,
                thai = thai,
                english = english,
                romanization = romanization
            )
            onSaved()
            if (keepOpenForAnother) {
                english = ""
                thai = ""
                romanization = ""
                activeSource = null
                manualOverrideTarget = false
                translateStatus = "Saved ✓ Ready for next phrase!"
            } else {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingCard != null) "Edit Flashcard" else "New Flashcard",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Set Picker
            Box {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSetDropdown = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selectedSet?.let { "${it.emoji}  ${it.name}" } ?: "Select a Set",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose Set")
                    }
                }
                DropdownMenu(
                    expanded = showSetDropdown,
                    onDismissRequest = { showSetDropdown = false }
                ) {
                    sets.forEach { set ->
                        DropdownMenuItem(
                            text = { Text("${set.emoji}  ${set.name}") },
                            onClick = {
                                selectedSetId = set.id
                                showSetDropdown = false
                            }
                        )
                    }
                }
            }

            // Quick Voice-to-Card Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VoiceCaptureButton(
                    label = if (listeningLang == "en-US") "Listening EN…" else "Speak English",
                    isListening = listeningLang == "en-US",
                    enabled = recognitionAvailable && listeningLang == null,
                    onClick = { requestVoice("en-US") },
                    modifier = Modifier.weight(1f)
                )
                VoiceCaptureButton(
                    label = if (listeningLang == "th-TH") "Listening TH…" else "Speak Thai",
                    isListening = listeningLang == "th-TH",
                    enabled = recognitionAvailable && listeningLang == null,
                    onClick = { requestVoice("th-TH") },
                    modifier = Modifier.weight(1f)
                )
            }

            if (speechError != null) {
                Text(
                    text = speechError.orEmpty(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // English Field
            OutlinedTextField(
                value = english,
                onValueChange = {
                    english = it
                    if (activeSource == "TH") {
                        manualOverrideTarget = true
                    } else {
                        activeSource = "EN"
                        manualOverrideTarget = false
                    }
                },
                label = { Text("English (type, paste, or speak)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    if (english.isNotBlank()) {
                        IconButton(onClick = {
                            english = ""
                            activeSource = null
                            manualOverrideTarget = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )

            // Thai Script Field
            OutlinedTextField(
                value = thai,
                onValueChange = {
                    thai = it
                    if (activeSource == "EN") {
                        manualOverrideTarget = true
                    } else {
                        activeSource = "TH"
                        manualOverrideTarget = false
                    }
                },
                label = { Text("Thai Script (ภาษาไทย)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        if (thai.isNotBlank()) {
                            IconButton(onClick = { ttsManager.speak(thai) }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Preview Thai audio",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            )

            // Romanization Field
            OutlinedTextField(
                value = romanization,
                onValueChange = { romanization = it },
                label = { Text("Romanization (optional, editable)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            // Auto-translate status + Manual Re-translate button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = translateStatus ?: "Type or speak either side to auto-translate",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        manualOverrideTarget = false
                        when {
                            activeSource == "TH" && thai.isNotBlank() -> triggerTranslation(thai, fromThai = true)
                            english.isNotBlank() -> triggerTranslation(english, fromThai = false)
                            thai.isNotBlank() -> triggerTranslation(thai, fromThai = true)
                        }
                    },
                    enabled = !isTranslating && (english.isNotBlank() || thai.isNotBlank())
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Translate", fontSize = 12.sp)
                }
            }

            // Duplicate Warning
            if (duplicate != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RatingColors.Yellow.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = RatingColors.Yellow,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Similar card already in \"${duplicateSetName ?: "another set"}\"",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Save Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (editingCard == null) {
                    OutlinedButton(
                        onClick = { saveCard(keepOpenForAnother = true) },
                        enabled = canSave,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save & Add More", fontSize = 13.sp)
                    }
                }
                Button(
                    onClick = { saveCard(keepOpenForAnother = false) },
                    enabled = canSave,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (editingCard != null) "Save Changes" else "Save Card", fontSize = 13.sp)
                }
            }

            // Edit-only actions (Reset Progress & Delete)
            if (editingCard != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            repository.resetCardProgress(editingCard.id)
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Progress", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = {
                            repository.deleteCard(editingCard.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete Card", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceCaptureButton(
    label: String,
    isListening: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isListening) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        },
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isListening) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isListening) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
        }
    }
}
