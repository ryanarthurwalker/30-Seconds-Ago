package com.thirtysecondsago.thorreplay.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ReplayStorage {
    const val RELATIVE_PATH = "Movies/ThorReplay"
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH-mm-ss")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    fun filenameFor(
        timestamp: LocalDateTime = LocalDateTime.now(),
        template: String = "ThorReplay_{datetime}",
        durationSeconds: Int = 30,
        width: Int = 1280,
        height: Int = 720,
        frameRate: Int = 30,
    ): String {
        val rawName = template.ifBlank { "ThorReplay_{datetime}" }
            .replace("{date}", timestamp.format(dateFormatter))
            .replace("{time}", timestamp.format(timeFormatter))
            .replace("{datetime}", timestamp.format(dateTimeFormatter))
            .replace("{duration}", "${durationSeconds}s")
            .replace("{resolution}", "${width}x$height")
            .replace("{fps}", "${frameRate}fps")
        val safeName = rawName
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "ThorReplay_${timestamp.format(dateTimeFormatter)}" }
        return if (safeName.endsWith(".mp4", ignoreCase = true)) safeName else "$safeName.mp4"
    }

    fun createTempRecordingFile(context: Context, filename: String): File {
        val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
        return File(dir, filename)
    }

    fun saveCompletedVideo(context: Context, source: File, filename: String, outputFolderUri: String): SavedVideo {
        if (outputFolderUri.isNotBlank()) {
            return saveDocumentVideo(context, source, filename, Uri.parse(outputFolderUri))
        }
        return saveMediaStoreVideo(context, source, filename)
    }

    private fun saveMediaStoreVideo(context: Context, source: File, filename: String): SavedVideo {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Video.Media.SIZE, source.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore did not return an output URI")
        val descriptor = resolver.openFileDescriptor(uri, "w")
            ?: run {
                resolver.delete(uri, null, null)
                error("Unable to open MediaStore output")
            }
        try {
            descriptor.use { pfd ->
                source.inputStream().use { input ->
                    java.io.FileOutputStream(pfd.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            return SavedVideo(filename, uri)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun saveDocumentVideo(context: Context, source: File, filename: String, folderUri: Uri): SavedVideo {
        val resolver = context.contentResolver
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: error("Unable to open selected output folder")
        val document = folder.createFile("video/mp4", filename)
            ?: error("Unable to create output file in selected folder")
        val descriptor = resolver.openFileDescriptor(document.uri, "w")
            ?: run {
                resolver.delete(document.uri, null, null)
                error("Unable to open selected-folder output")
            }
        try {
            descriptor.use { pfd ->
                source.inputStream().use { input ->
                    java.io.FileOutputStream(pfd.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return SavedVideo(filename, document.uri)
        } catch (error: Throwable) {
            resolver.delete(document.uri, null, null)
            throw error
        }
    }

    fun listSavedClips(context: Context, outputFolderUri: String): List<SavedClip> {
        val clips = mutableListOf<SavedClip>()
        clips += runCatching { listMediaStoreClips(context) }.getOrDefault(emptyList())
        if (outputFolderUri.isNotBlank()) {
            clips += runCatching {
                listDocumentClips(context, Uri.parse(outputFolderUri))
            }.getOrDefault(emptyList())
        }
        return clips.distinctBy { it.uri }.sortedByDescending { it.modifiedMs }
    }

    private fun listMediaStoreClips(context: Context): List<SavedClip> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
        )
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.RELATIVE_PATH}=?"
        } else {
            null
        }
        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("$RELATIVE_PATH/")
        } else {
            null
        }
        return resolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn)
                    if (!name.endsWith(".mp4", ignoreCase = true)) continue
                    val id = cursor.getLong(idColumn)
                    add(
                        SavedClip(
                            name = name,
                            uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString()),
                            sizeBytes = cursor.getLong(sizeColumn),
                            modifiedMs = cursor.getLong(modifiedColumn) * 1_000L,
                            source = "Movies/ThorReplay",
                            durationMs = cursor.getLong(durationColumn),
                            width = cursor.getInt(widthColumn),
                            height = cursor.getInt(heightColumn),
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    private fun listDocumentClips(context: Context, folderUri: Uri): List<SavedClip> {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        return folder.listFiles()
            .filter { it.isFile && it.name?.endsWith(".mp4", ignoreCase = true) == true }
            .map {
                val metadata = readVideoMetadata(context, it.uri)
                SavedClip(
                    name = it.name ?: "Unnamed clip",
                    uri = it.uri,
                    sizeBytes = it.length(),
                    modifiedMs = it.lastModified(),
                    source = "Selected folder",
                    durationMs = metadata.durationMs,
                    width = metadata.width,
                    height = metadata.height,
                )
            }
    }

    fun renameClip(context: Context, clip: SavedClip, requestedName: String): SavedClip {
        val newName = normalizedClipName(requestedName)
        require(newName.isNotBlank()) { "Clip name cannot be empty" }
        if (newName == clip.name) return clip

        if (clip.source == "Selected folder") {
            val document = DocumentFile.fromSingleUri(context, clip.uri)
                ?: error("Clip is no longer available")
            check(document.renameTo(newName)) { "Unable to rename clip" }
        } else {
            val updated = context.contentResolver.update(
                clip.uri,
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, newName)
                    put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1_000L)
                },
                null,
                null,
            )
            check(updated > 0) { "Clip is no longer available" }
        }
        return clip.copy(name = newName, modifiedMs = System.currentTimeMillis())
    }

    fun deleteClip(context: Context, clip: SavedClip) {
        val deleted = if (clip.source == "Selected folder") {
            DocumentFile.fromSingleUri(context, clip.uri)?.delete() == true
        } else {
            context.contentResolver.delete(clip.uri, null, null) > 0
        }
        check(deleted) { "Clip is no longer available or could not be deleted" }
    }

    fun trimClip(
        context: Context,
        clip: SavedClip,
        startMs: Long,
        endMs: Long,
        replaceOriginal: Boolean,
        outputFolderUri: String,
    ): TrimClipResult {
        val baseName = clip.name.removeSuffix(".mp4")
        val trimmedName = normalizedClipName("${baseName}_trimmed")
        val tempDirectory = File(context.cacheDir, "recordings").apply { mkdirs() }
        val tempFile = File.createTempFile("trim_", ".mp4", tempDirectory)
        return try {
            val actualRange = ClipTrimmer.writeTrimmedMp4(
                context = context,
                sourceUri = clip.uri,
                outputFile = tempFile,
                requestedStartMs = startMs,
                requestedEndMs = endMs,
            )
            val trimmedSize = tempFile.length()
            val saved = saveCompletedVideo(context, tempFile, trimmedName, outputFolderUri)
            var savedClip = clip.copy(
                name = saved.filename,
                uri = saved.uri,
                sizeBytes = trimmedSize,
                modifiedMs = System.currentTimeMillis(),
                source = if (outputFolderUri.isBlank()) "Movies/ThorReplay" else "Selected folder",
                durationMs = (actualRange.endMs - actualRange.startMs).coerceAtLeast(0L),
            )
            if (!replaceOriginal) {
                return TrimClipResult(savedClip, replacedOriginal = false, "Saved ${savedClip.name}")
            }

            val deletedOriginal = runCatching { deleteClip(context, clip) }.isSuccess
            if (!deletedOriginal) {
                return TrimClipResult(
                    savedClip,
                    replacedOriginal = false,
                    message = "Trimmed copy saved, but the original could not be deleted",
                )
            }
            savedClip = runCatching { renameClip(context, savedClip, clip.name) }.getOrDefault(savedClip)
            TrimClipResult(savedClip, replacedOriginal = true, "Replaced ${clip.name}")
        } finally {
            tempFile.delete()
        }
    }

    fun normalizedClipName(requestedName: String): String {
        val trimmedName = requestedName.trim()
        val nameWithoutExtension = if (trimmedName.endsWith(".mp4", ignoreCase = true)) {
            trimmedName.dropLast(4)
        } else {
            trimmedName
        }
        val safeName = nameWithoutExtension
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
        return if (safeName.isBlank()) "" else "$safeName.mp4"
    }

    private fun readVideoMetadata(context: Context, uri: Uri): VideoMetadata {
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                VideoMetadata(
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                    width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                    height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                )
            }
        }.getOrDefault(VideoMetadata())
    }
}

private data class VideoMetadata(
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
)

data class SavedVideo(
    val filename: String,
    val uri: Uri,
)

data class SavedClip(
    val name: String,
    val uri: Uri,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val source: String,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
)

data class TrimClipResult(
    val clip: SavedClip,
    val replacedOriginal: Boolean,
    val message: String,
)
