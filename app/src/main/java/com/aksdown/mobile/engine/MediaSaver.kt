package com.aksdown.mobile.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.aksdown.mobile.data.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.coroutines.resume

data class SavedFile(val uri: Uri, val name: String, val size: Long, val path: String?, val resolution: String?)

/** Saves finished files to Internal Storage/Download/AKSdown/ via MediaStore (or the public dir on Android 8–9). */
object MediaSaver {
    const val FOLDER = "AKSdown"

    fun mime(f: Format) = if (f == Format.MP4) "video/mp4" else "audio/mpeg"
    private fun ext(f: Format) = if (f == Format.MP4) "mp4" else "mp3"

    /** Verifies the container signature and that Android can read a duration from it. */
    fun validate(file: File, format: Format): String? {
        if (!file.isFile || file.length() < 8 * 1024) return null
        val head = ByteArray(12)
        RandomAccessFile(file, "r").use { it.readFully(head) }
        val sigOk = when (format) {
            Format.MP4 -> String(head, 4, 4, Charsets.US_ASCII) == "ftyp"
            Format.MP3 -> (head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) ||
                ((head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xE0) == 0xE0)
        }
        if (!sigOk) return null
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            if (dur <= 0) return null
            if (format == Format.MP4) {
                val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (h == null || w == null) return null
                val shortSide = if (rot == 90 || rot == 270) w else minOf(w, h)
                "${shortSide}p"
            } else "320 kbps"
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    suspend fun save(ctx: Context, src: File, title: String?, format: Format, resolution: String?): SavedFile =
        withContext(Dispatchers.IO) {
            val base = UrlTools.safeFileName(title, "AKSdown_${System.currentTimeMillis()}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveMediaStore(ctx, src, base, format, resolution)
            else saveLegacy(ctx, src, base, format, resolution)
        }

    private fun saveMediaStore(ctx: Context, src: File, base: String, format: Format, res: String?): SavedFile {
        val resolver = ctx.contentResolver
        val relPath = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "/"
        val name = uniqueName(base, ext(format)) { candidate -> existsInMediaStore(ctx, candidate, relPath) }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime(format))
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw EngineError("err_save")
        try {
            resolver.openOutputStream(uri, "w")?.use { out -> src.inputStream().use { it.copyTo(out, 1 shl 16) } }
                ?: throw EngineError("err_save")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            if (e is EngineError) throw e
            throw EngineError(if ((e.message ?: "").contains("ENOSPC") || (e.message ?: "").contains("No space")) "err_storage" else "err_save", e.message)
        }
        // MediaStore may adjust the name; read back the final one.
        val finalName = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: name
        return SavedFile(uri, finalName, src.length(), null, res)
    }

    private fun existsInMediaStore(ctx: Context, name: String, relPath: String): Boolean = runCatching {
        ctx.contentResolver.query(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(name, relPath), null
        )?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private suspend fun saveLegacy(ctx: Context, src: File, base: String, format: Format, res: String?): SavedFile {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw EngineError("err_save", "mkdirs failed")
        val name = uniqueName(base, ext(format)) { File(dir, it).exists() }
        val dest = File(dir, name)
        try {
            src.inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
        } catch (e: Exception) {
            dest.delete(); throw EngineError("err_save", e.message)
        }
        val uri = scan(ctx, dest, mime(format)) ?: Uri.fromFile(dest)
        return SavedFile(uri, name, dest.length(), dest.absolutePath, res)
    }

    private suspend fun scan(ctx: Context, f: File, mime: String): Uri? = suspendCancellableCoroutine { c ->
        MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf(mime)) { _, uri -> if (c.isActive) c.resume(uri) }
    }

    private fun uniqueName(base: String, ext: String, exists: (String) -> Boolean): String {
        var n = "$base.$ext"; var i = 1
        while (exists(n) && i < 1000) { n = "$base ($i).$ext"; i++ }
        return n
    }

    fun rename(ctx: Context, uri: Uri, path: String?, newBase: String, format: Format): String? {
        val safe = UrlTools.safeFileName(newBase, "AKSdown")
        val name = "$safe.${ext(format)}"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ok = runCatching {
                ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }, null, null) > 0
            }.getOrDefault(false)
            if (ok) name else null
        } else {
            val f = File(path ?: return null); val dest = File(f.parentFile, name)
            if (dest.exists()) return null
            if (f.renameTo(dest)) { MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath, dest.absolutePath), null, null); name } else null
        }
    }
}
