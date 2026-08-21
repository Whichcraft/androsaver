package com.androsaver

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.androsaver.auth.DropboxAuthManager
import com.androsaver.databinding.ActivityDropboxSetupBinding

class DropboxSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDropboxSetupBinding
    private val authManager by lazy { DropboxAuthManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDropboxSetupBinding.inflate(layoutInflater)
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
            binding.appKeyEdit.setText(prefs.getString(Prefs.DROPBOX_APP_KEY, ""))
            binding.appSecretEdit.setText(prefs.getString(Prefs.DROPBOX_APP_SECRET, ""))
            binding.folderEdit.setText(prefs.getString(Prefs.DROPBOX_FOLDER, ""))
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.credential_storage_failed, Toast.LENGTH_LONG).show()
            binding.authorizeButton.isEnabled = false
            binding.revokeButton.isEnabled = false
        }
    }

    private fun saveAndAuthorize() {
        val appKey    = binding.appKeyEdit.text.toString().trim()
        val appSecret = binding.appSecretEdit.text.toString().trim()
        if (appKey.isEmpty() || appSecret.isEmpty()) {
            Toast.makeText(this, R.string.dropbox_app_key_required, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val saved = com.androsaver.Prefs.get(this).edit()
                .putString(Prefs.DROPBOX_APP_KEY,    appKey)
                .putString(Prefs.DROPBOX_APP_SECRET, appSecret)
                .putString(Prefs.DROPBOX_FOLDER,     binding.folderEdit.text.toString().trim())
                .commit()
            if (!saved) throw IllegalStateException("Credential storage failed")
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.credential_storage_failed, Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, DropboxAuthActivity::class.java))
    }

    private fun revokeAuth() {
        try {
            authManager.clearAuth()
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.credential_storage_failed, Toast.LENGTH_LONG).show()
            return
        }
        updateAuthStatus()
        Toast.makeText(this, R.string.auth_revoked, Toast.LENGTH_SHORT).show()
    }

    private fun updateAuthStatus() {
        val authorized = try { authManager.isAuthorized() } catch (_: Throwable) {
            binding.revokeButton.isEnabled = false
            return
        }
        binding.authStatusText.text = if (authorized)
            getString(R.string.dropbox_authorized)
        else
            getString(R.string.dropbox_not_authorized)
        binding.revokeButton.isEnabled = authorized
    }
}
