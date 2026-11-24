package com.aksdown.mobile.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import java.io.File

class Settings(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("aks_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private fun read() = SettingsState(
        defaultFormat = runCatching { Format.valueOf(p.getString("fmt", "MP4")!!) }.getOrDefault(Format.MP4),
        autoDownload = p.getBoolean("auto", false),
        wifiOnly = p.getBoolean("wifi", false),
        notifications = p.getBoolean("notif", true),
        language = p.getString("lang", "") ?: "",
    )

    fun edit(block: SettingsState.() -> SettingsState) {
        val n = _state.value.block()
        p.edit().putString("fmt", n.defaultFormat.name).putBoolean("auto", n.autoDownload)
            .putBoolean("wifi", n.wifiOnly).putBoolean("notif", n.notifications)
            .putString("lang", n.language).apply()
        _state.value = n
    }
}

data class SettingsState(
    val defaultFormat: Format,
    val autoDownload: Boolean,
    val wifiOnly: Boolean,
    val notifications: Boolean,
    /** "" = follow system, otherwise "en", "ar", "he". */
    val language: String,
)

/** Download history, kept in memory as a StateFlow and persisted as JSON in private storage. */
class History(ctx: Context) {
    private val file = File(ctx.filesDir, "history.json")
    private val lock = Any()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private fun load(): List<DownloadItem> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { DownloadItem.fromJson(arr.getJSONObject(it)) }.map {
            // On app start nothing is running yet: a download interrupted when the app was closed
            // is shown as failed (network interruption) so the user can press Retry to resume it.
            if (it.status == Status.RUNNING || it.status == Status.QUEUED) it.copy(status = Status.FAILED, errorKey = "err_network") else it
        }
    }.getOrDefault(emptyList())

    private fun persist(list: List<DownloadItem>) = synchronized(lock) {
        runCatching {
            val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
            val tmp = File(file.parentFile, "history.json.tmp")
            tmp.writeText(arr.toString()); tmp.renameTo(file)
        }
    }

    fun get(id: String) = _items.value.firstOrNull { it.id == id }

    fun add(item: DownloadItem) { _items.update { listOf(item) + it }; persist(_items.value) }

    /** Progress ticks call with persist=false to avoid disk writes several times per second. */
    fun update(id: String, persist: Boolean = true, f: (DownloadItem) -> DownloadItem) {
        _items.update { list -> list.map { if (it.id == id) f(it) else it } }
        if (persist) persist(_items.value)
    }

    fun remove(id: String) { _items.update { l -> l.filterNot { it.id == id } }; persist(_items.value) }

    fun clearFinished() {
        _items.update { l -> l.filter { it.status == Status.RUNNING || it.status == Status.QUEUED } }
        persist(_items.value)
    }

    fun nextQueued(): DownloadItem? = _items.value.lastOrNull { it.status == Status.QUEUED }
}
