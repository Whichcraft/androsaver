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
    private var scope: CoroutineScope? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = DreamLayoutBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        val prefs = com.androsaver.Prefs.get(this)
        isInteractive = prefs.getString(Prefs.SCREENSAVER_MODE, Prefs.MODE_SLIDESHOW) == Prefs.MODE_VISUALIZER

        val s = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = s
        engine = ScreensaverEngine(this, binding, s, onRequestFinish = { finish() })
        engine.start(prefs)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        engine.resumeVisualizer()
    }

    override fun onDreamingStopped() {
        engine.pauseVisualizer()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        engine.stop()
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = engine.handleKeyEvent(event)
}
