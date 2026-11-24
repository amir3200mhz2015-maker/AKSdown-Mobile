package com.aksdown.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aksdown.mobile.AksApp
import com.aksdown.mobile.data.Format
import com.aksdown.mobile.data.VideoMeta
import com.aksdown.mobile.engine.Engine
import com.aksdown.mobile.engine.EngineError
import com.aksdown.mobile.engine.Platform
import com.aksdown.mobile.engine.UrlTools
import com.aksdown.mobile.service.Downloads
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val input: String = "",
    val url: String? = null,
    val platform: Platform? = null,
    val loadingInfo: Boolean = false,
    val meta: VideoMeta? = null,
    val infoErrorKey: String? = null,
    val inputErrorKey: String? = null,
    val format: Format = Format.MP4,
    val currentId: String? = null,
    val toastKey: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AksApp
    private val _home = MutableStateFlow(HomeState(format = app.settings.state.value.defaultFormat))
    val home = _home.asStateFlow()
    val history = app.history.items
    val settings = app.settings.state
    private var infoJob: Job? = null

    fun onInput(text: String) {
        val url = UrlTools.extract(text)
        _home.update {
            it.copy(
                input = if (url != null && text.length > url.length + 1) url else text,
                url = url, platform = url?.let(UrlTools::platform),
                inputErrorKey = if (text.isNotBlank() && url == null) "err_invalid_url" else null,
                meta = if (url != it.url) null else it.meta, infoErrorKey = null,
                currentId = if (url != it.url) null else it.currentId,
            )
        }
        if (url != null) loadInfo(url, debounceMs = 500) else infoJob?.cancel()
    }

    fun onClipboardEmpty() = _home.update { it.copy(inputErrorKey = "err_clipboard_empty") }

    /** Debug builds only: lets the automated emulator test start a download through a share intent. */
    fun testDownload(text: String, f: Format) { onInput(text); setFormat(f); startDownload() }

    fun clear() {
        infoJob?.cancel()
        _home.update { HomeState(format = it.format) }
    }

    fun setFormat(f: Format) = _home.update { it.copy(format = f) }

    private fun loadInfo(url: String, debounceMs: Long) {
        infoJob?.cancel()
        infoJob = viewModelScope.launch {
            delay(debounceMs)
            _home.update { it.copy(loadingInfo = true, infoErrorKey = null) }
            try {
                Engine.ensureInit(app)
                val meta = Engine.fetchInfo(url)
                if (_home.value.url == url) _home.update { it.copy(meta = meta, loadingInfo = false) }
            } catch (e: EngineError) {
                if (_home.value.url == url) _home.update { it.copy(loadingInfo = false, infoErrorKey = e.key) }
            }
        }
    }

    fun retryInfo() { _home.value.url?.let { loadInfo(it, 0) } }

    /** Called with text shared from another app. */
    fun onShared(text: String) {
        onInput(text)
        val s = _home.value
        if (s.url == null) return
        if (app.settings.state.value.autoDownload) {
            setFormat(app.settings.state.value.defaultFormat)
            startDownload()
        }
    }

    fun startDownload() {
        val s = _home.value
        val url = s.url ?: run { _home.update { it.copy(inputErrorKey = "err_invalid_url") }; return }
        if (Downloads.hasDone(url, s.format)) _home.update { it.copy(toastKey = "duplicate") }
        val id = Downloads.enqueue(app, url, s.format, s.meta)
        _home.update { it.copy(currentId = id) }
    }

    fun cancelCurrent() { _home.value.currentId?.let { Downloads.cancel(app, it) } }
    fun retry(id: String) { Downloads.retry(app, id); _home.update { it.copy(currentId = id) } }
    fun toastShown() = _home.update { it.copy(toastKey = null) }
    fun setDefaultFormat(f: Format) { app.settings.edit { copy(defaultFormat = f) }; setFormat(f) }
}
