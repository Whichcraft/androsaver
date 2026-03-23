package com.androsaver

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.preference.PreferenceManager
import com.androsaver.databinding.DreamLayoutBinding
import com.androsaver.source.GoogleDriveSource
import com.androsaver.source.ImageItem
import com.androsaver.source.ImageSource
import com.androsaver.source.SynologySource
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ScreensaverService : DreamService() {

    private lateinit var binding: DreamLayoutBinding
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val imageItems = mutableListOf<ImageItem>()
    private var currentIndex = 0
    private var activeView = 1  // 1 = imageView1 on top is showing, 2 = imageView2 is showing
    private val handler = Handler(Looper.getMainLooper())
    private var slideshowRunnable: Runnable? = null

    companion object {
        private const val TAG = "AndroSaver"
        private const val FADE_DURATION_MS = 1500L
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = DreamLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // imageView1 is on top (declared last in XML), starts at alpha=1 (no image = black)
        // imageView2 is below, starts at alpha=0
        binding.imageView1.alpha = 1f
        binding.imageView2.alpha = 0f

        loadImages()
    }

    override fun onDetachedFromWindow() {
        stopSlideshow()
        scope.cancel()
        Glide.with(applicationContext).onStop()
        super.onDetachedFromWindow()
    }

    private fun loadImages() {
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = getString(R.string.loading_images)

        val sources = getConfiguredSources()
        if (sources.isEmpty()) {
            binding.statusText.text = getString(R.string.no_sources_configured)
            return
        }

        scope.launch {
            val items = mutableListOf<ImageItem>()
            for (source in sources) {
                try {
                    items.addAll(source.getImageUrls())
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching from ${source.name}", e)
                }
            }
            if (items.isEmpty()) {
                binding.statusText.text = getString(R.string.no_images_found)
                return@launch
            }
            binding.statusText.visibility = View.GONE
            imageItems.clear()
            imageItems.addAll(items.shuffled())
            startSlideshow()
        }
    }

    private fun getConfiguredSources(): List<ImageSource> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return buildList {
            if (prefs.getBoolean(Prefs.ENABLE_GOOGLE_DRIVE, false)) add(GoogleDriveSource(this@ScreensaverService))
            if (prefs.getBoolean(Prefs.ENABLE_SYNOLOGY, false)) add(SynologySource(this@ScreensaverService))
        }
    }

    private fun startSlideshow() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val durationMs = prefs.getString(Prefs.SLIDE_DURATION, "10000")?.toLongOrNull() ?: 10_000L

        showNextImage()
        slideshowRunnable = object : Runnable {
            override fun run() {
                showNextImage()
                handler.postDelayed(this, durationMs)
            }
        }
        handler.postDelayed(slideshowRunnable!!, durationMs)
    }

    private fun stopSlideshow() {
        slideshowRunnable?.let { handler.removeCallbacks(it) }
        slideshowRunnable = null
    }

    private fun showNextImage() {
        if (imageItems.isEmpty()) return

        val item = imageItems[currentIndex]
        currentIndex = (currentIndex + 1) % imageItems.size

        // Determine which view receives the new image (the one not currently showing)
        val incomingView = if (activeView == 1) binding.imageView2 else binding.imageView1
        val outgoingView = if (activeView == 1) binding.imageView1 else binding.imageView2
        val nextActive = if (activeView == 1) 2 else 1
        activeView = nextActive

        val glideUrl = if (item.headers.isNotEmpty()) {
            val builder = LazyHeaders.Builder()
            item.headers.forEach { (k, v) -> builder.addHeader(k, v) }
            GlideUrl(item.url, builder.build())
        } else {
            GlideUrl(item.url)
        }

        Glide.with(this)
            .load(glideUrl)
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    incomingView.setImageDrawable(resource)
                    incomingView.alpha = 0f

                    incomingView.animate()
                        .alpha(1f)
                        .setDuration(FADE_DURATION_MS)
                        .start()

                    outgoingView.animate()
                        .alpha(0f)
                        .setDuration(FADE_DURATION_MS)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                outgoingView.setImageDrawable(null)
                            }
                        })
                        .start()
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    incomingView.setImageDrawable(null)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    Log.w(TAG, "Failed to load: ${item.url}")
                    // Skip to next image
                    showNextImage()
                }
            })
    }
}
