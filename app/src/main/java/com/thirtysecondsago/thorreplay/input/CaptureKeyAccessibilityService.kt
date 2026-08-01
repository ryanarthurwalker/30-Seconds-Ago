package com.thirtysecondsago.thorreplay.input

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.thirtysecondsago.thorreplay.capture.ReplayBufferService
import com.thirtysecondsago.thorreplay.util.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CaptureKeyAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var keyBindingRepository: KeyBindingRepository
    private val debouncer = KeyEventDebouncer()
    @Volatile private var binding = KeyBinding()

    override fun onCreate() {
        super.onCreate()
        keyBindingRepository = KeyBindingRepository(applicationContext)
        scope.launch {
            keyBindingRepository.binding.collectLatest { binding = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val current = binding
        if (current.testModeEnabled) {
            Log.i(
                LogTags.INPUT,
                "key event action=${event.action} keyCode=${event.keyCode} scanCode=${event.scanCode} " +
                    "source=${event.source.toString(16)} device=${event.device?.name.orEmpty()}"
            )
        }
        if (
            event.action == KeyEvent.ACTION_UP &&
            event.matches(current) &&
            debouncer.shouldAccept(event.keyCode, event.eventTime)
        ) {
            Log.i(LogTags.INPUT, "Capture key pressed ${event.toCaptureEvent().summary}")
            val intent = ReplayBufferService.commandIntent(this, ReplayBufferService.ACTION_SAVE_REPLAY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
            return current.consumeEvent
        }
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

private fun KeyEvent.matches(binding: KeyBinding): Boolean {
    if (keyCode != binding.keyCode) return false
    if (binding.scanCode != 0 && scanCode != binding.scanCode) return false
    if (binding.source != 0 && source and binding.source == 0) return false
    return true
}
