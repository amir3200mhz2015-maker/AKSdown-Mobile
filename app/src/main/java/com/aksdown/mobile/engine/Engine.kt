package com.aksdown.mobile.engine

import android.content.Context
import android.util.Log
import com.aksdown.mobile.BuildConfig
import com.aksdown.mobile.data.Format
import com.aksdown.mobile.data.VideoMeta
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** A failure with a user-facing message key (err_*) plus the raw engine output for "Details". */
class EngineError(val key: String, val detail: String? = null) : Exception(key)

/**
 * On-device engine: embedded CPython 3.11 + yt-dlp zipapp (with yt-dlp-ejs) + FFmpeg (from the
 * youtubedl-android library) + QuickJS-NG for YouTube's JavaScript challenges. Everything runs
 * as child processes started from nativeLibraryDir, which Android allows to execute.
 */
object Engine {
    private const val TAG = "AKSEngine"
    private val mutex = Mutex()
    @Volatile var ready = false; private set

    private lateinit var app: Context
    private val qjs get() = File(app.applicationInfo.nativeLibraryDir, "libqjs.so")
    private val cacheDir get() = File(app.filesDir, "ytdlp-cache").apply { mkdirs() }

    suspend fun ensureInit(ctx: Context) = mutex.withLock {
        if (ready) return@withLock
        app = ctx.applicationContext
        withContext(Dispatchers.IO) {
            try {
                replaceBundledYtdlpIfAppUpdated()
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                ready = true
            } catch (t: Throwable) {
                Log.e(TAG, "init failed", t)
                throw EngineError("err_init", t.message)
            }
        }
    }

    /** The library only copies yt-dlp when missing; force a fresh copy when the app ships a new one. */
    private fun replaceBundledYtdlpIfAppUpdated() {
        val marker = File(app.noBackupFilesDir, "aks_ytdlp_version")
        val current = BuildConfig.YTDLP_VERSION
        if (!marker.exists() || marker.readText() != current) {
            File(app.noBackupFilesDir, "youtubedl-android/yt-dlp").deleteRecursively()
            marker.writeText(current)
        }
    }

    fun engineVersion(): String = "yt-dlp ${BuildConfig.YTDLP_VERSION} · FFmpeg · QuickJS-NG"

    private fun common(url: String): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        addOption("--no-playlist")
        addOption("--encoding", "utf-8")
        addOption("--no-js-runtimes")
        if (qjs.exists()) addOption("--js-runtimes", "quickjs:${qjs.absolutePath}")
        addOption("--cache-dir", cacheDir.absolutePath)
        addOption("--socket-timeout", "30")
        addOption("--retries", "10")
        addOption("--extractor-retries", "3")
        addOption("--no-color")
        // End of options: the URL can never be interpreted as a yt-dlp flag.
        addCommands(listOf("--"))
    }

    suspend fun fetchInfo(url: String): VideoMeta = withContext(Dispatchers.IO) {
        val req = common(url).apply {
            addOption("--dump-single-json")
            addOption("--skip-download")
        }
        val out = try {
            YoutubeDL.getInstance().execute(req, "info-${url.hashCode()}", null).out
        } catch (e: YoutubeDL.CanceledException) {
            throw EngineError("canceled")
        } catch (e: Exception) {
            throw mapError(e.message ?: "")
        }
        val json = runCatching { JSONObject(out.trim().lines().last { it.startsWith("{") }) }
            .getOrElse { throw EngineError("err_engine", out.take(2000)) }
        if (json.optString("_type") == "playlist") throw EngineError("err_playlist")
        if (json.optBoolean("is_live")) throw EngineError("err_live")
        val formats = json.optJSONArray("formats")
        var maxH = 0
        if (formats != null) for (i in 0 until formats.length()) {
            val f = formats.getJSONObject(i)
            if (f.optString("vcodec") != "none") maxH = maxOf(maxH, f.optInt("height"))
        }
        if (maxH == 0) maxH = json.optInt("height")
        VideoMeta(
            title = json.optString("title").takeIf { it.isNotBlank() },
            thumbnail = json.optString("thumbnail").takeIf { it.startsWith("https://") },
            durationSec = json.optDouble("duration").takeIf { !it.isNaN() }?.toLong(),
            extractor = json.optString("extractor_key").takeIf { it.isNotBlank() },
            uploader = json.optString("uploader").takeIf { it.isNotBlank() },
            maxHeight = maxH.takeIf { it > 0 },
            approxSize = json.optLong("filesize_approx").takeIf { it > 0 }
                ?: json.optLong("filesize").takeIf { it > 0 },
        )
    }

    enum class Phase { DOWNLOADING, PROCESSING }

    /**
     * Downloads into [workDir] and returns the finished media file.
     * Quality is always automatic: best video + best audio, never upscaled.
     */
    suspend fun download(
        url: String,
        format: Format,
        workDir: File,
        processId: String,
        recodeFallback: Boolean = false,
        onProgress: (progress: Float, etaSec: Long, speed: String?, phase: Phase) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        val req = common(url).apply {
            addOption("--newline")
            addOption("--continue")
            addOption("--no-mtime")
            addOption("--fragment-retries", "10")
            addOption("--concurrent-fragments", "4")
            addOption("--embed-metadata")
            addOption("-o", File(workDir, "media.%(ext)s").absolutePath)
            when (format) {
                Format.MP4 -> {
                    // Best video stream + best audio stream. Resolution and frame rate always win;
                    // AAC audio is preferred only because Opus-in-MP4 is not playable everywhere.
                    addOption("-f", "bv*+ba/b")
                    addOption("-S", "res,fps,hdr:12,acodec:aac")
                    addOption("--merge-output-format", "mp4")
                    if (recodeFallback) addOption("--recode-video", "mp4")
                    else addOption("--remux-video", "mp4")
                    addOption("--postprocessor-args", "Merger+ffmpeg_o:-movflags +faststart")
                }
                Format.MP3 -> {
                    addOption("-f", "ba/b")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "320K")
                    addOption("--embed-thumbnail")
                    addOption("--convert-thumbnails", "jpg")
                }
            }
        }
        val speedRx = Regex("""at\s+(~?\s*[\d.]+\s*[KMG]?i?B/s)""")
        try {
            YoutubeDL.getInstance().execute(req, processId) { p, eta, line ->
                val phase = if (line.startsWith("[Merger]") || line.startsWith("[ExtractAudio]") ||
                    line.startsWith("[VideoRemuxer]") || line.startsWith("[VideoConvertor]") ||
                    line.startsWith("[EmbedThumbnail]") || line.startsWith("[Metadata]")
                ) Phase.PROCESSING else Phase.DOWNLOADING
                val speed = speedRx.find(line)?.groupValues?.get(1)?.replace(" ", "")
                onProgress(p.coerceIn(0f, 100f) / 100f, eta, speed, phase)
            }
        } catch (e: YoutubeDL.CanceledException) {
            throw EngineError("canceled")
        } catch (e: InterruptedException) {
            throw EngineError("canceled")
        } catch (e: Exception) {
            throw mapError(e.message ?: "")
        }
        val ext = if (format == Format.MP4) "mp4" else "mp3"
        workDir.listFiles()?.filter { it.isFile && it.extension.equals(ext, true) && it.length() > 0 }
            ?.maxByOrNull { it.length() }
            ?: throw EngineError("err_ffmpeg",
                workDir.listFiles()?.joinToString { it.name })
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    /** Converts yt-dlp stderr into a clear, translatable reason. Raw text is kept as detail. */
    fun mapError(err: String): EngineError {
        val e = err.lowercase()
        val key = when {
            "unsupported url" in e -> "err_unsupported"
            "drm" in e -> "err_drm"
            "private video" in e || "this video is private" in e || "login required" in e ||
                "requires authentication" in e || "log in" in e || "logged-in" in e ||
                "use --cookies" in e && "bot" !in e || "members-only" in e || "join this channel" in e -> "err_private"
            "confirm you" in e && "bot" in e -> "err_bot"
            "not available in your country" in e || "geo restrict" in e || "geo-restrict" in e -> "err_geo"
            "is live" in e && "begin" in e || "live event will begin" in e -> "err_live"
            "video unavailable" in e || "has been removed" in e || "does not exist" in e ||
                "http error 404" in e || "no video formats found" in e || "not available" in e -> "err_unavailable"
            "no space left" in e || "errno 28" in e -> "err_storage"
            "unable to download" in e || "timed out" in e || "connection" in e || "network is unreachable" in e ||
                "name or service not known" in e || "temporary failure in name resolution" in e ||
                "nodename nor servname" in e || "getaddrinfo" in e || "ssl" in e && "eof" in e -> "err_network"
            "ffmpeg" in e || "postprocessing" in e || "conversion failed" in e -> "err_ffmpeg"
            else -> "err_engine"
        }
        return EngineError(key, err.takeLast(3000))
    }
}
