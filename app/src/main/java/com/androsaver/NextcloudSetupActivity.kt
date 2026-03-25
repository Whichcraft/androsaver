package com.androsaver

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.androsaver.databinding.ActivityNextcloudSetupBinding
import com.androsaver.source.NextcloudSource
import kotlinx.coroutines.launch

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
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.hostEdit.setText(prefs.getString(Prefs.NEXTCLOUD_HOST, ""))
        binding.portEdit.setText(prefs.getString(Prefs.NEXTCLOUD_PORT, "443"))
        binding.usernameEdit.setText(prefs.getString(Prefs.NEXTCLOUD_USERNAME, ""))
        binding.passwordEdit.setText(prefs.getString(Prefs.NEXTCLOUD_PASSWORD, ""))
        binding.folderEdit.setText(prefs.getString(Prefs.NEXTCLOUD_FOLDER, "/Photos"))
        binding.httpsSwitch.isChecked = prefs.getBoolean(Prefs.NEXTCLOUD_USE_HTTPS, true)
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
        val port = validatedPort() ?: return
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(Prefs.NEXTCLOUD_HOST, binding.hostEdit.text.toString().trim())
            .putString(Prefs.NEXTCLOUD_PORT, port)
            .putString(Prefs.NEXTCLOUD_USERNAME, binding.usernameEdit.text.toString())
            .putString(Prefs.NEXTCLOUD_PASSWORD, binding.passwordEdit.text.toString())
            .putString(Prefs.NEXTCLOUD_FOLDER, binding.folderEdit.text.toString().trim().ifEmpty { "/Photos" })
            .putBoolean(Prefs.NEXTCLOUD_USE_HTTPS, binding.httpsSwitch.isChecked)
            .apply()
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testConnection() {
        val port = validatedPort() ?: return
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(Prefs.NEXTCLOUD_HOST, binding.hostEdit.text.toString().trim())
            .putString(Prefs.NEXTCLOUD_PORT, port)
            .putString(Prefs.NEXTCLOUD_USERNAME, binding.usernameEdit.text.toString())
            .putString(Prefs.NEXTCLOUD_PASSWORD, binding.passwordEdit.text.toString())
            .putString(Prefs.NEXTCLOUD_FOLDER, binding.folderEdit.text.toString().trim().ifEmpty { "/Photos" })
            .putBoolean(Prefs.NEXTCLOUD_USE_HTTPS, binding.httpsSwitch.isChecked)
            .apply()

        binding.testConnectionButton.isEnabled = false
        binding.testStatus.visibility = View.VISIBLE
        binding.testStatus.text = getString(R.string.testing_connection)

        lifecycleScope.launch {
            val source = NextcloudSource(this@NextcloudSetupActivity)
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
