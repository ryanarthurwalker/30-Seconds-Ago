package com.thirtysecondsago.thorreplay.capture

data class CaptureConfiguration(
    val width: Int = 1280,
    val height: Int = 720,
    val frameRate: Int = 30,
    val videoBitrate: Int = 6_000_000,
    val replayDurationSeconds: Int = 30,
    val audioEnabled: Boolean = false,
)

enum class CapturePreset(
    val label: String,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val bitrateMbps: Int,
) {
    LowPower("720p30 Low", 1280, 720, 30, 4),
    Standard("720p30 Standard", 1280, 720, 30, 6),
    Smooth720("720p60 Smooth", 1280, 720, 60, 8),
    FullHd30("1080p30", 1920, 1080, 30, 8),
    FullHd60("1080p60 High", 1920, 1080, 60, 12),
}

data class CaptureStats(
    val bufferDurationMs: Long = 0,
    val approximateBufferBytes: Long = 0,
    val framesEncoded: Long = 0,
    val framesDropped: Long = 0,
    val activeResolution: String = "1280 x 720",
    val activeFrameRate: Int = 30,
    val activeBitrate: Int = 6_000_000,
    val encoderName: String = "Not started",
    val lastSaveDurationMs: Long = 0,
    val lastSaveResult: String = "No save yet",
)
