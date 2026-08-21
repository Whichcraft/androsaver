package com.androsaver

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.androsaver.databinding.ActivitySynologySetupBinding
import com.androsaver.source.SynologySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class SynologySetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySynologySetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySynologySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSavedSettings()

        binding.testConnectionButton.setOnClickListener { testConnection() }
        binding.saveButton.setOnClickListener { saveSettings() }
    }

    private fun loadSavedSettings() {
        try {
            val prefs = com.androsaver.Prefs.get(this)
            binding.hostEdit.setText(prefs.getString(Prefs.SYNOLOGY_HOST, ""))
            binding.portEdit.setText(prefs.getString(Prefs.SYNOLOGY_PORT, "5000"))
            binding.usernameEdit.setText(prefs.getString(Prefs.SYNOLOGY_USERNAME, ""))
            binding.passwordEdit.setText(prefs.getString(Prefs.SYNOLOGY_PASSWORD, ""))
            binding.folderEdit.setText(prefs.getString(Prefs.SYNOLOGY_FOLDER, "/photos"))
            binding.httpsSwitch.isChecked = prefs.getBoolean(Prefs.SYNOLOGY_USE_HTTPS, true)
            binding.allowInsecureSwitch.isChecked = prefs.getBoolean(Prefs.SYNOLOGY_ALLOW_INSECURE, false)
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.credential_storage_failed, Toast.LENGTH_LONG).show()
            binding.saveButton.isEnabled = false
            binding.testConnectionButton.isEnabled = false
        }
    }

    private fun validatedPort(): String? {
        val raw = binding.portEdit.text.toString().trim().ifEmpty { "5000" }
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
            .putString(Prefs.SYNOLOGY_HOST, binding.hostEdit.text.toString().trim())
            .putString(Prefs.SYNOLOGY_PORT, port)
            .putString(Prefs.SYNOLOGY_USERNAME, binding.usernameEdit.text.toString())
            .putString(Prefs.SYNOLOGY_PASSWORD, binding.passwordEdit.text.toString())
            .putString(Prefs.SYNOLOGY_FOLDER, binding.folderEdit.text.toString().trim().ifEmpty { "/photos" })
            .putBoolean(Prefs.SYNOLOGY_USE_HTTPS, binding.httpsSwitch.isChecked)
            .putBoolean(Prefs.SYNOLOGY_ALLOW_INSECURE, binding.allowInsecureSwitch.isChecked)
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
        val config = SynologySource.ConnectionConfig(
            host = binding.hostEdit.text.toString().trim(),
            port = port,
            username = binding.usernameEdit.text.toString(),
            password = binding.passwordEdit.text.toString(),
            useHttps = binding.httpsSwitch.isChecked,
            allowInsecure = binding.allowInsecureSwitch.isChecked
        )

        binding.testConnectionButton.isEnabled = false
        binding.testStatus.visibility = View.VISIBLE
        binding.testStatus.text = getString(R.string.testing_connection)

        lifecycleScope.launch {
            try {
                val ok = withTimeout(60_000L) { SynologySource(this@SynologySetupActivity).probeConnection(config) }
                binding.testStatus.text = if (ok) getString(R.string.connection_success_no_images) else getString(R.string.connection_failed, "Server rejected the request")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                binding.testStatus.text = getString(R.string.connection_failed, e.message ?: "Unknown error")
            } finally {
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
