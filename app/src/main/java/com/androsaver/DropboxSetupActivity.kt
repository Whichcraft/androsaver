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
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.appKeyEdit.setText(prefs.getString(Prefs.DROPBOX_APP_KEY, ""))
        binding.appSecretEdit.setText(prefs.getString(Prefs.DROPBOX_APP_SECRET, ""))
        binding.folderEdit.setText(prefs.getString(Prefs.DROPBOX_FOLDER, ""))
    }

    private fun saveAndAuthorize() {
        val appKey    = binding.appKeyEdit.text.toString().trim()
        val appSecret = binding.appSecretEdit.text.toString().trim()
        if (appKey.isEmpty() || appSecret.isEmpty()) {
            Toast.makeText(this, R.string.dropbox_app_key_required, Toast.LENGTH_SHORT).show()
            return
        }
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(Prefs.DROPBOX_APP_KEY,    appKey)
            .putString(Prefs.DROPBOX_APP_SECRET, appSecret)
            .putString(Prefs.DROPBOX_FOLDER,     binding.folderEdit.text.toString().trim())
            .apply()
        startActivity(Intent(this, DropboxAuthActivity::class.java))
    }

    private fun revokeAuth() {
        authManager.clearAuth()
        updateAuthStatus()
        Toast.makeText(this, R.string.auth_revoked, Toast.LENGTH_SHORT).show()
    }

    private fun updateAuthStatus() {
        val authorized = authManager.isAuthorized()
        binding.authStatusText.text = if (authorized)
            getString(R.string.dropbox_authorized)
        else
            getString(R.string.dropbox_not_authorized)
        binding.revokeButton.isEnabled = authorized
    }
}
