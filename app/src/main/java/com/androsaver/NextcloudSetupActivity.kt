package com.androsaver

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.androsaver.databinding.ActivityNextcloudSetupBinding
import com.androsaver.source.NextcloudSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class NextcloudSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNextcloudSetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNextcloudSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSavedSettings()

        binding.testConnectionButton.setOnClickListener { testConnection() }
        binding.saveButton.setOnClickListener { saveSettings() }
    }

    private fun loadSavedSettings() {
        val prefs = com.androsaver.Prefs.get(this)
        binding.hostEdit.setText(prefs.getString(Prefs.NEXTCLOUD_HOST, ""))
        binding.portEdit.setText(prefs.getString(Prefs.NEXTCLOUD_PORT, "443"))
        binding.usernameEdit.setText(prefs.getString(Prefs.NEXTCLOUD_USERNAME, ""))
        binding.passwordEdit.setText(prefs.getString(Prefs.NEXTCLOUD_PASSWORD, ""))
        binding.folderEdit.setText(prefs.getString(Prefs.NEXTCLOUD_FOLDER, "/Photos"))
        binding.httpsSwitch.isChecked = prefs.getBoolean(Prefs.NEXTCLOUD_USE_HTTPS, true)
        binding.allowInsecureSwitch.isChecked = prefs.getBoolean(Prefs.NEXTCLOUD_ALLOW_INSECURE, false)
    }

    private fun validatedPort(): String? {
        val raw = binding.portEdit.text.toString().trim().ifEmpty { "443" }
        val n = raw.toIntOrNull()
        if (n == null || n < 1 || n > 65535) {
            Toast.makeText(this, R.string.invalid_port, Toast.LENGTH_SHORT).show()
            return null
        }
        return raw
    }

    private fun saveSettings() {
        if (!validateRequired()) return
        val port = validatedPort() ?: return
        try { com.androsaver.Prefs.get(this).edit()
            .putString(Prefs.NEXTCLOUD_HOST, binding.hostEdit.text.toString().trim())
            .putString(Prefs.NEXTCLOUD_PORT, port)
            .putString(Prefs.NEXTCLOUD_USERNAME, binding.usernameEdit.text.toString())
            .putString(Prefs.NEXTCLOUD_PASSWORD, binding.passwordEdit.text.toString())
            .putString(Prefs.NEXTCLOUD_FOLDER, binding.folderEdit.text.toString().trim().ifEmpty { "/Photos" })
            .putBoolean(Prefs.NEXTCLOUD_USE_HTTPS, binding.httpsSwitch.isChecked)
            .putBoolean(Prefs.NEXTCLOUD_ALLOW_INSECURE, binding.allowInsecureSwitch.isChecked)
            .commit().also { if (!it) error("Credential storage failed") }
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.credential_storage_failed, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testConnection() {
        if (!validateRequired()) return
        val port = validatedPort() ?: return
        val prefs = Prefs.get(this)
        val snapshot = PreferenceSnapshot(prefs, setOf(Prefs.NEXTCLOUD_HOST, Prefs.NEXTCLOUD_PORT, Prefs.NEXTCLOUD_USERNAME, Prefs.NEXTCLOUD_PASSWORD, Prefs.NEXTCLOUD_FOLDER, Prefs.NEXTCLOUD_USE_HTTPS, Prefs.NEXTCLOUD_ALLOW_INSECURE))
        prefs.edit()
            .putString(Prefs.NEXTCLOUD_HOST, binding.hostEdit.text.toString().trim())
            .putString(Prefs.NEXTCLOUD_PORT, port)
            .putString(Prefs.NEXTCLOUD_USERNAME, binding.usernameEdit.text.toString())
            .putString(Prefs.NEXTCLOUD_PASSWORD, binding.passwordEdit.text.toString())
            .putString(Prefs.NEXTCLOUD_FOLDER, binding.folderEdit.text.toString().trim().ifEmpty { "/Photos" })
            .putBoolean(Prefs.NEXTCLOUD_USE_HTTPS, binding.httpsSwitch.isChecked)
            .putBoolean(Prefs.NEXTCLOUD_ALLOW_INSECURE, binding.allowInsecureSwitch.isChecked)
            .apply()

        binding.testConnectionButton.isEnabled = false
        binding.testStatus.visibility = View.VISIBLE
        binding.testStatus.text = getString(R.string.testing_connection)

        lifecycleScope.launch {
            try {
                val ok = withTimeout(60_000L) { NextcloudSource(this@NextcloudSetupActivity).probeConnection() }
                binding.testStatus.text = if (ok) getString(R.string.connection_success_no_images) else getString(R.string.connection_failed, "Server rejected the request")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                binding.testStatus.text = getString(R.string.connection_failed, e.message ?: "Unknown error")
            } finally {
                snapshot.restore()
                binding.testConnectionButton.isEnabled = true
            }
        }
    }

    private fun validateRequired(): Boolean {
        if (SourceSetupValidation.host(binding.hostEdit.text.toString()) == null ||
            !SourceSetupValidation.required(binding.usernameEdit.text.toString(), binding.passwordEdit.text.toString())) {
            Toast.makeText(this, "Enter a valid host, username, and password.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
}
