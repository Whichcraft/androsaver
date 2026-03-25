package com.androsaver

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.androsaver.auth.OneDriveAuthManager
import com.androsaver.databinding.ActivityOneDriveSetupBinding

class OneDriveSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOneDriveSetupBinding
    private val authManager by lazy { OneDriveAuthManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOneDriveSetupBinding.inflate(layoutInflater)
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
        binding.clientIdEdit.setText(prefs.getString(Prefs.ONEDRIVE_CLIENT_ID, ""))
        binding.folderEdit.setText(prefs.getString(Prefs.ONEDRIVE_FOLDER, ""))
    }

    private fun saveAndAuthorize() {
        val clientId = binding.clientIdEdit.text.toString().trim()
        if (clientId.isEmpty()) {
            Toast.makeText(this, R.string.onedrive_client_id_required, Toast.LENGTH_SHORT).show()
            return
        }
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(Prefs.ONEDRIVE_CLIENT_ID, clientId)
            .putString(Prefs.ONEDRIVE_FOLDER, binding.folderEdit.text.toString().trim())
            .apply()
        startActivity(Intent(this, OneDriveAuthActivity::class.java))
    }

    private fun revokeAuth() {
        authManager.clearAuth()
        updateAuthStatus()
        Toast.makeText(this, R.string.auth_revoked, Toast.LENGTH_SHORT).show()
    }

    private fun updateAuthStatus() {
        val authorized = authManager.isAuthorized()
        binding.authStatusText.text = if (authorized)
            getString(R.string.onedrive_authorized)
        else
            getString(R.string.onedrive_not_authorized)
        binding.revokeButton.isEnabled = authorized
    }
}
