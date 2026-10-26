package com.androsaver

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.androsaver.auth.DeviceAuthViewModel
import org.junit.Assert.*
import org.junit.Test

class AuthorizationRecreationTest {
    @Test fun googleKeepsItsAuthorizationViewModelAcrossRecreation() = exercise(GoogleAuthActivity::class.java)
    @Test fun oneDriveKeepsItsAuthorizationViewModelAcrossRecreation() = exercise(OneDriveAuthActivity::class.java)
    private fun exercise(type: Class<out AppCompatActivity>) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = Prefs.get(context)
        val google = prefs.getString(Prefs.GOOGLE_CLIENT_ID, null)
        val oneDrive = prefs.getString(Prefs.ONEDRIVE_CLIENT_ID, null)
        val googleScope = prefs.getString(com.androsaver.source.SourceIdentity.key("google"), null)
        val oneDriveScope = prefs.getString(com.androsaver.source.SourceIdentity.key("onedrive"), null)
        try {
            // Missing client IDs prevent any real OAuth network request.
            prefs.edit().remove(Prefs.GOOGLE_CLIENT_ID).remove(Prefs.ONEDRIVE_CLIENT_ID).commit()
            ActivityScenario.launch(type).use { scenario ->
                var original: DeviceAuthViewModel? = null
                var state: Any? = null
                scenario.onActivity { activity ->
                    original = ViewModelProvider(activity)[DeviceAuthViewModel::class.java]
                    state = original!!.state
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val current = ViewModelProvider(activity)[DeviceAuthViewModel::class.java]
                    assertSame(original, current)
                    assertSame(state, current.state)
                }
            }
        } finally { prefs.edit().putString(Prefs.GOOGLE_CLIENT_ID, google).putString(Prefs.ONEDRIVE_CLIENT_ID, oneDrive)
            .putString(com.androsaver.source.SourceIdentity.key("google"), googleScope)
            .putString(com.androsaver.source.SourceIdentity.key("onedrive"), oneDriveScope).commit() }
    }
}
