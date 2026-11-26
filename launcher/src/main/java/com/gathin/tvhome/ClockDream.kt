package com.gathin.tvhome

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockDream : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var bgImage: ImageView
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var weatherView: TextView
    private lateinit var contentBox: LinearLayout
    private var cfg = LauncherConfig()
    private var wallpapers: List<File> = emptyList()
    private var wpIndex = 0
    private var tickCount = 0

    private val ticker = object : Runnable {
        override fun run() {
            updateTime()
            tickCount++
            // Dời nhẹ mỗi 30 giây để chống lưu ảnh OLED.
            if (tickCount % 30 == 0) {
                val dx = ((tickCount / 30) % 5 - 2) * 36f
                val dy = ((tickCount / 60) % 3 - 1) * 28f
                contentBox.animate().translationX(dx).translationY(dy).setDuration(1800).start()
            }
            // Đổi ảnh nền mỗi 20 giây nếu có bộ ảnh xoay vòng và bật màn chờ động.
            if (cfg.dreamMotion && wallpapers.size > 1 && tickCount % 20 == 0) {
                wpIndex = (wpIndex + 1) % wallpapers.size
                showWallpaper(wallpapers[wpIndex], animate = true)
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        cfg = ConfigStore.load(this)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        bgImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.48f
        }
        root.addView(bgImage, FrameLayout.LayoutParams(-1, -1))

        val dirList = ConfigStore.wallpapersDir(this).listFiles { f -> f.isFile }?.sortedBy { it.name }.orEmpty()
        wallpapers = if (dirList.isNotEmpty()) dirList else listOfNotNull(ConfigStore.wallpaperFile(this).takeIf { it.isFile })
        if (wallpapers.isNotEmpty()) {
            showWallpaper(wallpapers[0], animate = false)
        }

        clockView = TextView(this).apply {
            setTextColor(cfg.headerColor ?: Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 82f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(16f, 0f, 4f, Color.BLACK)
        }
        dateView = TextView(this).apply {
            setTextColor(cfg.accentColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setShadowLayer(10f, 0f, 2f, Color.BLACK)
        }
        weatherView = TextView(this).apply {
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setPadding(0, 12, 0, 0)
            setShadowLayer(10f, 0f, 2f, Color.BLACK)
        }

        contentBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(clockView)
            addView(dateView)
            addView(weatherView)
        }
        root.addView(contentBox, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(root)
        loadWeather()
        handler.post(ticker)
    }

    private fun showWallpaper(file: File, animate: Boolean) {
        runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()?.let { bmp ->
            bgImage.setImageBitmap(bmp)
            if (animate) {
                bgImage.alpha = 0.15f
                bgImage.scaleX = 1f
                bgImage.scaleY = 1f
                bgImage.animate().alpha(0.48f).scaleX(1.06f).scaleY(1.06f).setDuration(18_000L).start()
            } else if (cfg.dreamMotion) {
                bgImage.scaleX = 1f
                bgImage.scaleY = 1f
                bgImage.animate().scaleX(1.06f).scaleY(1.06f).setDuration(18_000L).start()
            }
        }
    }

    private fun loadWeather() {
        val lat = cfg.weatherLat ?: return
        val lon = cfg.weatherLon ?: return
        scope.launch {
            val info = withContext(Dispatchers.IO) { WeatherClient.fetch(lat, lon) }
            if (info != null) {
                val place = cfg.weatherPlace.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                weatherView.text = "${info.icon} ${info.tempC}°C · ${info.summary}$place"
            }
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        scope.cancel()
        super.onDetachedFromWindow()
    }

    private fun updateTime() {
        val now = Date()
        val fmt = if (cfg.clock24h) "HH:mm" else "h:mm a"
        clockView.text = SimpleDateFormat(fmt, Locale.getDefault()).format(now)
        val vi = Locale.getDefault().language == "vi"
        val solar = SimpleDateFormat(if (vi) "EEEE, dd/MM/yyyy" else "EEEE, d MMMM yyyy", if (vi) Locale("vi", "VN") else Locale.getDefault()).format(now)
        dateView.text = if (cfg.showLunarCalendar && vi) "$solar  ·  ${LunarCalendar.formatToday()}" else solar
    }
}
