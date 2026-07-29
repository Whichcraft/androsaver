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

    suspend fun downloadAndInstall(context: Context, apkUrl: String) {
        val apkFile = withContext(Dispatchers.IO) {
            val destination = File(context.cacheDir, "androsaver-update.apk")
            val temporary = File(context.cacheDir, "androsaver-update.apk.part")
            val request = Request.Builder().url(apkUrl).build()
            if (!request.url.isHttps) {
                throw java.io.IOException("Refusing to download an update over insecure HTTP")
            }
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw java.io.IOException("Unexpected HTTP code $response")
                }
                val body = response.body ?: throw java.io.IOException("Empty update response body")
                body.byteStream().use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (temporary.length() == 0L) throw java.io.IOException("Downloaded update is empty")
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            destination
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        withContext(Dispatchers.Main) {
            context.startActivity(intent)
        }
    }
}
