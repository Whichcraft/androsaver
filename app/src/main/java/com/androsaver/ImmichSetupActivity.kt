package com.androsaver

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.androsaver.databinding.ActivityImmichSetupBinding
import com.androsaver.source.ImmichSource
import kotlinx.coroutines.launch

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
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.hostEdit.setText(prefs.getString(Prefs.IMMICH_HOST, ""))
        binding.portEdit.setText(prefs.getString(Prefs.IMMICH_PORT, "2283"))
        binding.apiKeyEdit.setText(prefs.getString(Prefs.IMMICH_API_KEY, ""))
        binding.albumIdEdit.setText(prefs.getString(Prefs.IMMICH_ALBUM_ID, ""))
        binding.httpsSwitch.isChecked = prefs.getBoolean(Prefs.IMMICH_USE_HTTPS, false)
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
        val port = validatedPort() ?: return
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(Prefs.IMMICH_HOST, binding.hostEdit.text.toString().trim())
            .putString(Prefs.IMMICH_PORT, port)
            .putString(Prefs.IMMICH_API_KEY, binding.apiKeyEdit.text.toString().trim())
            .putString(Prefs.IMMICH_ALBUM_ID, binding.albumIdEdit.text.toString().trim())
            .putBoolean(Prefs.IMMICH_USE_HTTPS, binding.httpsSwitch.isChecked)
            .apply()
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testConnection() {
        val port = validatedPort() ?: return
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(Prefs.IMMICH_HOST, binding.hostEdit.text.toString().trim())
            .putString(Prefs.IMMICH_PORT, port)
            .putString(Prefs.IMMICH_API_KEY, binding.apiKeyEdit.text.toString().trim())
            .putString(Prefs.IMMICH_ALBUM_ID, binding.albumIdEdit.text.toString().trim())
            .putBoolean(Prefs.IMMICH_USE_HTTPS, binding.httpsSwitch.isChecked)
            .apply()

        binding.testConnectionButton.isEnabled = false
        binding.testStatus.visibility = View.VISIBLE
        binding.testStatus.text = getString(R.string.testing_connection)

        lifecycleScope.launch {
            val source = ImmichSource(this@ImmichSetupActivity)
            try {
                val images = source.getImageUrls()
                binding.testStatus.text = if (images.isNotEmpty()) {
                    getString(R.string.connection_success, images.size)
                } else {
                    getString(R.string.connection_success_no_images)
                }
            } catch (e: Exception) {
                binding.testStatus.text = getString(R.string.connection_failed, e.message ?: "Unknown error")
            }
            binding.testConnectionButton.isEnabled = true
        }
    }
}
