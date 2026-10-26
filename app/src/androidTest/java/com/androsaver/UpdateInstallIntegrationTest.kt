package com.androsaver

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdateInstallIntegrationTest {
    @Test fun installerIntentSharesAReadableApkThroughTheRealFileProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(directory, "integration-installed-copy.apk")
        try {
            File(context.applicationInfo.sourceDir).copyTo(apk, overwrite = true)
            val intent = UpdateInstaller.installIntent(context, apk)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertEquals("content", intent.data!!.scheme)
            assertEquals("${context.packageName}.fileprovider", intent.data!!.authority)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(intent.data, intent.clipData!!.getItemAt(0).uri)
            context.contentResolver.openInputStream(intent.data!!)!!.use {
                assertEquals('P'.code, it.read())
                assertEquals('K'.code, it.read())
            }
        } finally { apk.delete() }
    }
}
