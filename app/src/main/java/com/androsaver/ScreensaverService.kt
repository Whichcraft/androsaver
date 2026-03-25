package com.androsaver

import android.service.dreams.DreamService
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.preference.PreferenceManager
import com.androsaver.databinding.DreamLayoutBinding
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ScreensaverService : DreamService() {

    private lateinit var binding: DreamLayoutBinding
    private lateinit var engine: ScreensaverEngine
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = DreamLayoutBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        isInteractive = prefs.getString(Prefs.SCREENSAVER_MODE, Prefs.MODE_SLIDESHOW) == Prefs.MODE_VISUALIZER

        engine = ScreensaverEngine(this, binding, scope, onRequestFinish = { finish() })
        engine.start(prefs)
    }

    override fun onDetachedFromWindow() {
        engine.stop()
        scope.cancel()
        Glide.with(applicationContext).onStop()
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = engine.handleKeyEvent(event)
}
