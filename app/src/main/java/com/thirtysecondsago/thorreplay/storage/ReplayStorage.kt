package com.thirtysecondsago.thorreplay.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
        clips += listMediaStoreClips(context)
        if (outputFolderUri.isNotBlank()) {
            clips += listDocumentClips(context, Uri.parse(outputFolderUri))
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
                SavedClip(
                    name = it.name ?: "Unnamed clip",
                    uri = it.uri,
                    sizeBytes = it.length(),
                    modifiedMs = it.lastModified(),
                    source = "Selected folder",
                )
            }
    }
}

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
)
