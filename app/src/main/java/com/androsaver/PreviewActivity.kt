package com.androsaver

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.androsaver.databinding.DreamLayoutBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: DreamLayoutBinding
    private lateinit var engine: ScreensaverEngine
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = DreamLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        engine = ScreensaverEngine(this, binding, scope, onRequestFinish = { finish() })
        engine.start(PreferenceManager.getDefaultSharedPreferences(this))
    }

    override fun onPause() {
        super.onPause()
        engine.pauseVisualizer()  // stop audio + GL when not in foreground
    }

    override fun onResume() {
        super.onResume()
        engine.resumeVisualizer()
    }

    override fun onDestroy() {
        engine.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            finish(); return true
        }
        return engine.handleKeyEvent(event) || super.dispatchKeyEvent(event)
    }
}
