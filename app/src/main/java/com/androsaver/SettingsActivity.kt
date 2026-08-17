package com.androsaver

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
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
        try {
            PrefetchScheduler.schedule(this)
        } catch (e: Throwable) {
            if (BuildConfig.DEBUG_LOGGING) android.util.Log.e("SettingsActivity", "Prefetch scheduler failed", e)
        }
        if (savedInstanceState == null) {
            try {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, SettingsFragment())
                    .commitNow()
            } catch (e: Throwable) {
                if (BuildConfig.DEBUG_LOGGING) android.util.Log.e("SettingsActivity", "Failed to start settings fragment", e)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, StartupErrorFragment.newInstance("Settings failed to start", e))
                    .commitNowAllowingStateLoss()
            }
        }
    }

    // ── Main settings screen ──────────────────────────────────────────────────

    private class StartupErrorFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val title = requireArguments().getString(ARG_TITLE).orEmpty()
            val details = requireArguments().getString(ARG_DETAILS).orEmpty()
            return TextView(requireContext()).apply {
                text = buildString {
                    append(title.ifBlank { "Settings failed to load" })
                    if (details.isNotBlank()) {
                        append("\n\n")
                        append(details)
                    }
                }
                setTextIsSelectable(true)
                setPadding(48, 48, 48, 48)
            }
        }

        companion object {
            private const val ARG_TITLE = "title"
            private const val ARG_DETAILS = "details"

            fun newInstance(title: String, throwable: Throwable): StartupErrorFragment {
                return StartupErrorFragment().apply {
                    arguments = Bundle().apply {
                        putString(ARG_TITLE, title)
                        putString(ARG_DETAILS, buildString {
                            append(throwable::class.java.name)
                            throwable.message?.takeIf { it.isNotBlank() }?.let {
                                append(": ")
                                append(it)
                            }
                            append("\n\n")
                            append(android.util.Log.getStackTraceString(throwable))
                        })
                    }
                }
            }
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private var pendingUpdateUrl: String? = null

        private val staticImagePicker = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
            val uri = result.data?.data ?: return@registerForActivityResult
            try {
                val flags = result.data?.flags?.and(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                ) ?: Intent.FLAG_GRANT_READ_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // Some providers do not offer persistable permissions; the URI can
                // still be used for the current session.
            }
            Prefs.get(requireContext()).edit().putString(Prefs.STATIC_IMAGE_URI, uri.toString()).apply()
            updateStaticImageSummary(uri)
        }

        private val audioPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) android.widget.Toast.makeText(
                requireContext(), R.string.audio_permission_denied, android.widget.Toast.LENGTH_LONG
            ).show()
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            try {
                preferenceManager.preferenceDataStore = SecurePreferenceDataStore(requireContext())
                setPreferencesFromResource(R.xml.screensaver_preferences, rootKey)

                val prefs = Prefs.get(requireContext())
                val currentMode = prefs.getString(Prefs.SCREENSAVER_MODE, Prefs.MODE_SLIDESHOW) ?: Prefs.MODE_SLIDESHOW
                updateModeVisibility(currentMode)
                updateStaticImageSummary()

                findPreference<Preference>(Prefs.STATIC_IMAGE_URI)?.setOnPreferenceClickListener {
                    staticImagePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    })
                    true
                }

                findPreference<ListPreference>(Prefs.SCREENSAVER_MODE)?.setOnPreferenceChangeListener { _, newValue ->
                    updateModeVisibility(newValue as String)
                    if (newValue == Prefs.MODE_VISUALIZER && !hasAudioPermission()) {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    true
                }

                configureWeatherPreference(Prefs.WEATHER_CITY)
                configureWeatherPreference(Prefs.WEATHER_API_KEY)
                findPreference<SwitchPreferenceCompat>(Prefs.WEATHER_ENABLED)
                    ?.setOnPreferenceChangeListener { _, newValue ->
                        updateWeatherSummary(enabled = newValue as Boolean)
                        true
                    }
                updateWeatherSummary()

            } catch (e: Throwable) {
                if (BuildConfig.DEBUG_LOGGING) android.util.Log.e("SettingsActivity", "Settings fragment failed to load", e)
                preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
                    addPreference(Preference(requireContext()).apply {
                        title = "Settings failed to load"
                        summary = e::class.java.name + ": " + (e.message ?: "see stack trace above")
                        isSelectable = false
                    })
                }
            }
        }

        override fun onResume() {
            super.onResume()
            try {
                updateSourcesSummary()
                updateWeatherSummary()
                updateAboutVersion()
                checkForUpdates()
                val prefs = Prefs.get(requireContext())
                val currentMode = prefs.getString(Prefs.SCREENSAVER_MODE, Prefs.MODE_SLIDESHOW)
                if (currentMode == Prefs.MODE_VISUALIZER && !hasAudioPermission()) {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            } catch (e: Throwable) {
                if (BuildConfig.DEBUG_LOGGING) android.util.Log.e("SettingsActivity", "Settings fragment resume failed", e)
            }
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
                        val appContext = requireContext().applicationContext
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            try {
                                UpdateInstaller.downloadAndInstall(appContext, url)
                            } catch (e: Exception) {
                                if (BuildConfig.DEBUG_LOGGING) android.util.Log.e("SettingsActivity", "Update failed", e)
                                if (isAdded) {
                                    findPreference<Preference>("about_app")?.summary = getString(R.string.update_failed)
                                    android.widget.Toast.makeText(appContext, R.string.update_failed_toast, android.widget.Toast.LENGTH_LONG).show()
                                }
                                pendingUpdateUrl = null
                            }
                        }
                    } else {
                        findPreference<Preference>("about_app")?.summary = getString(R.string.update_checking)
                        viewLifecycleOwner.lifecycleScope.launch {
                            val update = UpdateChecker.checkForUpdate()
                            if (update != null) {
                                pendingUpdateUrl = update.apkUrl
                                findPreference<Preference>("about_app")?.summary =
                                    getString(R.string.update_available, update.versionName)
                            } else {
                                updateAboutVersion()
                            }
                        }
                    }
                    true
                }
                else -> super.onPreferenceTreeClick(preference)
            }
        }

        private fun configureWeatherPreference(key: String) {
            findPreference<EditTextPreference>(key)?.apply {
                setOnBindEditTextListener { editText ->
                    editText.inputType = InputType.TYPE_CLASS_TEXT
                    editText.imeOptions = EditorInfo.IME_ACTION_DONE
                    editText.maxLines = 1
                }
                setOnPreferenceChangeListener { _, _ -> updateWeatherSummary(); true }
            }
        }

        private fun updateWeatherSummary(enabled: Boolean? = null) {
            val prefs = Prefs.get(requireContext())
            val isOn  = enabled ?: prefs.getBoolean(Prefs.WEATHER_ENABLED, false)
            val city  = prefs.getString(Prefs.WEATHER_CITY, "") ?: ""
            val key   = prefs.getString(Prefs.WEATHER_API_KEY, "") ?: ""
            val pref  = findPreference<SwitchPreferenceCompat>(Prefs.WEATHER_ENABLED) ?: return
            pref.summary = when {
                !isOn          -> "Display current temperature in the top-right corner"
                city.isBlank() -> "⚠ Enter a city name below"
                key.isBlank()  -> "⚠ API key required — free at openweathermap.org/api"
                else           -> "Showing weather for $city"
            }
        }

        private fun updateModeVisibility(mode: String) {
            val isSlideshow = mode == Prefs.MODE_SLIDESHOW
            val isStatic = mode == Prefs.MODE_STATIC
            val isVisualizer = mode == Prefs.MODE_VISUALIZER
            findPreference<Preference>("image_sources")?.isVisible = isSlideshow
            findPreference<androidx.preference.PreferenceCategory>("cat_slideshow")?.isVisible = isSlideshow
            findPreference<androidx.preference.PreferenceCategory>("cat_static")?.isVisible = isStatic
            findPreference<androidx.preference.PreferenceCategory>("cat_visualizer")?.isVisible = isVisualizer
        }

        private fun updateStaticImageSummary(uri: Uri? = null) {
            val preference = findPreference<Preference>(Prefs.STATIC_IMAGE_URI) ?: return
            val selected = uri?.toString()
                ?: Prefs.get(requireContext()).getString(Prefs.STATIC_IMAGE_URI, null)
            preference.summary = selected?.let {
                Uri.parse(it).lastPathSegment?.substringAfterLast('/') ?: "Selected image"
            } ?: getString(R.string.static_image_not_selected)
        }

        private fun updateSourcesSummary() {
            val prefs = Prefs.get(requireContext())
            val summary = StringBuilder()

            fun appendSource(enabled: Boolean, name: String) {
                if (!enabled) return
                if (summary.isNotEmpty()) summary.append(", ")
                summary.append(name)
            }

            appendSource(prefs.getBoolean(Prefs.ENABLE_GOOGLE_DRIVE, false), "Google Drive")
            appendSource(prefs.getBoolean(Prefs.ENABLE_ONEDRIVE, false), "OneDrive")
            appendSource(prefs.getBoolean(Prefs.ENABLE_DROPBOX, false), "Dropbox")
            appendSource(prefs.getBoolean(Prefs.ENABLE_IMMICH, false), "Immich")
            appendSource(prefs.getBoolean(Prefs.ENABLE_NEXTCLOUD, false), "Nextcloud")
            appendSource(prefs.getBoolean(Prefs.ENABLE_SYNOLOGY, false), "Synology")
            appendSource(prefs.getBoolean(Prefs.ENABLE_LOCAL_STORAGE, false), "Device Photos")

            findPreference<Preference>("image_sources")?.summary =
                if (summary.isEmpty()) getString(R.string.sources_none_active) else summary.toString()
        }

        private fun updateAboutVersion() {
            val channel = if (BuildConfig.FLAVOR == "dev") Prefs.UPDATE_CHANNEL_DEV
                          else Prefs.UPDATE_CHANNEL_STABLE
            findPreference<Preference>("about_app")?.summary =
                getString(R.string.about_version_summary, BuildConfig.VERSION_NAME, channel)
        }

        private fun checkForUpdates() {
            viewLifecycleOwner.lifecycleScope.launch {
                val update = UpdateChecker.checkForUpdate()
                if (update != null) {
                    pendingUpdateUrl = update.apkUrl
                    findPreference<Preference>("about_app")?.summary =
                        getString(R.string.update_available, update.versionName)
                }
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
            try {
                preferenceManager.preferenceDataStore = SecurePreferenceDataStore(requireContext())
                setPreferencesFromResource(R.xml.sources_preferences, rootKey)

                findPreference<SwitchPreferenceCompat>(Prefs.ENABLE_LOCAL_STORAGE)?.setOnPreferenceChangeListener { _, newValue ->
                    if (newValue == true && !hasStoragePermission()) {
                        storagePermissionLauncher.launch(storagePermission)
                        false  // revert; re-enabled if permission granted
                    } else true
                }
            } catch (e: Throwable) {
                if (BuildConfig.DEBUG_LOGGING) android.util.Log.e("SettingsActivity", "Sources fragment failed to load", e)
                preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
                    addPreference(Preference(requireContext()).apply {
                        title = "Image sources failed to load"
                        summary = e::class.java.name + ": " + (e.message ?: "see stack trace above")
                        isSelectable = false
                    })
                }
            }
        }

        override fun onResume() {
            super.onResume()
            try {
                updateGoogleDriveStatus()
                updateOneDriveStatus()
                updateDropboxStatus()
                updateImmichStatus()
                updateNextcloudStatus()
                updateSynologyStatus()
            } catch (e: Throwable) {
                if (BuildConfig.DEBUG_LOGGING) android.util.Log.e("SettingsActivity", "Sources fragment resume failed", e)
            }
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
            val prefs = Prefs.get(requireContext())
            val authorized = !prefs.getString(Prefs.GOOGLE_REFRESH_TOKEN, null).isNullOrEmpty()
            findPreference<Preference>("google_drive_setup")?.summary = if (authorized)
                getString(R.string.google_drive_authorized) else getString(R.string.google_drive_not_authorized)
        }

        private fun updateOneDriveStatus() {
            val prefs = Prefs.get(requireContext())
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
            val prefs = Prefs.get(requireContext())
            val configured = !prefs.getString(Prefs.IMMICH_HOST, null).isNullOrEmpty() &&
                             !prefs.getString(Prefs.IMMICH_API_KEY, null).isNullOrEmpty()
            findPreference<Preference>("immich_setup")?.summary = if (configured)
                getString(R.string.immich_authorized) else getString(R.string.immich_not_authorized)
        }

        private fun updateNextcloudStatus() {
            val prefs = Prefs.get(requireContext())
            val configured = !prefs.getString(Prefs.NEXTCLOUD_HOST, null).isNullOrEmpty() &&
                             !prefs.getString(Prefs.NEXTCLOUD_USERNAME, null).isNullOrEmpty()
            findPreference<Preference>("nextcloud_setup")?.summary = if (configured)
                getString(R.string.nextcloud_authorized) else getString(R.string.nextcloud_not_authorized)
        }

        private fun updateSynologyStatus() {
            val prefs = Prefs.get(requireContext())
            val configured = !prefs.getString(Prefs.SYNOLOGY_HOST, null).isNullOrEmpty()
            findPreference<Preference>("synology_setup")?.summary = if (configured)
                getString(R.string.synology_authorized) else getString(R.string.synology_not_authorized)
        }

        private fun hasStoragePermission() =
            ContextCompat.checkSelfPermission(requireContext(), storagePermission) == PackageManager.PERMISSION_GRANTED
    }
}
