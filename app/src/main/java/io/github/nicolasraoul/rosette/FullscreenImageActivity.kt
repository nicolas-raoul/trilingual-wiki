package io.github.nicolasraoul.rosette

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

/**
 * Full-screen image viewer activity that displays images over all panels.
 * This provides a native Android full-screen image viewing experience
 * that overlays the entire app instead of using Wikipedia's image viewer.
 */
class FullscreenImageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_URL = "extra_image_url"
        private const val TAG = "FullscreenImageActivity"
    }

    private lateinit var imageView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var closeButton: ImageButton
    private val progressHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "FullscreenImageActivity onCreate started")
        
        setContentView(R.layout.activity_fullscreen_image)
        
        // Set up full-screen immersive mode after content view is set
        setupFullscreenMode()
        
        // Set up modern back handling
        setupBackHandler()
        
        imageView = findViewById(R.id.fullscreen_image_view)
        progressBar = findViewById(R.id.loading_progress)
        closeButton = findViewById(R.id.close_button)
        
        // Set up close button
        closeButton.setOnClickListener { finish() }
        
        // Remove tap-to-close functionality on image as requested
        // Only close button and back button should dismiss the image
        
        // Get the image URL from the intent
        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL)
        
        Log.d(TAG, "Received image URL: $imageUrl")
        
        if (imageUrl.isNullOrBlank()) {
            Log.e(TAG, "Invalid image URL received")
            Toast.makeText(this, "Invalid image URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Convert SVG URLs to PNG if needed
        val processedUrl = processSvgUrl(imageUrl)
        Log.d(TAG, "Processed URL: $processedUrl")
        
        // Load the image
        loadImage(processedUrl)
    }
    
    private fun setupFullscreenMode() {
        // Use modern WindowInsetsController for full screen mode (minSdk 34, so API 30+ is guaranteed)
        window.insetsController?.let { controller ->
            controller.hide(android.view.WindowInsets.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        // Set system bar colors using modern approach
        window.setDecorFitsSystemWindows(false)
    }
    
    private fun setupBackHandler() {
        // Use modern OnBackPressedCallback for handling back button (API 33+)
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back button pressed")
                finish()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }
    
    /**
     * Converts SVG URLs to PNG representation URLs using Wikimedia's Special:Redirect service.
     * This approach is more reliable than trying to construct thumbnail URLs manually.
     * Example: 
     * https://commons.wikimedia.org/wiki/File:Flag_of_New_Zealand.svg
     * -> https://commons.wikimedia.org/w/index.php?title=Special:Redirect/file/Flag_of_New_Zealand.svg&width=960
     */
    private fun processSvgUrl(imageUrl: String): String {
        Log.d(TAG, "Processing URL: $imageUrl")
        
        // Check if it's an SVG file
        if (imageUrl.contains(".svg", ignoreCase = true)) {
            Log.d(TAG, "Detected SVG file, converting to PNG using Special:Redirect")
            
            // Handle commons.wikimedia.org/wiki/File: URLs
            if (imageUrl.contains("commons.wikimedia.org/wiki/File:")) {
                val fileName = imageUrl.substringAfterLast("File:")
                Log.d(TAG, "Extracted file name: $fileName")
                
                // Use Wikimedia's Special:Redirect service to get PNG representation
                val redirectUrl = "https://commons.wikimedia.org/w/index.php?title=Special:Redirect/file/$fileName&width=960"
                Log.d(TAG, "Converted SVG to PNG using Special:Redirect: $redirectUrl")
                return redirectUrl
            }
            
            // Handle direct upload.wikimedia.org URLs that are SVG
            if (imageUrl.contains("upload.wikimedia.org") && imageUrl.contains(".svg")) {
                val fileName = if (imageUrl.contains("/thumb/")) {
                    // For thumbnail URLs like:
                    // https://upload.wikimedia.org/wikipedia/commons/thumb/8/85/Tennis_pictogram.svg/40px-Tennis_pictogram.svg.png
                    // We need to extract "Tennis_pictogram.svg" from the path, not "40px-Tennis_pictogram.svg.png"
                    val pathSegments = imageUrl.split("/")
                    val thumbIndex = pathSegments.indexOf("thumb")
                    if (thumbIndex >= 0 && thumbIndex + 3 < pathSegments.size) {
                        // The SVG filename is at position thumbIndex + 3 (after thumb/x/xx/)
                        pathSegments[thumbIndex + 3]
                    } else {
                        // Fallback to original logic if path structure is unexpected
                        imageUrl.substringAfterLast("/")
                    }
                } else {
                    // For direct SVG URLs, use the last segment as before
                    imageUrl.substringAfterLast("/")
                }
                
                Log.d(TAG, "Extracted filename from direct URL: $fileName")
                
                // Use Special:Redirect service
                val redirectUrl = "https://commons.wikimedia.org/w/index.php?title=Special:Redirect/file/$fileName&width=960"
                Log.d(TAG, "Converted direct SVG URL to PNG using Special:Redirect: $redirectUrl")
                return redirectUrl
            }
        }
        
        Log.d(TAG, "No SVG conversion needed")
        return imageUrl
    }
    
    private fun loadImage(imageUrl: String) {
        Log.d(TAG, "Starting image load for: $imageUrl")
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        
        // Register progress listener for this specific URL
        ProgressInterceptor.addListener(imageUrl) { progress ->
            Log.d(TAG, "Real progress update: $progress%")
            progressBar.progress = progress
        }
        
        Glide.with(this)
            .load(imageUrl)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.e(TAG, "Image load failed", e)
                    ProgressInterceptor.removeListener(imageUrl)
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@FullscreenImageActivity, "Failed to load image", Toast.LENGTH_SHORT).show()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d(TAG, "Image loaded successfully")
                    ProgressInterceptor.removeListener(imageUrl)
                    
                    // Complete progress bar if not already at 100%
                    if (progressBar.progress < 100) {
                        progressBar.progress = 100
                    }
                    
                    // Hide progress bar after a brief delay
                    progressHandler.postDelayed({
                        progressBar.visibility = View.GONE
                    }, 200)
                    
                    return false // Let Glide handle the actual display
                }
            })
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(imageView)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FullscreenImageActivity onDestroy")
        
        // Clean up any remaining progress listeners
        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL)
        if (!imageUrl.isNullOrBlank()) {
            val processedUrl = processSvgUrl(imageUrl)
            ProgressInterceptor.removeListener(processedUrl)
        }
    }
}