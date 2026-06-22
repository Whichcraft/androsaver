package com.androsaver

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences private constructor(context: Context) : SharedPreferences {

    companion object {
        private const val TAG = "SecurePreferences"
        private const val SECURE_PREFS_FILE = "secure_prefs"

        private val SENSITIVE_KEYS = setOf(
            Prefs.GOOGLE_CLIENT_ID,
            Prefs.GOOGLE_CLIENT_SECRET,
            Prefs.GOOGLE_ACCESS_TOKEN,
            Prefs.GOOGLE_REFRESH_TOKEN,
            Prefs.ONEDRIVE_CLIENT_ID,
            Prefs.ONEDRIVE_ACCESS_TOKEN,
            Prefs.ONEDRIVE_REFRESH_TOKEN,
            Prefs.DROPBOX_APP_KEY,
            Prefs.DROPBOX_APP_SECRET,
            Prefs.DROPBOX_ACCESS_TOKEN,
            Prefs.DROPBOX_REFRESH_TOKEN,
            Prefs.IMMICH_API_KEY,
            Prefs.NEXTCLOUD_PASSWORD,
            Prefs.SYNOLOGY_PASSWORD,
            Prefs.WEATHER_API_KEY
        )

        @Volatile
        private var instance: SecurePreferences? = null

        fun wrap(context: Context): SharedPreferences {
            return instance ?: synchronized(this) {
                instance ?: SecurePreferences(context.applicationContext).also { instance = it }
            }
        }
    }

    private val plainPrefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var encryptedPrefs: SharedPreferences? = null

    init {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            migrateIfNeeded()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, falling back to plaintext storage", e)
        }
    }

    private fun migrateIfNeeded() {
        val enc = encryptedPrefs ?: return
        val plainKeys = plainPrefs.all
        var editor: SharedPreferences.Editor? = null
        var encEditor: SharedPreferences.Editor? = null

        for (key in SENSITIVE_KEYS) {
            if (plainPrefs.contains(key)) {
                val value = plainKeys[key]
                if (value != null) {
                    if (encEditor == null) encEditor = enc.edit()
                    if (editor == null) editor = plainPrefs.edit()

                    when (value) {
                        is String -> encEditor?.putString(key, value)
                        is Boolean -> encEditor?.putBoolean(key, value)
                        is Int -> encEditor?.putInt(key, value)
                        is Long -> encEditor?.putLong(key, value)
                        is Float -> encEditor?.putFloat(key, value)
                    }
                    editor?.remove(key)
                }
            }
        }

        encEditor?.apply()
        editor?.apply()
    }

    private fun getPrefs(key: String): SharedPreferences {
        return if (key in SENSITIVE_KEYS) {
            encryptedPrefs ?: plainPrefs
        } else {
            plainPrefs
        }
    }

    override fun getAll(): Map<String, *> {
        val allPlain = plainPrefs.all.toMutableMap()
        val enc = encryptedPrefs
        if (enc != null) {
            allPlain.putAll(enc.all)
        }
        return allPlain
    }

    override fun getString(key: String, defValue: String?): String? {
        return getPrefs(key).getString(key, defValue)
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        return getPrefs(key).getStringSet(key, defValues)
    }

    override fun getInt(key: String, defValue: Int): Int {
        return getPrefs(key).getInt(key, defValue)
    }

    override fun getLong(key: String, defValue: Long): Long {
        return getPrefs(key).getLong(key, defValue)
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return getPrefs(key).getFloat(key, defValue)
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return getPrefs(key).getBoolean(key, defValue)
    }

    override fun contains(key: String): Boolean {
        return getPrefs(key).contains(key)
    }

    override fun edit(): SharedPreferences.Editor {
        return Editor(plainPrefs.edit(), encryptedPrefs?.edit())
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        plainPrefs.registerOnSharedPreferenceChangeListener(listener)
        encryptedPrefs?.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        plainPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        encryptedPrefs?.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private inner class Editor(
        private val plainEditor: SharedPreferences.Editor,
        private val encryptedEditor: SharedPreferences.Editor?
    ) : SharedPreferences.Editor {

        private fun getEditor(key: String): SharedPreferences.Editor {
            return if (key in SENSITIVE_KEYS) {
                encryptedEditor ?: plainEditor
            } else {
                plainEditor
            }
        }

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            getEditor(key).putString(key, value)
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            getEditor(key).putStringSet(key, values)
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            getEditor(key).putInt(key, value)
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            getEditor(key).putLong(key, value)
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            getEditor(key).putFloat(key, value)
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            getEditor(key).putBoolean(key, value)
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            plainEditor.remove(key)
            encryptedEditor?.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            plainEditor.clear()
            encryptedEditor?.clear()
            return this
        }

        override fun commit(): Boolean {
            val r1 = plainEditor.commit()
            val r2 = encryptedEditor?.commit() ?: true
            return r1 && r2
        }

        override fun apply() {
            plainEditor.apply()
            encryptedEditor?.apply()
        }
    }
}
