package com.androsaver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.androsaver.auth.DropboxAuthManager
import kotlinx.coroutines.launch

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

    // ── Main settings screen ──────────────────────────────────────────────────

    class SettingsFragment : PreferenceFragmentCompat() {

        private var pendingUpdateUrl: String? = null

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

            // Set flavor-appropriate default for update channel if not yet saved
            val defaultChannel = if (BuildConfig.FLAVOR == "dev") Prefs.UPDATE_CHANNEL_DEV
                                  else Prefs.UPDATE_CHANNEL_STABLE
            findPreference<ListPreference>(Prefs.UPDATE_CHANNEL)?.let { pref ->
                if (prefs.getString(Prefs.UPDATE_CHANNEL, null) == null) pref.value = defaultChannel
            }

            findPreference<ListPreference>(Prefs.UPDATE_CHANNEL)
                ?.setOnPreferenceChangeListener { _, _ ->
                    pendingUpdateUrl = null
                    updateAboutVersion()
                    checkForUpdates()
                    true
                }
        }

        override fun onResume() {
            super.onResume()
            updateSourcesSummary()
            updateAboutVersion()
            checkForUpdates()
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            return when (preference.key) {
                "preview_screensaver" -> {
                    startActivity(Intent(requireContext(), PreviewActivity::class.java))
                    true
                }
                "image_sources" -> {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.settings_container, SourcesFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                "about_app" -> {
                    val url = pendingUpdateUrl
                    if (url != null) {
                        findPreference<Preference>("about_app")?.summary = getString(R.string.update_downloading)
                        viewLifecycleOwner.lifecycleScope.launch {
                            UpdateInstaller.downloadAndInstall(requireContext(), url)
                        }
                    }
                    true
                }
                else -> super.onPreferenceTreeClick(preference)
            }
        }

        private fun updateModeVisibility(mode: String) {
            val isSlideshow = mode == Prefs.MODE_SLIDESHOW
            findPreference<Preference>("image_sources")?.isVisible = isSlideshow
            findPreference<androidx.preference.PreferenceCategory>("cat_slideshow")?.isVisible = isSlideshow
            findPreference<androidx.preference.PreferenceCategory>("cat_visualizer")?.isVisible = !isSlideshow
        }

        private fun updateSourcesSummary() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val sources = listOf(
                Prefs.ENABLE_GOOGLE_DRIVE to "Google Drive",
                Prefs.ENABLE_ONEDRIVE     to "OneDrive",
                Prefs.ENABLE_DROPBOX      to "Dropbox",
                Prefs.ENABLE_IMMICH       to "Immich",
                Prefs.ENABLE_NEXTCLOUD    to "Nextcloud",
                Prefs.ENABLE_SYNOLOGY     to "Synology",
                Prefs.ENABLE_LOCAL_STORAGE to "Device Photos"
            )
            val active = sources.filter { (key, _) -> prefs.getBoolean(key, false) }.map { it.second }
            findPreference<Preference>("image_sources")?.summary =
                if (active.isEmpty()) getString(R.string.sources_none_active)
                else active.joinToString(", ")
        }

        private fun updateAboutVersion() {
            findPreference<Preference>("about_app")?.summary =
                getString(R.string.about_version_summary, BuildConfig.VERSION_NAME)
        }

        private fun checkForUpdates() {
            viewLifecycleOwner.lifecycleScope.launch {
                val update = UpdateChecker.checkForUpdate(requireContext()) ?: return@launch
                pendingUpdateUrl = update.apkUrl
                findPreference<Preference>("about_app")?.summary =
                    getString(R.string.update_available, update.versionName)
            }
        }

        private fun hasAudioPermission() =
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    // ── Image sources sub-screen ──────────────────────────────────────────────

    class SourcesFragment : PreferenceFragmentCompat() {

        private val storagePermission = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        private val storagePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) findPreference<SwitchPreferenceCompat>(Prefs.ENABLE_LOCAL_STORAGE)?.isChecked = true
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.sources_preferences, rootKey)

            findPreference<SwitchPreferenceCompat>(Prefs.ENABLE_LOCAL_STORAGE)?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue == true && !hasStoragePermission()) {
                    storagePermissionLauncher.launch(storagePermission)
                    false  // revert; re-enabled if permission granted
                } else true
            }
        }

        override fun onResume() {
            super.onResume()
            updateGoogleDriveStatus()
            updateOneDriveStatus()
            updateDropboxStatus()
            updateImmichStatus()
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            return when (preference.key) {
                "google_drive_setup" -> {
                    startActivity(Intent(requireContext(), GoogleDriveSetupActivity::class.java))
                    true
                }
                "onedrive_setup" -> {
                    startActivity(Intent(requireContext(), OneDriveSetupActivity::class.java))
                    true
                }
                "dropbox_setup" -> {
                    startActivity(Intent(requireContext(), DropboxSetupActivity::class.java))
                    true
                }
                "immich_setup" -> {
                    startActivity(Intent(requireContext(), ImmichSetupActivity::class.java))
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

        private fun updateGoogleDriveStatus() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val authorized = !prefs.getString(Prefs.GOOGLE_REFRESH_TOKEN, null).isNullOrEmpty()
            findPreference<Preference>("google_drive_setup")?.summary = if (authorized)
                getString(R.string.google_drive_authorized) else getString(R.string.google_drive_not_authorized)
        }

        private fun updateOneDriveStatus() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val authorized = !prefs.getString(Prefs.ONEDRIVE_REFRESH_TOKEN, null).isNullOrEmpty()
            findPreference<Preference>("onedrive_setup")?.summary = if (authorized)
                getString(R.string.onedrive_authorized) else getString(R.string.onedrive_not_authorized)
        }

        private fun updateDropboxStatus() {
            val authorized = DropboxAuthManager(requireContext()).isAuthorized()
            findPreference<Preference>("dropbox_setup")?.summary = if (authorized)
                getString(R.string.dropbox_authorized) else getString(R.string.dropbox_not_authorized)
        }

        private fun updateImmichStatus() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val configured = !prefs.getString(Prefs.IMMICH_HOST, null).isNullOrEmpty() &&
                             !prefs.getString(Prefs.IMMICH_API_KEY, null).isNullOrEmpty()
            findPreference<Preference>("immich_setup")?.summary = if (configured)
                getString(R.string.immich_authorized) else getString(R.string.immich_not_authorized)
        }

        private fun hasStoragePermission() =
            ContextCompat.checkSelfPermission(requireContext(), storagePermission) == PackageManager.PERMISSION_GRANTED
    }
}
