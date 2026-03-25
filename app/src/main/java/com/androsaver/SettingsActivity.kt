package com.androsaver

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.screensaver_preferences, rootKey)

            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val currentMode = prefs.getString(Prefs.SCREENSAVER_MODE, Prefs.MODE_SLIDESHOW) ?: Prefs.MODE_SLIDESHOW
            updateModeVisibility(currentMode)

            findPreference<ListPreference>(Prefs.SCREENSAVER_MODE)?.setOnPreferenceChangeListener { _, newValue ->
                updateModeVisibility(newValue as String)
                true
            }
        }

        override fun onResume() {
            super.onResume()
            updateGoogleDriveStatus()
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            return when (preference.key) {
                "google_drive_setup" -> {
                    startActivity(Intent(requireContext(), GoogleDriveSetupActivity::class.java))
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
            findPreference<Preference>("google_drive_setup")?.summary = if (authorized) {
                getString(R.string.google_drive_authorized)
            } else {
                getString(R.string.google_drive_not_authorized)
            }
        }
    }
}
