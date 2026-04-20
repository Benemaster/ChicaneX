package com.chicanex.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import androidx.appcompat.app.AppCompatActivity
import com.chicanex.app.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())
    private val navigateRunnable = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        animateSplash()
        handler.postDelayed(navigateRunnable, SPLASH_DURATION)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(navigateRunnable)
    }

    private fun animateSplash() {
        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 800
            fillAfter = true
        }
        val scaleUp = ScaleAnimation(
            0.8f, 1f, 0.8f, 1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 800
            fillAfter = true
        }
        val animSet = AnimationSet(true).apply {
            addAnimation(fadeIn)
            addAnimation(scaleUp)
        }
        binding.logoContainer.startAnimation(animSet)
    }

    companion object {
        private const val SPLASH_DURATION = 2500L
    }
}
