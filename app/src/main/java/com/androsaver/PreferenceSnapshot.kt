package com.androsaver

import android.content.SharedPreferences

class PreferenceSnapshot(
    private val prefs: SharedPreferences,
    private val keys: Set<String>
) {
    private val values = prefs.all.filterKeys { it in keys }

    fun restore() {
        val editor = prefs.edit()
        keys.filter { it !in values }.forEach { editor.remove(it) }
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
    }
}
