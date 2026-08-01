package com.thirtysecondsago.thorreplay.input

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.keyBindingDataStore by preferencesDataStore("thor_replay_key_binding")

data class KeyBinding(
    val keyCode: Int = KeyEvent.KEYCODE_UNKNOWN,
    val scanCode: Int = 0,
    val source: Int = 0,
    val deviceName: String = "",
    val vendorId: Int = 0,
    val productId: Int = 0,
    val consumeEvent: Boolean = true,
    val testModeEnabled: Boolean = false,
) {
    val keyLabel: String
        get() = if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            "Not selected"
        } else {
            KeyEvent.keyCodeToString(keyCode)
        }
}

data class KeyCaptureEvent(
    val keyCode: Int,
    val scanCode: Int,
    val source: Int,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
) {
    val summary: String
        get() = "${KeyEvent.keyCodeToString(keyCode)} scan=$scanCode source=${source.toString(16)}"
}

data class ControllerDevice(
    val id: Int,
    val name: String,
    val descriptor: String,
    val vendorId: Int,
    val productId: Int,
    val sources: Int,
) {
    val sourceLabel: String
        get() = buildList {
            if (sources hasSource InputDevice.SOURCE_GAMEPAD) add("gamepad")
            if (sources hasSource InputDevice.SOURCE_JOYSTICK) add("joystick")
            if (sources hasSource InputDevice.SOURCE_DPAD) add("d-pad")
            if (sources hasSource InputDevice.SOURCE_KEYBOARD) add("keyboard")
        }.ifEmpty { listOf("unknown") }.joinToString(", ")

    val identityLabel: String
        get() = if (vendorId != 0 || productId != 0) {
            "vendor=$vendorId product=$productId"
        } else {
            "vendor/product unavailable"
        }
}

private infix fun Int.hasSource(source: Int): Boolean = this and source == source

class KeyBindingRepository(private val context: Context) {
    val binding: Flow<KeyBinding> = context.keyBindingDataStore.data.map { prefs ->
        KeyBinding(
            keyCode = prefs[Keys.keyCode] ?: KeyEvent.KEYCODE_UNKNOWN,
            scanCode = prefs[Keys.scanCode] ?: 0,
            source = prefs[Keys.source] ?: 0,
            deviceName = prefs[Keys.deviceName] ?: "",
            vendorId = prefs[Keys.vendorId] ?: 0,
            productId = prefs[Keys.productId] ?: 0,
            consumeEvent = prefs[Keys.consumeEvent] ?: true,
            testModeEnabled = prefs[Keys.testModeEnabled] ?: false,
        )
    }

    suspend fun saveKey(event: KeyCaptureEvent) {
        context.keyBindingDataStore.edit {
            it[Keys.keyCode] = event.keyCode
            it[Keys.scanCode] = event.scanCode
            it[Keys.source] = event.source
            it[Keys.deviceName] = event.deviceName
            it[Keys.vendorId] = event.vendorId
            it[Keys.productId] = event.productId
        }
    }

    suspend fun clearKey() {
        context.keyBindingDataStore.edit {
            it[Keys.keyCode] = KeyEvent.KEYCODE_UNKNOWN
            it[Keys.scanCode] = 0
            it[Keys.source] = 0
            it[Keys.deviceName] = ""
            it[Keys.vendorId] = 0
            it[Keys.productId] = 0
        }
    }

    suspend fun setConsumeEvent(consume: Boolean) {
        context.keyBindingDataStore.edit { it[Keys.consumeEvent] = consume }
    }

    suspend fun setTestMode(enabled: Boolean) {
        context.keyBindingDataStore.edit { it[Keys.testModeEnabled] = enabled }
    }

    private object Keys {
        val keyCode = intPreferencesKey("selected_key_code")
        val scanCode = intPreferencesKey("selected_scan_code")
        val source = intPreferencesKey("selected_source")
        val deviceName = stringPreferencesKey("selected_device_name")
        val vendorId = intPreferencesKey("selected_vendor_id")
        val productId = intPreferencesKey("selected_product_id")
        val consumeEvent = booleanPreferencesKey("consume_event")
        val testModeEnabled = booleanPreferencesKey("test_mode_enabled")
    }
}

fun KeyEvent.toCaptureEvent(): KeyCaptureEvent {
    val inputDevice = device
    return KeyCaptureEvent(
        keyCode = keyCode,
        scanCode = scanCode,
        source = source,
        deviceName = inputDevice?.name.orEmpty(),
        vendorId = inputDevice?.vendorId ?: 0,
        productId = inputDevice?.productId ?: 0,
    )
}

class KeyEventDebouncer(private val debounceMs: Long = 1_000L) {
    private var lastAcceptedAtMs = Long.MIN_VALUE
    private var lastAcceptedKeyCode = KeyEvent.KEYCODE_UNKNOWN

    fun shouldAccept(keyCode: Int, eventTimeMs: Long): Boolean {
        val repeated = keyCode == lastAcceptedKeyCode &&
            eventTimeMs - lastAcceptedAtMs < debounceMs
        if (repeated) return false
        lastAcceptedKeyCode = keyCode
        lastAcceptedAtMs = eventTimeMs
        return true
    }
}
