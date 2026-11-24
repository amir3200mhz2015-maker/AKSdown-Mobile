package com.aksdown.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.aksdown.mobile.data.History
import com.aksdown.mobile.data.Settings
import com.aksdown.mobile.engine.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class AksApp : Application() {
    lateinit var settings: Settings; private set
    lateinit var history: History; private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = Settings(this)
        history = History(this)
        createChannels()
        // Unpack the bundled engine in the background so the first download starts faster.
        scope.launch { runCatching { Engine.ensureInit(this@AksApp) } }
    }

    val workRoot: File get() = File(cacheDir, "dl").apply { mkdirs() }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, getString(R.string.notif_done), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    companion object {
        const val CH_PROGRESS = "aks_progress"
        const val CH_DONE = "aks_done"
        lateinit var instance: AksApp; private set
    }
}
