package com.thaiflash.app.audio

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class ThaiVoiceInfo(
    val name: String,
    val displayName: String,
    val isNetwork: Boolean,
    val isHighQuality: Boolean,
    val voice: Voice
)

class TtsManager(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var englishTts: TextToSpeech? = null
    var isReady: Boolean = false
        private set
    var isEnglishReady: Boolean = false
        private set

    var currentVoiceName: String? = null
        private set

    private var pendingPreferredVoiceName: String? = null

    var speechRate: Float = 0.85f
        set(value) {
            field = value.coerceIn(0.5f, 1.2f)
            tts?.setSpeechRate(field)
        }

    init {
        tts = TextToSpeech(context.applicationContext, this, "com.google.android.tts")
        englishTts = TextToSpeech(context.applicationContext, { status ->
            if (status == TextToSpeech.SUCCESS) {
                englishTts?.setLanguage(Locale.US)
                englishTts?.setSpeechRate(0.95f)
                isEnglishReady = true
            }
        }, "com.google.android.tts")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val thaiLocale = Locale("th", "TH")
            val result = tts?.setLanguage(thaiLocale)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts = TextToSpeech(context.applicationContext) { fallbackStatus ->
                    if (fallbackStatus == TextToSpeech.SUCCESS) {
                        tts?.setLanguage(thaiLocale)
                        tts?.setSpeechRate(speechRate)
                        applyVoiceSelection(pendingPreferredVoiceName)
                        isReady = true
                        onInitComplete(true)
                    } else {
                        isReady = false
                        onInitComplete(false)
                    }
                }
            } else {
                tts?.setSpeechRate(speechRate)
                applyVoiceSelection(pendingPreferredVoiceName)
                isReady = true
                onInitComplete(true)
            }
        } else {
            isReady = false
            onInitComplete(false)
        }
    }

    fun applyPreferredVoice(voiceName: String?) {
        pendingPreferredVoiceName = voiceName
        if (isReady) {
            applyVoiceSelection(voiceName)
        }
    }

    fun getAvailableThaiVoices(): List<ThaiVoiceInfo> {
        val engine = tts ?: return emptyList()
        val voices = try {
            engine.voices
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        return voices.filter { voice ->
            voice.locale?.language.orEmpty().equals("th", ignoreCase = true)
        }.sortedWith(
            compareByDescending<Voice> { it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }
                .thenBy { it.name }
        ).mapIndexed { index, voice ->
            val isNetwork = voice.isNetworkConnectionRequired
            val nameLower = voice.name.lowercase()
            val gender = when {
                nameLower.contains("female") || nameLower.contains("cfn") || nameLower.contains("cfo") -> "Female"
                nameLower.contains("male") || nameLower.contains("thb") || nameLower.contains("tha") -> "Male"
                else -> "Voice ${index + 1}"
            }
            val typeLabel = if (isNetwork) "Neural HD" else "On-Device"
            ThaiVoiceInfo(
                name = voice.name,
                displayName = "$gender ($typeLabel)",
                isNetwork = isNetwork,
                isHighQuality = voice.quality >= Voice.QUALITY_HIGH || isNetwork,
                voice = voice
            )
        }
    }

    fun setVoiceByName(voiceName: String?): Boolean {
        val engine = tts ?: return false
        if (voiceName.isNullOrBlank()) {
            return autoSelectBestVoice()
        }
        val target = try {
            engine.voices?.firstOrNull { it.name == voiceName }
        } catch (_: Exception) {
            null
        }
        return if (target != null) {
            try {
                engine.voice = target
                currentVoiceName = target.name
                true
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
    }

    private fun applyVoiceSelection(preferredVoiceName: String?) {
        if (!preferredVoiceName.isNullOrBlank()) {
            if (setVoiceByName(preferredVoiceName)) return
        }
        autoSelectBestVoice()
    }

    private fun autoSelectBestVoice(): Boolean {
        val engine = tts ?: return false
        val available = getAvailableThaiVoices()
        val best = available.firstOrNull { it.isNetwork } ?: available.firstOrNull()
        if (best != null) {
            try {
                engine.voice = best.voice
                currentVoiceName = best.name
                return true
            } catch (_: Exception) {
            }
        }
        currentVoiceName = null
        return false
    }

    fun speak(text: String, rate: Float = speechRate) {
        if (!isReady || tts == null || text.isBlank()) return
        tts?.setSpeechRate(rate.coerceIn(0.5f, 1.2f))
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_THAI_${System.currentTimeMillis()}")
    }

    /**
     * Speaks [text] in Thai or English and suspends until playback finishes (or coroutine cancels).
     * Used by the Pimsleur hands-free Listen Set player.
     */
    suspend fun speakAndWait(
        text: String,
        isThai: Boolean,
        rate: Float = if (isThai) speechRate else 0.95f
    ) {
        if (text.isBlank()) return
        val engine = if (isThai) {
            if (!isReady) return
            tts
        } else {
            if (!isEnglishReady) return
            englishTts
        } ?: return

        val utteranceId = "LISTEN_${if (isThai) "TH" else "EN"}_${System.nanoTime()}"
        // Generous timeout safety net so playback never stalls if a TTS engine drops onDone
        val timeoutMs = (3500L + text.length * 140L).coerceIn(4500L, 18000L)

        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { cont ->
                val done = AtomicBoolean(false)
                fun finish() {
                    if (done.compareAndSet(false, true)) {
                        runCatching { engine.setOnUtteranceProgressListener(null) }
                        if (cont.isActive) cont.resumeWith(Result.success(Unit))
                    }
                }

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        if (id == utteranceId) finish()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        if (id == utteranceId) finish()
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utteranceId) finish()
                    }
                })

                engine.setSpeechRate(rate.coerceIn(0.5f, 1.2f))
                val res = runCatching {
                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                }.getOrDefault(TextToSpeech.ERROR)

                if (res != TextToSpeech.SUCCESS) {
                    finish()
                }

                cont.invokeOnCancellation {
                    runCatching {
                        engine.setOnUtteranceProgressListener(null)
                        engine.stop()
                    }
                }
            }
        }
    }

    fun stop() {
        tts?.stop()
        englishTts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false

        englishTts?.stop()
        englishTts?.shutdown()
        englishTts = null
        isEnglishReady = false
    }

    fun getInstallTtsDataIntent(): Intent {
        return Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    }
}
