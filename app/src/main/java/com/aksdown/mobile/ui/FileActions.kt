package com.aksdown.mobile.ui

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import com.aksdown.mobile.AksApp
import com.aksdown.mobile.R
import com.aksdown.mobile.data.DownloadItem
import com.aksdown.mobile.engine.MediaSaver
import java.io.File

object FileActions {
    private fun uri(item: DownloadItem) = item.uri?.let(Uri::parse)

    fun open(ctx: Context, item: DownloadItem) {
        val u = uri(item) ?: return
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(u, MediaSaver.mime(item.format))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(ctx, Intent.createChooser(i, item.fileName))
    }

    fun share(ctx: Context, item: DownloadItem) {
        val u = uri(item) ?: return
        val i = Intent(Intent.ACTION_SEND).setType(MediaSaver.mime(item.format))
            .putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(ctx, Intent.createChooser(i, item.fileName))
    }

    fun openFolder(ctx: Context) {
        // Opens the system Downloads view (Files / Samsung My Files) where the AKSdown folder lives.
        start(ctx, Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun rename(ctx: Context, item: DownloadItem, newName: String): Boolean {
        val u = uri(item) ?: return false
        val name = MediaSaver.rename(ctx, u, item.filePath, newName, item.format) ?: return false
        AksApp.instance.history.update(item.id) {
            it.copy(fileName = name, filePath = it.filePath?.let { p -> File(File(p).parentFile, name).absolutePath })
        }
        return true
    }

    /**
     * Deletes the file. Returns an IntentSender when Android must ask the user first
     * (files created by an earlier install of the app are no longer "owned" by it).
     */
    fun delete(ctx: Context, item: DownloadItem): IntentSender? {
        val h = AksApp.instance.history
        val u = uri(item)
        if (u == null) { h.remove(item.id); return null }
        return try {
            if (Build.VERSION.SDK_INT < 29 && item.filePath != null) File(item.filePath).delete()
            ctx.contentResolver.delete(u, null, null)
            h.remove(item.id); null
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= 30) MediaStore.createDeleteRequest(ctx.contentResolver, listOf(u)).intentSender
            else if (Build.VERSION.SDK_INT == 29 && e is android.app.RecoverableSecurityException) e.userAction.actionIntent.intentSender
            else { h.remove(item.id); null }
        }
    }

    private fun start(ctx: Context, i: Intent) {
        try { ctx.startActivity(i) } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, ctx.getString(R.string.err_engine), Toast.LENGTH_SHORT).show()
        }
    }
}
