package com.androsaver

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object UpdateInstaller {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun downloadAndInstall(context: Context, apkUrl: String) = withContext(Dispatchers.IO) {
        val apkFile = File(context.cacheDir, "androsaver-update.apk")
        val request = Request.Builder().url(apkUrl).build()
        client.newCall(request).execute().use { response ->
            response.body?.byteStream()?.use { input ->
                apkFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
