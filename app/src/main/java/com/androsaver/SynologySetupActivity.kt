package com.androsaver

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.androsaver.databinding.ActivitySynologySetupBinding
import com.androsaver.source.SynologySource
import kotlinx.coroutines.launch

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
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.hostEdit.setText(prefs.getString(Prefs.SYNOLOGY_HOST, ""))
        binding.portEdit.setText(prefs.getString(Prefs.SYNOLOGY_PORT, "5000"))
        binding.usernameEdit.setText(prefs.getString(Prefs.SYNOLOGY_USERNAME, ""))
        binding.passwordEdit.setText(prefs.getString(Prefs.SYNOLOGY_PASSWORD, ""))
        binding.folderEdit.setText(prefs.getString(Prefs.SYNOLOGY_FOLDER, "/photos"))
        binding.httpsSwitch.isChecked = prefs.getBoolean(Prefs.SYNOLOGY_USE_HTTPS, false)
    }

    private fun saveSettings() {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(Prefs.SYNOLOGY_HOST, binding.hostEdit.text.toString().trim())
            .putString(Prefs.SYNOLOGY_PORT, binding.portEdit.text.toString().trim().ifEmpty { "5000" })
            .putString(Prefs.SYNOLOGY_USERNAME, binding.usernameEdit.text.toString())
            .putString(Prefs.SYNOLOGY_PASSWORD, binding.passwordEdit.text.toString())
            .putString(Prefs.SYNOLOGY_FOLDER, binding.folderEdit.text.toString().trim().ifEmpty { "/photos" })
            .putBoolean(Prefs.SYNOLOGY_USE_HTTPS, binding.httpsSwitch.isChecked)
            .apply()
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testConnection() {
        // Save current values before testing
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(Prefs.SYNOLOGY_HOST, binding.hostEdit.text.toString().trim())
            .putString(Prefs.SYNOLOGY_PORT, binding.portEdit.text.toString().trim().ifEmpty { "5000" })
            .putString(Prefs.SYNOLOGY_USERNAME, binding.usernameEdit.text.toString())
            .putString(Prefs.SYNOLOGY_PASSWORD, binding.passwordEdit.text.toString())
            .putString(Prefs.SYNOLOGY_FOLDER, binding.folderEdit.text.toString().trim().ifEmpty { "/photos" })
            .putBoolean(Prefs.SYNOLOGY_USE_HTTPS, binding.httpsSwitch.isChecked)
            .apply()

        binding.testConnectionButton.isEnabled = false
        binding.testStatus.visibility = View.VISIBLE
        binding.testStatus.text = getString(R.string.testing_connection)

        lifecycleScope.launch {
            val source = SynologySource(this@SynologySetupActivity)
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
