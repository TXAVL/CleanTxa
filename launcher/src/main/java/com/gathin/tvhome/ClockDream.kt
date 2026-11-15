package com.gathin.tvhome

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockDream : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var contentBox: LinearLayout
    private var tickCount = 0

    private val ticker = object : Runnable {
        override fun run() {
            updateTime()
            tickCount++
            // Gently drift position every 30s to prevent OLED burn-in
            if (tickCount % 30 == 0) {
                val dx = ((tickCount / 30) % 5 - 2) * 36f
                val dy = ((tickCount / 60) % 3 - 1) * 28f
                contentBox.animate().translationX(dx).translationY(dy).setDuration(1500).start()
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        val cfg = ConfigStore.load(this)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        val wp = ConfigStore.wallpaperFile(this)
        if (wp.isFile) {
            runCatching { BitmapFactory.decodeFile(wp.absolutePath) }.getOrNull()?.let { bmp ->
                val iv = ImageView(this).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    alpha = 0.45f
                }
                root.addView(iv, FrameLayout.LayoutParams(-1, -1))
            }
        }

        clockView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 76f)
            setShadowLayer(12f, 0f, 4f, Color.BLACK)
        }
        dateView = TextView(this).apply {
            setTextColor(Color.parseColor("#B6F2EA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }

        contentBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(clockView)
            addView(dateView)
        }
        root.addView(contentBox, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(root)
        handler.post(ticker)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    private fun updateTime() {
        val now = Date()
        clockView.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val solar = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi", "VN")).format(now)
        val cfg = ConfigStore.load(this)
        dateView.text = if (cfg.showLunarCalendar) "$solar  ·  ${LunarCalendar.formatToday()}" else solar
    }
}

