package com.aksdown.mobile.data

import org.json.JSONObject

enum class Format { MP4, MP3 }
enum class Status { QUEUED, RUNNING, DONE, FAILED, CANCELED }
enum class Stage { WAITING, INIT, INFO, DOWNLOADING, PROCESSING, SAVING }

/** Error keys map to string resources (err_*) so messages are shown in the selected language. */
data class DownloadItem(
    val id: String,
    val url: String,
    val format: Format,
    val platform: String,
    val title: String? = null,
    val thumbnail: String? = null,
    val durationSec: Long? = null,
    val status: Status = Status.QUEUED,
    val stage: Stage = Stage.WAITING,
    val progress: Float = 0f,
    val speed: String? = null,
    val etaSec: Long = 0,
    val errorKey: String? = null,
    val errorDetail: String? = null,
    val uri: String? = null,
    val filePath: String? = null,
    val fileName: String? = null,
    val sizeBytes: Long = 0,
    val resolution: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("url", url); put("format", format.name); put("platform", platform)
        putOpt("title", title); putOpt("thumbnail", thumbnail); putOpt("durationSec", durationSec)
        put("status", status.name)
        putOpt("errorKey", errorKey)
        putOpt("errorDetail", errorDetail)
        putOpt("uri", uri); putOpt("filePath", filePath); putOpt("fileName", fileName)
        put("sizeBytes", sizeBytes); putOpt("resolution", resolution)
        put("createdAt", createdAt); putOpt("finishedAt", finishedAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = DownloadItem(
            id = o.getString("id"), url = o.getString("url"),
            format = runCatching { Format.valueOf(o.getString("format")) }.getOrDefault(Format.MP4),
            platform = o.optString("platform", "OTHER"),
            title = o.optStringOrNull("title"), thumbnail = o.optStringOrNull("thumbnail"),
            durationSec = if (o.has("durationSec")) o.optLong("durationSec") else null,
            status = runCatching { Status.valueOf(o.getString("status")) }.getOrDefault(Status.FAILED),
            errorKey = o.optStringOrNull("errorKey"), errorDetail = o.optStringOrNull("errorDetail"),
            uri = o.optStringOrNull("uri"), filePath = o.optStringOrNull("filePath"),
            fileName = o.optStringOrNull("fileName"), sizeBytes = o.optLong("sizeBytes"),
            resolution = o.optStringOrNull("resolution"), createdAt = o.optLong("createdAt"),
            finishedAt = if (o.has("finishedAt")) o.optLong("finishedAt") else null,
        )
    }
}

fun JSONObject.optStringOrNull(k: String): String? = if (has(k) && !isNull(k)) optString(k).takeIf { it.isNotEmpty() } else null

data class VideoMeta(
    val title: String?,
    val thumbnail: String?,
    val durationSec: Long?,
    val extractor: String?,
    val uploader: String?,
    val maxHeight: Int?,
    val approxSize: Long?,
)
