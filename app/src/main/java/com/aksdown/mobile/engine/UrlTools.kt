package com.aksdown.mobile.engine

import android.net.Uri
import androidx.compose.ui.graphics.Color

enum class Platform(val label: String, val color: Long) {
    YOUTUBE("YouTube", 0xFFFF0033),
    YOUTUBE_SHORTS("YouTube Shorts", 0xFFFF0033),
    INSTAGRAM("Instagram", 0xFFD62976),
    INSTAGRAM_REELS("Instagram Reels", 0xFFFA7E1E),
    FACEBOOK("Facebook", 0xFF1877F2),
    FACEBOOK_REELS("Facebook Reels", 0xFF1877F2),
    TIKTOK("TikTok", 0xFF25F4EE),
    X("X / Twitter", 0xFF9CA3AF),
    VIMEO("Vimeo", 0xFF1AB7EA),
    DAILYMOTION("Dailymotion", 0xFF0066DC),
    REDDIT("Reddit", 0xFFFF4500),
    OTHER("Web", 0xFF64748B);

    val tint: Color get() = Color(color)
    val short: String get() = when (this) {
        YOUTUBE, YOUTUBE_SHORTS -> "YT"; INSTAGRAM, INSTAGRAM_REELS -> "IG"
        FACEBOOK, FACEBOOK_REELS -> "FB"; TIKTOK -> "TT"; X -> "X"; VIMEO -> "V"
        DAILYMOTION -> "DM"; REDDIT -> "R"; OTHER -> "www"
    }
}

object UrlTools {
    private val urlRegex = Regex("""https?://[^\s<>"'`{}|\\^]+""", RegexOption.IGNORE_CASE)
    private const val MAX_URL = 2048

    /** Pulls the first http(s) link out of arbitrary shared text and validates it. */
    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val raw = urlRegex.find(text)?.value?.trimEnd(')', ']', ',', '.', ';', '!', '?', '»', '"') ?: return null
        return validate(raw)
    }

    fun validate(raw: String): String? {
        if (raw.length > MAX_URL) return null
        // No control characters or whitespace; this string is passed as a single argv entry (never a shell).
        if (raw.any { it.isISOControl() || it.isWhitespace() }) return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host?.lowercase() ?: return null
        if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
        if (host == "localhost" || host.endsWith(".local") || Regex("""^\d+\.\d+\.\d+\.\d+$""").matches(host)) return null
        // Upgrade plain http to https: all supported platforms serve https.
        return if (scheme == "http") "https" + raw.substring(4) else raw
    }

    fun platform(url: String): Platform {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return Platform.OTHER
        val host = (uri.host ?: "").lowercase().removePrefix("www.").removePrefix("m.").removePrefix("mobile.").removePrefix("music.")
        val path = (uri.path ?: "").lowercase()
        fun on(vararg d: String) = d.any { host == it || host.endsWith(".$it") }
        return when {
            on("youtube.com", "youtube-nocookie.com") && path.startsWith("/shorts") -> Platform.YOUTUBE_SHORTS
            on("youtube.com", "youtu.be", "youtube-nocookie.com") -> Platform.YOUTUBE
            on("instagram.com") && (path.contains("/reel")) -> Platform.INSTAGRAM_REELS
            on("instagram.com", "instagr.am") -> Platform.INSTAGRAM
            on("facebook.com", "fb.watch", "fb.com") && path.contains("/reel") -> Platform.FACEBOOK_REELS
            on("facebook.com", "fb.watch", "fb.com") -> Platform.FACEBOOK
            on("tiktok.com") -> Platform.TIKTOK
            on("x.com", "twitter.com") -> Platform.X
            on("vimeo.com") -> Platform.VIMEO
            on("dailymotion.com", "dai.ly") -> Platform.DAILYMOTION
            on("reddit.com", "redd.it") -> Platform.REDDIT
            else -> Platform.OTHER
        }
    }

    fun platformByName(name: String?): Platform = Platform.entries.firstOrNull { it.name == name } ?: Platform.OTHER

    /** Safe, readable file name: strips path separators, reserved and control characters. */
    fun safeFileName(title: String?, fallback: String): String {
        val cleaned = (title ?: "")
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F\u007F]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim().trim('.', ' ')
        val base = if (cleaned.isEmpty()) fallback else cleaned
        // Keep well below the 255-byte filesystem limit (Arabic/Hebrew are 2 bytes per char in UTF-8).
        var out = base
        while (out.toByteArray().size > 180) out = out.dropLast(1)
        return out.trim()
    }

    fun formatDuration(sec: Long?): String {
        if (sec == null || sec <= 0) return ""
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun formatBytes(b: Long): String {
        if (b <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = b.toDouble(); var i = 0
        while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
        return if (i == 0) "$b B" else "%.1f %s".format(v, units[i])
    }

    fun formatEta(sec: Long): String = if (sec <= 0) "" else formatDuration(sec)
}
