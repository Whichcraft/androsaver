package com.androsaver.auth

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

class DeviceAuthViewModel(application: Application) : AndroidViewModel(application) {
    private var session: DeviceAuthorization? = null
    internal val state get() = requireNotNull(session).state
    internal fun start(google: Boolean) {
        if (session != null) return
        val app = getApplication<Application>()
        val flow = if (google) {
            val manager = GoogleAuthManager(app)
            DeviceAuthorization(viewModelScope, manager::requestDeviceCode, manager::pollForToken, SystemClock::elapsedRealtime)
        } else {
            val manager = OneDriveAuthManager(app)
            DeviceAuthorization(viewModelScope, manager::requestDeviceCode, manager::pollForToken, SystemClock::elapsedRealtime)
        }
        session = flow
        flow.start()
    }
}
