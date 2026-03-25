package com.androsaver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val storagePermission = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        private val storagePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) findPreference<SwitchPreferenceCompat>(Prefs.ENABLE_LOCAL_STORAGE)?.isChecked = true
        }

        private val audioPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) android.widget.Toast.makeText(
                requireContext(), R.string.audio_permission_denied, android.widget.Toast.LENGTH_LONG
            ).show()
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.screensaver_preferences, rootKey)

            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val currentMode = prefs.getString(Prefs.SCREENSAVER_MODE, Prefs.MODE_SLIDESHOW) ?: Prefs.MODE_SLIDESHOW
            updateModeVisibility(currentMode)

            findPreference<ListPreference>(Prefs.SCREENSAVER_MODE)?.setOnPreferenceChangeListener { _, newValue ->
                updateModeVisibility(newValue as String)
                if (newValue == Prefs.MODE_VISUALIZER && !hasAudioPermission()) {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                true
            }

            findPreference<SwitchPreferenceCompat>(Prefs.ENABLE_LOCAL_STORAGE)?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue == true && !hasStoragePermission()) {
                    storagePermissionLauncher.launch(storagePermission)
                    false  // revert; will be re-enabled if permission granted
                } else true
            }
        }

        override fun onResume() {
            super.onResume()
            updateGoogleDriveStatus()
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            return when (preference.key) {
                "preview_screensaver" -> {
                    startActivity(Intent(requireContext(), PreviewActivity::class.java))
                    true
                }
                "google_drive_setup" -> {
                    startActivity(Intent(requireContext(), GoogleDriveSetupActivity::class.java))
                    true
                }
                "nextcloud_setup" -> {
                    startActivity(Intent(requireContext(), NextcloudSetupActivity::class.java))
                    true
                }
                "synology_setup" -> {
                    startActivity(Intent(requireContext(), SynologySetupActivity::class.java))
                    true
                }
                else -> super.onPreferenceTreeClick(preference)
            }
        }

        private fun updateModeVisibility(mode: String) {
            val isSlideshow = mode == Prefs.MODE_SLIDESHOW
            findPreference<PreferenceCategory>("cat_sources")?.isVisible = isSlideshow
            findPreference<PreferenceCategory>("cat_slideshow")?.isVisible = isSlideshow
            findPreference<PreferenceCategory>("cat_visualizer")?.isVisible = !isSlideshow
        }

        private fun updateGoogleDriveStatus() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val authorized = !prefs.getString(Prefs.GOOGLE_REFRESH_TOKEN, null).isNullOrEmpty()
            findPreference<Preference>("google_drive_setup")?.summary = if (authorized)
                getString(R.string.google_drive_authorized)
            else
                getString(R.string.google_drive_not_authorized)
        }

        private fun hasStoragePermission() =
            ContextCompat.checkSelfPermission(requireContext(), storagePermission) == PackageManager.PERMISSION_GRANTED

        private fun hasAudioPermission() =
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
}
