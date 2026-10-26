package com.androsaver

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object UpdateInstaller {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun downloadAndInstall(context: Context, update: UpdateInfo) {
        require(update.sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid update checksum" }
        val apkFile = withContext(Dispatchers.IO) {
            UpdateDownloadFiles.prepareBounded(
                File(context.cacheDir, "updates"),
                "androsaver-${update.versionCode}-${update.sha256}.apk",
                expectedBytes = update.sizeBytes,
                installedVersion = BuildConfig.VERSION_CODE,
                validate = { file ->
                    verifyBytes(file, update)
                    validatePackage(context, file, update)
                },
                download = { temporary -> download(temporary, update) }
            )
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

    private suspend fun download(temporary: File, update: UpdateInfo) {
        val request = Request.Builder().url(update.apkUrl).build()
        if (!request.url.isHttps) throw java.io.IOException("Refusing insecure update download")
        client.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("Update HTTP ${response.code}")
            val body = response.body ?: throw java.io.IOException("Empty update response body")
            if (body.contentLength() > update.sizeBytes || body.contentLength() > MAX_BYTES) {
                throw java.io.IOException("Update exceeds declared limit")
            }
            body.byteStream().use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > update.sizeBytes || total > MAX_BYTES) throw java.io.IOException("Update exceeds size limit")
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
    }

    private suspend fun verifyBytes(file: File, update: UpdateInfo) {
        if (file.length() != update.sizeBytes || update.sizeBytes !in 1..MAX_BYTES) {
            throw java.io.IOException("Update size mismatch")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        if (digest.digest().joinToString("") { "%02x".format(it) } != update.sha256) {
            throw java.io.IOException("Update checksum mismatch")
        }
    }

    private fun validatePackage(context: Context, apk: File, update: UpdateInfo) {
        val flags = UpdatePackagePolicy.signatureFlags(android.os.Build.VERSION.SDK_INT)
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw java.io.IOException("Downloaded file is not an APK")
        if (archive.packageName != context.packageName) throw java.io.IOException("Downloaded APK package mismatch")
        val current = context.packageManager.getPackageInfo(context.packageName, flags)
        val archiveVersion = if (android.os.Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        val currentVersion = if (android.os.Build.VERSION.SDK_INT >= 28) current.longVersionCode else current.versionCode.toLong()
        if (archiveVersion != update.versionCode.toLong() || archiveVersion <= currentVersion) {
            throw java.io.IOException("Downloaded APK version does not match the update")
        }
        val archiveSigners = if (android.os.Build.VERSION.SDK_INT >= 28) {
            archive.signingInfo?.apkContentsSigners
        } else archive.signatures
        val currentSigners = if (android.os.Build.VERSION.SDK_INT >= 28) {
            current.signingInfo?.apkContentsSigners
        } else current.signatures
        val archiveSignerSet = archiveSigners?.map { it.toByteArray().toList() }?.toSet()
        val currentSignerSet = currentSigners?.map { it.toByteArray().toList() }?.toSet()
        if (archiveSignerSet == null || currentSignerSet == null || archiveSignerSet != currentSignerSet) {
            throw java.io.IOException("Downloaded APK signer mismatch")
        }
    }

    private const val MAX_BYTES = 100L * 1024 * 1024
}
