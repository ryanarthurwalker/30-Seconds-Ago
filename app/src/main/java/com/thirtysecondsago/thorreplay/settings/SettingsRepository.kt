package com.thirtysecondsago.thorreplay.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.thirtysecondsago.thorreplay.capture.CaptureConfiguration
import com.thirtysecondsago.thorreplay.capture.CapturePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("thor_replay_settings")

data class AppSettings(
    val replayDurationSeconds: Int = 30,
    val width: Int = 1280,
    val height: Int = 720,
    val frameRate: Int = 30,
    val bitrateMbps: Int = 6,
    val audioEnabled: Boolean = false,
    val serviceStatus: String = "Stopped",
    val lastSavedClip: String = "No clip saved yet",
    val lastSavedUri: String = "",
    val outputFolderUri: String = "",
    val outputFolderLabel: String = "Movies/ThorReplay",
    val filenameTemplate: String = "ThorReplay_{datetime}",
    val selectedDisplayId: Int = 0,
    val selectedDisplayLabel: String = "Default display",
    val savedPopupDisplayId: Int = 0,
    val savedPopupDisplayLabel: String = "Default display",
)

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            replayDurationSeconds = prefs[Keys.replayDurationSeconds] ?: 30,
            width = prefs[Keys.width] ?: 1280,
            height = prefs[Keys.height] ?: 720,
            frameRate = prefs[Keys.frameRate] ?: 30,
            bitrateMbps = prefs[Keys.bitrateMbps] ?: 6,
            audioEnabled = prefs[Keys.audioEnabled] ?: false,
            serviceStatus = prefs[Keys.serviceStatus] ?: "Stopped",
            lastSavedClip = prefs[Keys.lastSavedClip] ?: "No clip saved yet",
            lastSavedUri = prefs[Keys.lastSavedUri] ?: "",
            outputFolderUri = prefs[Keys.outputFolderUri] ?: "",
            outputFolderLabel = prefs[Keys.outputFolderLabel] ?: "Movies/ThorReplay",
            filenameTemplate = prefs[Keys.filenameTemplate] ?: "ThorReplay_{datetime}",
            selectedDisplayId = prefs[Keys.selectedDisplayId] ?: 0,
            selectedDisplayLabel = prefs[Keys.selectedDisplayLabel] ?: "Default display",
            savedPopupDisplayId = prefs[Keys.savedPopupDisplayId] ?: (prefs[Keys.selectedDisplayId] ?: 0),
            savedPopupDisplayLabel = prefs[Keys.savedPopupDisplayLabel] ?: (prefs[Keys.selectedDisplayLabel] ?: "Default display"),
        )
    }

    suspend fun updateCaptureConfiguration(configuration: CaptureConfiguration) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.replayDurationSeconds] = configuration.replayDurationSeconds
            prefs[Keys.width] = configuration.width
            prefs[Keys.height] = configuration.height
            prefs[Keys.frameRate] = configuration.frameRate
            prefs[Keys.bitrateMbps] = configuration.videoBitrate / 1_000_000
            prefs[Keys.audioEnabled] = configuration.audioEnabled
        }
    }

    suspend fun updateReplayDuration(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.replayDurationSeconds] = seconds }
    }

    suspend fun updateResolution(width: Int, height: Int) {
        context.settingsDataStore.edit {
            it[Keys.width] = width
            it[Keys.height] = height
        }
    }

    suspend fun applyPreset(preset: CapturePreset) {
        context.settingsDataStore.edit {
            it[Keys.width] = preset.width
            it[Keys.height] = preset.height
            it[Keys.frameRate] = preset.frameRate
            it[Keys.bitrateMbps] = preset.bitrateMbps
        }
    }

    suspend fun updateFrameRate(frameRate: Int) {
        context.settingsDataStore.edit { it[Keys.frameRate] = frameRate }
    }

    suspend fun updateBitrate(mbps: Int) {
        context.settingsDataStore.edit { it[Keys.bitrateMbps] = mbps }
    }

    suspend fun updateAudioEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.audioEnabled] = enabled }
    }

    suspend fun updateServiceStatus(status: String) {
        context.settingsDataStore.edit { it[Keys.serviceStatus] = status }
    }

    suspend fun updateLastSavedClip(name: String, uri: String) {
        context.settingsDataStore.edit {
            it[Keys.lastSavedClip] = name
            it[Keys.lastSavedUri] = uri
        }
    }

    suspend fun updateOutputFolder(uri: String, label: String) {
        context.settingsDataStore.edit {
            it[Keys.outputFolderUri] = uri
            it[Keys.outputFolderLabel] = label
        }
    }

    suspend fun updateFilenameTemplate(template: String) {
        context.settingsDataStore.edit {
            it[Keys.filenameTemplate] = template.ifBlank { "ThorReplay_{datetime}" }
        }
    }

    suspend fun updateSelectedDisplay(displayId: Int, label: String) {
        context.settingsDataStore.edit {
            it[Keys.selectedDisplayId] = displayId
            it[Keys.selectedDisplayLabel] = label
        }
    }

    suspend fun updateSavedPopupDisplay(displayId: Int, label: String) {
        context.settingsDataStore.edit {
            it[Keys.savedPopupDisplayId] = displayId
            it[Keys.savedPopupDisplayLabel] = label
        }
    }

    private object Keys {
        val replayDurationSeconds = intPreferencesKey("replay_duration_seconds")
        val width = intPreferencesKey("capture_width")
        val height = intPreferencesKey("capture_height")
        val frameRate = intPreferencesKey("capture_frame_rate")
        val bitrateMbps = intPreferencesKey("capture_bitrate_mbps")
        val audioEnabled = booleanPreferencesKey("audio_enabled")
        val serviceStatus = stringPreferencesKey("service_status")
        val lastSavedClip = stringPreferencesKey("last_saved_clip")
        val lastSavedUri = stringPreferencesKey("last_saved_uri")
        val outputFolderUri = stringPreferencesKey("output_folder_uri")
        val outputFolderLabel = stringPreferencesKey("output_folder_label")
        val filenameTemplate = stringPreferencesKey("filename_template")
        val selectedDisplayId = intPreferencesKey("selected_display_id")
        val selectedDisplayLabel = stringPreferencesKey("selected_display_label")
        val savedPopupDisplayId = intPreferencesKey("saved_popup_display_id")
        val savedPopupDisplayLabel = stringPreferencesKey("saved_popup_display_label")
    }
}
