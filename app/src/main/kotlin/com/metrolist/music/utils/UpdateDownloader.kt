/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Downloads an update APK through Android's own DownloadManager — so the user gets the
 * normal system download progress notification for free — then launches the system
 * package installer as soon as it lands. The user still has to tap "Install" in that
 * system dialog: Android has no way for a sideloaded app to install over itself silently
 * without root or device-owner privileges, and this never tries to bypass that.
 */
object UpdateDownloader {
    private const val FILE_NAME = "Metrolist-update.apk"

    /** Downloads [downloadUrl] via DownloadManager (native progress notification) and returns the APK file. */
    suspend fun download(
        context: Context,
        downloadUrl: String,
    ): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val destFile = File(context.getExternalFilesDir(null), FILE_NAME)
                destFile.delete() // drop any stale copy from a previous attempt before resuming via DownloadManager

                val request =
                    DownloadManager
                        .Request(downloadUrl.toUri())
                        .setTitle(FILE_NAME)
                        .setDestinationUri(destFile.toUri())
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)
                val downloadId = downloadManager.enqueue(request)

                while (true) {
                    delay(500)
                    val status =
                        downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                            if (!cursor.moveToFirst()) error("Download vanished from DownloadManager")
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        }
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> break
                        DownloadManager.STATUS_FAILED -> error("Update download failed")
                    }
                }

                destFile
            }
        }

    fun installIntent(context: Context, apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Tries to jump straight to the system installer. Android blocks starting an Activity
     * from a background process on API 29+, so this can throw (e.g. the download finished
     * after the user left the app) — callers should fall back to a notification instead,
     * since tapping a notification is always allowed to start an activity.
     */
    fun launchInstaller(context: Context, apkFile: File) {
        context.startActivity(installIntent(context, apkFile))
    }
}
