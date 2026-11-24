package com.aksdown.mobile.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aksdown.mobile.AksApp
import com.aksdown.mobile.MainActivity
import com.aksdown.mobile.R
import com.aksdown.mobile.data.DownloadItem
import com.aksdown.mobile.data.Format
import com.aksdown.mobile.data.Stage
import com.aksdown.mobile.data.Status
import com.aksdown.mobile.data.VideoMeta
import com.aksdown.mobile.engine.Engine
import com.aksdown.mobile.engine.EngineError
import com.aksdown.mobile.engine.MediaSaver
import com.aksdown.mobile.engine.UrlTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Public entry points used by the UI. */
object Downloads {
    private val app get() = AksApp.instance

    fun enqueue(ctx: Context, url: String, format: Format, meta: VideoMeta?): String {
        val id = UUID.randomUUID().toString()
        app.history.add(
            DownloadItem(
                id = id, url = url, format = format, platform = UrlTools.platform(url).name,
                title = meta?.title, thumbnail = meta?.thumbnail, durationSec = meta?.durationSec,
            )
        )
        DownloadService.kick(ctx)
        return id
    }

    fun cancel(ctx: Context, id: String) {
        val item = app.history.get(id) ?: return
        if (item.status == Status.RUNNING) {
            app.history.update(id) { it.copy(status = Status.CANCELED, stage = Stage.WAITING) }
            Engine.cancel(id)
        } else if (item.status == Status.QUEUED) {
            app.history.update(id) { it.copy(status = Status.CANCELED) }
        }
        File(app.workRoot, id).deleteRecursively()
    }

    fun retry(ctx: Context, id: String) {
        app.history.update(id) {
            it.copy(status = Status.QUEUED, stage = Stage.WAITING, progress = 0f, errorKey = null, errorDetail = null, speed = null, etaSec = 0)
        }
        DownloadService.kick(ctx)
    }

    fun hasDone(url: String, format: Format) =
        app.history.items.value.any { it.url == url && it.format == format && it.status == Status.DONE }
}

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private val app get() = application as AksApp
    private var wake: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastNotif = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_ID)?.let { Downloads.cancel(this, it) }
        }
        startInForeground(progressNotification(null))
        if (loop?.isActive != true) loop = scope.launch { runQueue() }
        return START_NOT_STICKY
    }

    private fun startInForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    private suspend fun runQueue() {
        acquireLocks()
        try {
            while (true) {
                val next = app.history.nextQueued() ?: break
                runJob(next)
            }
        } finally {
            releaseLocks()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun runJob(start: DownloadItem) {
        val id = start.id
        val h = app.history
        h.update(id) { it.copy(status = Status.RUNNING, stage = Stage.INIT, progress = 0f) }
        notifyProgress(h.get(id), force = true)
        val work = File(app.workRoot, id)
        try {
            if (!networkOk()) throw EngineError(if (app.settings.state.value.wifiOnly) "err_wifi" else "err_network")
            Engine.ensureInit(this)

            var item = h.get(id) ?: return
            if (item.title == null) {
                h.update(id, false) { it.copy(stage = Stage.INFO) }
                val meta = Engine.fetchInfo(item.url)
                h.update(id) { it.copy(title = meta.title, thumbnail = meta.thumbnail, durationSec = meta.durationSec) }
                checkStorage(meta.approxSize)
            } else checkStorage(null)
            if (h.get(id)?.status != Status.RUNNING) return

            h.update(id, false) { it.copy(stage = Stage.DOWNLOADING) }
            val file = try {
                download(item, work, recode = false)
            } catch (e: EngineError) {
                // Rare: the best streams can't be remuxed into MP4. Convert instead (re-encode once).
                if (e.key == "err_ffmpeg" && item.format == Format.MP4 && h.get(id)?.status == Status.RUNNING) {
                    download(item, work, recode = true)
                } else throw e
            }
            if (h.get(id)?.status != Status.RUNNING) return

            h.update(id, false) { it.copy(stage = Stage.SAVING, progress = 1f) }
            notifyProgress(h.get(id), force = true)
            item = h.get(id) ?: return
            val res = MediaSaver.validate(file, item.format) ?: throw EngineError("err_invalid_file")
            val saved = MediaSaver.save(this, file, item.title, item.format, res)
            work.deleteRecursively()
            h.update(id) {
                it.copy(
                    status = Status.DONE, stage = Stage.WAITING, progress = 1f, uri = saved.uri.toString(),
                    filePath = saved.path, fileName = saved.name, sizeBytes = saved.size,
                    resolution = saved.resolution, finishedAt = System.currentTimeMillis(), speed = null, etaSec = 0,
                )
            }
            notifyFinished(h.get(id)!!, success = true)
        } catch (e: EngineError) {
            if (e.key == "canceled" || h.get(id)?.status == Status.CANCELED) {
                h.update(id) { it.copy(status = Status.CANCELED, stage = Stage.WAITING) }
                work.deleteRecursively()
            } else {
                // Keep partial data in [work] so Retry resumes the download where possible.
                h.update(id) { it.copy(status = Status.FAILED, stage = Stage.WAITING, errorKey = e.key, errorDetail = e.detail) }
                notifyFinished(h.get(id)!!, success = false)
            }
        } catch (t: Throwable) {
            h.update(id) { it.copy(status = Status.FAILED, stage = Stage.WAITING, errorKey = "err_engine", errorDetail = t.toString()) }
            notifyFinished(h.get(id)!!, success = false)
        } finally {
            NotificationManagerCompat.from(this).cancel(NOTIF_ID + 1)
        }
    }

    private suspend fun download(item: DownloadItem, work: File, recode: Boolean): File =
        Engine.download(item.url, item.format, work, item.id, recode) { p, eta, speed, phase ->
            app.history.update(item.id, persist = false) {
                if (it.status != Status.RUNNING) it else it.copy(
                    progress = if (phase == Engine.Phase.PROCESSING) 1f else p,
                    etaSec = eta, speed = speed ?: it.speed,
                    stage = if (phase == Engine.Phase.PROCESSING) Stage.PROCESSING else Stage.DOWNLOADING,
                )
            }
            notifyProgress(app.history.get(item.id))
        }

    private fun checkStorage(approx: Long?) {
        val free = StatFs(app.cacheDir.absolutePath).availableBytes
        val need = (approx ?: 0L) * 2 + 64L * 1024 * 1024 // temp + final copy + margin
        if (free < need) throw EngineError("err_storage")
    }

    private fun networkOk(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (app.settings.state.value.wifiOnly) {
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
        return true
    }

    // ---------- notifications ----------
    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun progressNotification(item: DownloadItem?): Notification {
        val b = NotificationCompat.Builder(this, AksApp.CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(item?.title ?: getString(R.string.engine_wait))
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(openAppIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (item != null) {
            val text = when (item.stage) {
                Stage.DOWNLOADING -> buildString {
                    append("${(item.progress * 100).toInt()}%")
                    item.speed?.let { append(" · $it") }
                    if (item.etaSec > 0) append(" · ${UrlTools.formatEta(item.etaSec)}")
                }
                Stage.PROCESSING -> getString(R.string.processing)
                Stage.SAVING -> getString(R.string.saving)
                Stage.INFO -> getString(R.string.fetching)
                else -> getString(R.string.preparing)
            }
            b.setContentText(text)
            if (item.stage == Stage.DOWNLOADING) b.setProgress(100, (item.progress * 100).toInt(), false)
            else b.setProgress(0, 0, true)
            val cancel = PendingIntent.getService(
                this, item.id.hashCode(),
                Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, item.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.addAction(0, getString(R.string.cancel), cancel)
        }
        return b.build()
    }

    @SuppressLint("MissingPermission")
    private fun notifyProgress(item: DownloadItem?, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotif < 1000) return
        lastNotif = now
        if (canNotify()) NotificationManagerCompat.from(this).notify(NOTIF_ID, progressNotification(item))
    }

    @SuppressLint("MissingPermission")
    private fun notifyFinished(item: DownloadItem, success: Boolean) {
        if (!app.settings.state.value.notifications || !canNotify()) return
        val b = NotificationCompat.Builder(this, AksApp.CH_DONE)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(if (success) R.string.notif_done else R.string.notif_failed))
            .setContentText(item.fileName ?: item.title ?: item.url)
            .setAutoCancel(true)
        if (success && item.uri != null) {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.uri), MediaSaver.mime(item.format))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            b.setContentIntent(PendingIntent.getActivity(this, item.id.hashCode(), view, PendingIntent.FLAG_IMMUTABLE))
        } else b.setContentIntent(openAppIntent())
        NotificationManagerCompat.from(this).notify(item.id.hashCode(), b.build())
    }

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // ---------- keep CPU and Wi-Fi awake while downloading with the screen off ----------
    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AKSdown:download").apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
        wifiLock = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
            .createWifiLock(if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_HIGH_PERF else WifiManager.WIFI_MODE_FULL, "AKSdown:wifi")
            .apply { setReferenceCounted(false); acquire() }
    }

    private fun releaseLocks() {
        runCatching { wake?.release() }; runCatching { wifiLock?.release() }
    }

    override fun onDestroy() {
        releaseLocks(); scope.cancel(); super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 4100
        const val ACTION_CANCEL = "com.aksdown.mobile.CANCEL"
        const val EXTRA_ID = "id"
        fun kick(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
        }
    }
}
