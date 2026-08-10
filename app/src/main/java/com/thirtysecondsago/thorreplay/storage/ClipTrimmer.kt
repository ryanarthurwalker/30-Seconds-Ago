package com.thirtysecondsago.thorreplay.storage

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

object ClipTrimmer {
    private const val MIN_TRIM_MS = 500L

    fun writeTrimmedMp4(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        requestedStartMs: Long,
        requestedEndMs: Long,
    ): TrimmedRange {
        val durationMs = readDurationMs(context, sourceUri)
        val range = normalizedRange(requestedStartMs, requestedEndMs, durationMs)
        val actualStartUs = findPreviousVideoSyncUs(context, sourceUri, range.startMs * 1_000L)
        val requestedEndUs = range.endMs * 1_000L

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, sourceUri, null)
            val trackMap = mutableMapOf<Int, Int>()
            var bufferSize = 1 * 1024 * 1024
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            readRotation(context, sourceUri)?.let(muxer::setOrientationHint)

            for (trackIndex in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                trackMap[trackIndex] = muxer.addTrack(format)
                extractor.selectTrack(trackIndex)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
            }
            check(trackMap.isNotEmpty()) { "No audio or video tracks found" }

            muxer.start()
            extractor.seekTo(actualStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buffer = ByteBuffer.allocate(bufferSize.coerceAtMost(16 * 1024 * 1024))
            val info = MediaCodec.BufferInfo()
            var samplesWritten = 0

            while (true) {
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L || sampleTimeUs > requestedEndUs) break
                val inputTrack = extractor.sampleTrackIndex
                val outputTrack = trackMap[inputTrack]
                if (outputTrack != null && sampleTimeUs >= actualStartUs) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.set(
                        0,
                        size,
                        (sampleTimeUs - actualStartUs).coerceAtLeast(0L),
                        extractor.sampleFlags,
                    )
                    muxer.writeSampleData(outputTrack, buffer, info)
                    samplesWritten += 1
                }
                if (!extractor.advance()) break
            }
            check(samplesWritten > 0) { "No media samples found in selected range" }
            muxer.stop()
            muxer.release()
            muxer = null
            validateOutput(outputFile)
            return TrimmedRange(
                startMs = actualStartUs / 1_000L,
                endMs = range.endMs,
            )
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        } finally {
            extractor.release()
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    fun normalizedRange(startMs: Long, endMs: Long, durationMs: Long): TrimmedRange {
        require(durationMs >= MIN_TRIM_MS) { "Clip is too short to trim" }
        val safeStart = startMs.coerceIn(0L, durationMs - MIN_TRIM_MS)
        val safeEnd = endMs.coerceIn(safeStart + MIN_TRIM_MS, durationMs)
        return TrimmedRange(safeStart, safeEnd)
    }

    private fun findPreviousVideoSyncUs(context: Context, uri: Uri, requestedStartUs: Long): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val videoTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: error("No video track found")
            extractor.selectTrack(videoTrack)
            extractor.seekTo(requestedStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            extractor.sampleTime.coerceAtLeast(0L)
        } finally {
            extractor.release()
        }
    }

    private fun readDurationMs(context: Context, uri: Uri): Long {
        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: error("Unable to read clip duration")
        }
    }

    private fun readRotation(context: Context, uri: Uri): Int? {
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
            }
        }.getOrNull()
    }

    private fun validateOutput(file: File) {
        check(file.length() > 0L) { "Trimmed file is empty" }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val videoTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: error("Trimmed file has no video track")
            extractor.selectTrack(videoTrack)
            check(extractor.sampleTime >= 0L) {
                "Trimmed file has no playable video samples"
            }
        } finally {
            extractor.release()
        }
    }
}

data class TrimmedRange(val startMs: Long, val endMs: Long)
