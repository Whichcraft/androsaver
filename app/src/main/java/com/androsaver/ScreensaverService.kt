package com.androsaver

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
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
    private var activeView = 1  // 1 = imageView1 showing, 2 = imageView2 showing
    private val handler = Handler(Looper.getMainLooper())
    private var slideshowRunnable: Runnable? = null

    companion object {
        private const val TAG = "AndroSaver"
        private val RANDOM_EFFECTS = listOf(
            "crossfade", "fade_black", "slide_left", "slide_right", "zoom_in", "zoom_out"
        )
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = DreamLayoutBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

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

    // -------------------------------------------------------------------------
    // Image loading
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Slideshow
    // -------------------------------------------------------------------------

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

        val incoming = if (activeView == 1) binding.imageView2 else binding.imageView1
        val outgoing = if (activeView == 1) binding.imageView1 else binding.imageView2
        activeView = if (activeView == 1) 2 else 1

        val glideUrl = if (item.headers.isNotEmpty()) {
            val builder = LazyHeaders.Builder()
            item.headers.forEach { (k, v) -> builder.addHeader(k, v) }
            GlideUrl(item.url, builder.build())
        } else {
            GlideUrl(item.url)
        }

        Glide.with(this)
            .load(glideUrl)
            .into(object : CustomTarget<Drawable>(
                binding.imageView1.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels,
                binding.imageView1.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            ) {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    incoming.setImageDrawable(resource)
                    val effect = PreferenceManager.getDefaultSharedPreferences(this@ScreensaverService)
                        .getString(Prefs.TRANSITION_EFFECT, "crossfade") ?: "crossfade"
                    applyTransition(incoming, outgoing, effect)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    incoming.setImageDrawable(null)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    Log.w(TAG, "Failed to load: ${item.url}")
                    handler.post { showNextImage() }
                }
            })
    }

    // -------------------------------------------------------------------------
    // Transition effects
    // -------------------------------------------------------------------------

    private val transitionMs: Long get() =
        PreferenceManager.getDefaultSharedPreferences(this)
            .getString(Prefs.TRANSITION_SPEED, "1500")?.toLongOrNull() ?: 1500L

    private fun applyTransition(incoming: ImageView, outgoing: ImageView, effect: String) {
        // Bring the incoming view on top so all effects can freely manipulate both views
        incoming.bringToFront()

        // Reset any leftover transform state
        incoming.translationX = 0f
        incoming.translationY = 0f
        incoming.scaleX = 1f
        incoming.scaleY = 1f
        outgoing.translationX = 0f
        outgoing.translationY = 0f
        outgoing.scaleX = 1f
        outgoing.scaleY = 1f

        val resolved = if (effect == "random") RANDOM_EFFECTS.random() else effect

        when (resolved) {
            "crossfade" -> crossfade(incoming, outgoing)
            "fade_black" -> fadeBlack(incoming, outgoing)
            "slide_left" -> slide(incoming, outgoing, fromRight = true)
            "slide_right" -> slide(incoming, outgoing, fromRight = false)
            "zoom_in" -> zoomIn(incoming, outgoing)
            "zoom_out" -> zoomOut(incoming, outgoing)
            else -> crossfade(incoming, outgoing)
        }
    }

    /** Fade out old image while fading in new image simultaneously. */
    private fun crossfade(incoming: ImageView, outgoing: ImageView) {
        incoming.alpha = 0f
        incoming.animate().alpha(1f).setDuration(transitionMs).setListener(null).start()
        outgoing.animate().alpha(0f).setDuration(transitionMs)
            .setListener(resetOnEnd(outgoing)).start()
    }

    /** Old image fades to black, then new image fades in from black. */
    private fun fadeBlack(incoming: ImageView, outgoing: ImageView) {
        val half = transitionMs / 2
        incoming.alpha = 0f
        outgoing.animate().alpha(0f).setDuration(half)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    outgoing.setImageDrawable(null)
                    outgoing.alpha = 0f
                    incoming.animate().alpha(1f).setDuration(half).setListener(null).start()
                }
            }).start()
    }

    /** New image slides in from one side while old slides out the other. */
    private fun slide(incoming: ImageView, outgoing: ImageView, fromRight: Boolean) {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val inStart = if (fromRight) w else -w
        val outEnd = if (fromRight) -w else w

        incoming.alpha = 1f
        incoming.translationX = inStart
        incoming.animate().translationX(0f).setDuration(transitionMs).setListener(null).start()
        outgoing.animate().translationX(outEnd).setDuration(transitionMs)
            .setListener(resetOnEnd(outgoing)).start()
    }

    /** New image scales up from slightly smaller while fading in; old fades out. */
    private fun zoomIn(incoming: ImageView, outgoing: ImageView) {
        incoming.alpha = 0f
        incoming.scaleX = 0.85f
        incoming.scaleY = 0.85f
        incoming.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(transitionMs).setListener(null).start()
        outgoing.animate().alpha(0f).setDuration(transitionMs)
            .setListener(resetOnEnd(outgoing)).start()
    }

    /** Old image scales out and fades while new image fades in. */
    private fun zoomOut(incoming: ImageView, outgoing: ImageView) {
        incoming.alpha = 0f
        incoming.animate().alpha(1f).setDuration(transitionMs).setListener(null).start()
        outgoing.animate().alpha(0f).scaleX(1.15f).scaleY(1.15f).setDuration(transitionMs)
            .setListener(resetOnEnd(outgoing)).start()
    }

    /** Listener that clears and resets an outgoing view when its animation ends. */
    private fun resetOnEnd(view: ImageView) = object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            view.setImageDrawable(null)
            view.alpha = 0f
            view.translationX = 0f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
        }
    }
}
