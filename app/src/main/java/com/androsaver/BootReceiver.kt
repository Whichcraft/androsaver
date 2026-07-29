package com.androsaver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // WorkManager owns the network constraints, retries, and process
        // lifetime. Do not keep a boot broadcast open while three synchronous
        // token refresh requests run; BroadcastReceiver work has a short
        // execution window and can be killed before pending.finish().
        PrefetchScheduler.schedule(context)
    }
}
