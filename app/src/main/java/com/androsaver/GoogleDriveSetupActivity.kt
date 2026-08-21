package com.androsaver

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.androsaver.databinding.ActivityGoogleDriveSetupBinding

class GoogleDriveSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGoogleDriveSetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGoogleDriveSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSavedSettings()

        binding.authorizeButton.setOnClickListener { saveAndAuthorize() }
        binding.revokeButton.setOnClickListener { revokeAuth() }
    }

    override fun onResume() {
        super.onResume()
        updateAuthStatus()
    }

    private fun loadSavedSettings() {
        try {
            val prefs = com.androsaver.Prefs.get(this)
            binding.clientIdEdit.setText(prefs.getString(Prefs.GOOGLE_CLIENT_ID, ""))
            binding.clientSecretEdit.setText(prefs.getString(Prefs.GOOGLE_CLIENT_SECRET, ""))
            binding.folderIdEdit.setText(prefs.getString(Prefs.GOOGLE_FOLDER_ID, ""))
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.credential_storage_failed, Toast.LENGTH_LONG).show()
            binding.authorizeButton.isEnabled = false
            binding.revokeButton.isEnabled = false
        }
    }

    private fun saveAndAuthorize() {
        val clientId = binding.clientIdEdit.text.toString().trim()
        val clientSecret = binding.clientSecretEdit.text.toString().trim()

        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            Toast.makeText(this, R.string.client_id_required, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val saved = com.androsaver.Prefs.get(this).edit()
                .putString(Prefs.GOOGLE_CLIENT_ID, clientId)
                .putString(Prefs.GOOGLE_CLIENT_SECRET, clientSecret)
                .putString(Prefs.GOOGLE_FOLDER_ID, binding.folderIdEdit.text.toString().trim())
                .commit()
            if (!saved) throw IllegalStateException("Credential storage failed")
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.credential_storage_failed, Toast.LENGTH_LONG).show()
            return
        }

        startActivity(Intent(this, GoogleAuthActivity::class.java))
    }

    private fun revokeAuth() {
        try {
            val cleared = com.androsaver.Prefs.get(this).edit()
                .remove(Prefs.GOOGLE_ACCESS_TOKEN)
                .remove(Prefs.GOOGLE_REFRESH_TOKEN)
                .commit()
            if (!cleared) throw IllegalStateException("Credential storage failed")
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.credential_storage_failed, Toast.LENGTH_LONG).show()
            return
        }
        updateAuthStatus()
        Toast.makeText(this, R.string.auth_revoked, Toast.LENGTH_SHORT).show()
    }

    private fun updateAuthStatus() {
        val authorized = try {
            !com.androsaver.Prefs.get(this).getString(Prefs.GOOGLE_REFRESH_TOKEN, null).isNullOrEmpty()
        } catch (_: Throwable) {
            binding.revokeButton.isEnabled = false
            return
        }
        binding.authStatusText.text = if (authorized) {
            getString(R.string.google_drive_authorized)
        } else {
            getString(R.string.google_drive_not_authorized)
        }
        binding.revokeButton.isEnabled = authorized
    }
}
