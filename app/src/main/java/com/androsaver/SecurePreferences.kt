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
            try {
                migrateIfNeeded()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to migrate to encrypted prefs", e)
            }
        } catch (e: Throwable) {
            encryptedPrefs = null
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences", e)
        }
    }

    private fun migrateIfNeeded() {
        val enc = encryptedPrefs ?: return
        val cleaned = CredentialMigration.migrate(plainPrefs.all, enc.all.keys, SENSITIVE_KEYS,
            writeEncrypted = { values ->
                val editor = enc.edit()
                values.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        else -> throw IllegalStateException("Unsupported credential preference type")
                    }
                }
                editor.commit()
            },
            removePlain = { keys ->
                val editor = plainPrefs.edit()
                keys.forEach { editor.remove(it) }
                editor.commit()
            })
        if (!cleaned) Log.w(TAG, "Encrypted migration succeeded but plaintext cleanup did not")
    }

    private fun ensureMigrationBeforeCredentialEdit() {
        val enc = encryptedPrefs ?: throw IllegalStateException("Encrypted credential storage unavailable")
        // If initial migration failed, copy *all* legacy accounts before a new
        // credential transaction can record the global migration marker.
        if (!enc.getBoolean(CredentialMigration.MARKER, false)) migrateIfNeeded()
    }

    private fun getPrefs(key: String): SharedPreferences {
        return if (key in SENSITIVE_KEYS) {
            encryptedPrefs ?: throw IllegalStateException("Encrypted credential storage unavailable")
        } else {
            plainPrefs
        }
    }

    override fun getAll(): Map<String, *> {
        return try {
            val allPlain = plainPrefs.all.filterKeys { it !in SENSITIVE_KEYS }.toMutableMap()
            val enc = encryptedPrefs
            if (enc != null) {
                allPlain.putAll(enc.all)
            }
            allPlain.remove(CredentialMigration.MARKER)
            allPlain
        } catch (e: Throwable) {
            Log.e(TAG, "Decryption/Read failed while loading preferences", e)
            throw IllegalStateException("Encrypted credential storage unavailable", e)
        }
    }

    override fun getString(key: String, defValue: String?): String? {
        return try {
            getPrefs(key).getString(key, defValue)
        } catch (e: Throwable) {
            if (key in SENSITIVE_KEYS) throw IllegalStateException("Encrypted credential read failed", e)
            Log.e(TAG, "Preference read failed for key $key", e)
            defValue
        }
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        return try {
            getPrefs(key).getStringSet(key, defValues) ?: defValues ?: emptySet()
        } catch (e: Throwable) {
            if (key in SENSITIVE_KEYS) throw IllegalStateException("Encrypted credential read failed", e)
            defValues ?: emptySet()
        }
    }

    override fun getInt(key: String, defValue: Int): Int {
        return try {
            getPrefs(key).getInt(key, defValue)
        } catch (e: Throwable) {
            if (key in SENSITIVE_KEYS) throw IllegalStateException("Encrypted credential read failed", e)
            defValue
        }
    }

    override fun getLong(key: String, defValue: Long): Long {
        return try {
            getPrefs(key).getLong(key, defValue)
        } catch (e: Throwable) {
            if (key in SENSITIVE_KEYS) throw IllegalStateException("Encrypted credential read failed", e)
            defValue
        }
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return try {
            getPrefs(key).getFloat(key, defValue)
        } catch (e: Throwable) {
            if (key in SENSITIVE_KEYS) throw IllegalStateException("Encrypted credential read failed", e)
            defValue
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return try {
            getPrefs(key).getBoolean(key, defValue)
        } catch (e: Throwable) {
            if (key in SENSITIVE_KEYS) throw IllegalStateException("Encrypted credential read failed", e)
            defValue
        }
    }

    override fun contains(key: String): Boolean {
        return try {
            getPrefs(key).contains(key)
        } catch (e: Throwable) {
            if (key in SENSITIVE_KEYS) throw IllegalStateException("Encrypted credential read failed", e)
            false
        }
    }

    override fun edit(): SharedPreferences.Editor {
        val plainEditor = plainPrefs.edit()
        val encryptedEditor = try { encryptedPrefs?.edit() } catch (e: Throwable) {
            Log.e(TAG, "Failed to open encrypted prefs editor", e)
            throw IllegalStateException("Encrypted credential storage unavailable", e)
        }
        return Editor(plainEditor, encryptedEditor)
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
        private var sensitiveTouched = false
        private var migrationChecked = false
        private fun ensureMigration() {
            if (!migrationChecked) {
                ensureMigrationBeforeCredentialEdit()
                migrationChecked = true
            }
        }

        private fun getEditor(key: String): SharedPreferences.Editor {
            return if (key in SENSITIVE_KEYS) {
                ensureMigration()
                sensitiveTouched = true
                encryptedEditor ?: throw IllegalStateException("Encrypted credential storage unavailable")
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
            if (key in SENSITIVE_KEYS) {
                ensureMigration()
                sensitiveTouched = true
            }
            plainEditor.remove(key)
            encryptedEditor?.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            // Clearing all accounts intentionally supersedes every legacy value.
            migrationChecked = true
            sensitiveTouched = true
            plainEditor.clear()
            encryptedEditor?.clear()
            return this
        }

        override fun commit(): Boolean {
            if (sensitiveTouched) {
                val encryptedCommitted = try {
                    encryptedEditor?.putBoolean(CredentialMigration.MARKER, true)?.commit() ?: false
                } catch (e: Throwable) {
                    Log.e(TAG, "Encryption/Commit failed", e)
                    false
                }
                if (!encryptedCommitted) return false
            }
            return plainEditor.commit()
        }

        override fun apply() {
            if (sensitiveTouched) {
                if (!(encryptedEditor?.putBoolean(CredentialMigration.MARKER, true)?.commit() ?: false)) {
                    throw IllegalStateException("Encrypted credential write failed")
                }
                plainEditor.apply()
            } else {
                plainEditor.apply()
            }
        }
    }
}
