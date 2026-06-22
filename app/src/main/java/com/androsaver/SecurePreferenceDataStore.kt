package com.androsaver

import android.content.Context
import androidx.preference.PreferenceDataStore

class SecurePreferenceDataStore(context: Context) : PreferenceDataStore() {
    private val securePrefs = SecurePreferences.wrap(context)

    override fun putString(key: String, value: String?) {
        securePrefs.edit().putString(key, value).apply()
    }

    override fun getString(key: String, defValue: String?): String? {
        return securePrefs.getString(key, defValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        securePrefs.edit().putBoolean(key, value).apply()
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return securePrefs.getBoolean(key, defValue)
    }

    override fun putInt(key: String, value: Int) {
        securePrefs.edit().putInt(key, value).apply()
    }

    override fun getInt(key: String, defValue: Int): Int {
        return securePrefs.getInt(key, defValue)
    }

    override fun putLong(key: String, value: Long) {
        securePrefs.edit().putLong(key, value).apply()
    }

    override fun getLong(key: String, defValue: Long): Long {
        return securePrefs.getLong(key, defValue)
    }

    override fun putFloat(key: String, value: Float) {
        securePrefs.edit().putFloat(key, value).apply()
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return securePrefs.getFloat(key, defValue)
    }

    override fun putStringSet(key: String, values: Set<String>?) {
        securePrefs.edit().putStringSet(key, values).apply()
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        return securePrefs.getStringSet(key, defValues)
    }
}
