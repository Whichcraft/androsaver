package com.androsaver

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.androsaver.databinding.ActivityImmichSetupBinding
import com.androsaver.source.ImmichSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ImmichSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImmichSetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImmichSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSavedSettings()

        binding.testConnectionButton.setOnClickListener { testConnection() }
        binding.saveButton.setOnClickListener { saveSettings() }
    }

    private fun loadSavedSettings() {
        try {
            val prefs = com.androsaver.Prefs.get(this)
            binding.hostEdit.setText(prefs.getString(Prefs.IMMICH_HOST, ""))
            binding.portEdit.setText(prefs.getString(Prefs.IMMICH_PORT, "2283"))
            binding.apiKeyEdit.setText(prefs.getString(Prefs.IMMICH_API_KEY, ""))
            binding.albumIdEdit.setText(prefs.getString(Prefs.IMMICH_ALBUM_ID, ""))
            binding.httpsSwitch.isChecked = prefs.getBoolean(Prefs.IMMICH_USE_HTTPS, true)
            binding.allowInsecureSwitch.isChecked = prefs.getBoolean(Prefs.IMMICH_ALLOW_INSECURE, false)
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.credential_storage_failed, Toast.LENGTH_LONG).show()
            binding.saveButton.isEnabled = false
            binding.testConnectionButton.isEnabled = false
        }
    }

    private fun validatedPort(): String? {
        val raw = binding.portEdit.text.toString().trim().ifEmpty { "2283" }
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
            .putString(Prefs.IMMICH_HOST, binding.hostEdit.text.toString().trim())
            .putString(Prefs.IMMICH_PORT, port)
            .putString(Prefs.IMMICH_API_KEY, binding.apiKeyEdit.text.toString().trim())
            .putString(Prefs.IMMICH_ALBUM_ID, binding.albumIdEdit.text.toString().trim())
            .putBoolean(Prefs.IMMICH_USE_HTTPS, binding.httpsSwitch.isChecked)
            .putBoolean(Prefs.IMMICH_ALLOW_INSECURE, binding.allowInsecureSwitch.isChecked)
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
        val config = ImmichSource.ConnectionConfig(
            host = binding.hostEdit.text.toString().trim(),
            port = port,
            apiKey = binding.apiKeyEdit.text.toString().trim(),
            useHttps = binding.httpsSwitch.isChecked,
            allowInsecure = binding.allowInsecureSwitch.isChecked
        )

        binding.testConnectionButton.isEnabled = false
        binding.testStatus.visibility = View.VISIBLE
        binding.testStatus.text = getString(R.string.testing_connection)

        lifecycleScope.launch {
            try {
                val ok = withTimeout(60_000L) { ImmichSource(this@ImmichSetupActivity).probeConnection(config) }
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
            !SourceSetupValidation.required(binding.apiKeyEdit.text.toString())) {
            Toast.makeText(this, "Enter a valid host and API key.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
}
