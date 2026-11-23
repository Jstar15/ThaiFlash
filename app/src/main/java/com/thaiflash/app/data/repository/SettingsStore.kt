package com.thaiflash.app.data.repository

import android.content.Context
import com.thaiflash.app.data.model.StudyDirection
import com.thaiflash.app.data.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive SharedPreferences store for user preferences (romanization, direction, TTS voice/speed).
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    val current: UserSettings
        get() = _settings.value

    fun setShowRomanization(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ROMAN, enabled).apply()
        _settings.value = _settings.value.copy(showRomanization = enabled)
    }

    fun setDefaultDirection(direction: StudyDirection) {
        prefs.edit().putString(KEY_DEFAULT_DIRECTION, direction.name).apply()
        _settings.value = _settings.value.copy(defaultDirection = direction)
    }

    fun setAutoSpeak(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SPEAK, enabled).apply()
        _settings.value = _settings.value.copy(autoSpeak = enabled)
    }

    fun setRetryRedInReview(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RETRY_RED, enabled).apply()
        _settings.value = _settings.value.copy(retryRedInReview = enabled)
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.6f, 1.1f)
        prefs.edit().putFloat(KEY_SPEECH_RATE, clamped).apply()
        _settings.value = _settings.value.copy(speechRate = clamped)
    }

    fun setSelectedVoice(voiceName: String?) {
        prefs.edit().putString(KEY_SELECTED_VOICE, voiceName).apply()
        _settings.value = _settings.value.copy(selectedVoice = voiceName)
    }

    fun setLastUsedSetId(setId: String?) {
        prefs.edit().putString(KEY_LAST_SET_ID, setId).apply()
        _settings.value = _settings.value.copy(lastUsedSetId = setId)
    }

    private fun load(): UserSettings {
        val dirName = prefs.getString(KEY_DEFAULT_DIRECTION, StudyDirection.TH_TO_EN.name)
        val direction = runCatching { StudyDirection.valueOf(dirName ?: "") }
            .getOrDefault(StudyDirection.TH_TO_EN)
        return UserSettings(
            showRomanization = prefs.getBoolean(KEY_SHOW_ROMAN, false),
            defaultDirection = direction,
            autoSpeak = prefs.getBoolean(KEY_AUTO_SPEAK, true),
            retryRedInReview = prefs.getBoolean(KEY_RETRY_RED, true),
            speechRate = prefs.getFloat(KEY_SPEECH_RATE, 0.85f).coerceIn(0.6f, 1.1f),
            selectedVoice = prefs.getString(KEY_SELECTED_VOICE, null),
            lastUsedSetId = prefs.getString(KEY_LAST_SET_ID, null)
        )
    }

    private companion object {
        const val PREFS_NAME = "thaiflash_settings"
        const val KEY_SHOW_ROMAN = "show_romanization"
        const val KEY_DEFAULT_DIRECTION = "default_direction"
        const val KEY_AUTO_SPEAK = "auto_speak"
        const val KEY_RETRY_RED = "retry_red_in_review"
        const val KEY_SPEECH_RATE = "speech_rate"
        const val KEY_SELECTED_VOICE = "selected_voice"
        const val KEY_LAST_SET_ID = "last_used_set_id"
    }
}
