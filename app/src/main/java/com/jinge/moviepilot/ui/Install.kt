package com.jinge.moviepilot.ui

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** 拉起安装器安装已下载的 APK（cacheDir/update.apk） */
fun installApk(context: android.content.Context, apk: File): Boolean {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apk,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
