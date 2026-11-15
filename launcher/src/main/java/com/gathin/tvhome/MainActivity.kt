package com.gathin.tvhome

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var cfg = LauncherConfig()
    private var wallpaperIndex = 0

    private lateinit var rootFrame: FrameLayout
    private lateinit var wallpaperView: ImageView
    private lateinit var scrimView: View
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var greetingText: TextView
    private lateinit var weatherText: TextView
    private lateinit var nowPlayingBtn: TextView
    private lateinit var ipPill: TextView
    private lateinit var channelsRow: LinearLayout
    private lateinit var channelsScroll: HorizontalScrollView
    private lateinit var appsGrid: GridLayout

    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ConfigStore.ACTION_RELOAD_CONFIG) {
                reloadAll()
            }
        }
    }

    private val clockTicker = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, if (cfg.showClockSeconds) 1_000L else 15_000L)
        }
    }

    private val wallpaperRotator = object : Runnable {
        override fun run() {
            val mins = cfg.wallpaperRotationMinutes
            if (mins > 0) {
                wallpaperIndex++
                applyWallpaper()
                handler.postDelayed(this, mins * 60_000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildLayout()
        setContentView(rootFrame)

        val filter = IntentFilter(ConfigStore.ACTION_RELOAD_CONFIG)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(reloadReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(reloadReceiver, filter)
        }
        reloadAll()
    }

    override fun onResume() {
        super.onResume()
        reloadAll()
        handler.removeCallbacks(clockTicker)
        handler.post(clockTicker)
    }

    override fun onPause() {
        handler.removeCallbacks(clockTicker)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockTicker)
        handler.removeCallbacks(wallpaperRotator)
        runCatching { unregisterReceiver(reloadReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Stay on Home screen
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun buildLayout() {
        rootFrame = FrameLayout(this)
        wallpaperView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        rootFrame.addView(wallpaperView, FrameLayout.LayoutParams(-1, -1))

        scrimView = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(170, 8, 13, 20), Color.argb(120, 8, 13, 20), Color.argb(210, 8, 13, 20))
            )
        }
        rootFrame.addView(scrimView, FrameLayout.LayoutParams(-1, -1))

        val mainScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(40), dp(28), dp(40), dp(36))
        }

        // Top action bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val leftHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        greetingText = TextView(this).apply {
            setTextColor(Color.parseColor("#2EC4B6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        clockText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        }
        dateText = TextView(this).apply {
            setTextColor(Color.parseColor("#B4C0CC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        weatherText = TextView(this).apply {
            setTextColor(Color.parseColor("#B6F2EA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(4), 0, 0)
            visibility = View.GONE
        }
        leftHeader.addView(greetingText)
        leftHeader.addView(clockText)
        leftHeader.addView(dateText)
        leftHeader.addView(weatherText)
        topBar.addView(leftHeader, LinearLayout.LayoutParams(0, -2, 1f))

        val pillsBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        ipPill = makePill("IP") {
            Toast.makeText(this, "IP TV: ${localIpv4() ?: "Chưa có mạng"}", Toast.LENGTH_SHORT).show()
        }.apply { visibility = View.GONE }
        pillsBar.addView(ipPill)
        nowPlayingBtn = makePill("♪ Đang phát") {
            val paused = NowPlayingListener.pauseAll(this)
            val msg = if (paused > 0) "Đã tạm dừng $paused ứng dụng phát tiếng" else "Không có ứng dụng nào đang phát"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        pillsBar.addView(nowPlayingBtn)
        pillsBar.addView(makePill("Wi-Fi") {
            runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        })
        pillsBar.addView(makePill("Cài đặt") {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        })
        topBar.addView(pillsBar, LinearLayout.LayoutParams(-2, -2))
        content.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        // Channel tiles row
        channelsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(20), 0, dp(8))
        }
        channelsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        channelsScroll.addView(channelsRow)
        content.addView(channelsScroll, LinearLayout.LayoutParams(-1, -2))

        // Apps section header
        val appsLabel = TextView(this).apply {
            text = "Ứng dụng"
            setTextColor(Color.parseColor("#9FB0C0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(4), dp(18), 0, dp(10))
        }
        content.addView(appsLabel)

        appsGrid = GridLayout(this).apply {
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        content.addView(appsGrid, LinearLayout.LayoutParams(-1, -2))

        mainScroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        rootFrame.addView(mainScroll, FrameLayout.LayoutParams(-1, -1))
    }

    private fun makePill(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(16), dp(9), dp(16), dp(9))
            isFocusable = true
            isFocusableInTouchMode = true
            background = focusableCardDrawable(Color.parseColor("#24FFFFFF"), dp(20))
            val lp = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) }
            layoutParams = lp
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.07f else 1f).scaleY(if (hasFocus) 1.07f else 1f).setDuration(120).start()
            }
        }
    }

    private fun focusableCardDrawable(normalColor: Int, radiusPx: Int): StateListDrawable {
        val focused = GradientDrawable().apply {
            setColor(normalColor)
            cornerRadius = radiusPx.toFloat()
            setStroke(dp(2), Color.parseColor("#2EC4B6"))
        }
        val normal = GradientDrawable().apply {
            setColor(normalColor)
            cornerRadius = radiusPx.toFloat()
            setStroke(dp(1), Color.parseColor("#22FFFFFF"))
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(android.R.attr.state_selected), focused)
            addState(intArrayOf(), normal)
        }
    }

    private fun reloadAll() {
        cfg = ConfigStore.load(this)
        applyBackground()
        applyWallpaper()
        updateClock()
        updateGreeting()
        updateWeather()
        nowPlayingBtn.visibility = if (cfg.showNowPlaying) View.VISIBLE else View.GONE
        if (cfg.showNetworkIp) {
            ipPill.text = localIpv4() ?: "Offline"
            ipPill.visibility = View.VISIBLE
        } else {
            ipPill.visibility = View.GONE
        }
        scrimView.alpha = (cfg.wallpaperDim.coerceIn(0, 80) / 80f).coerceIn(0.15f, 1f)
        handler.removeCallbacks(clockTicker)
        handler.post(clockTicker)
        rebuildChannels()
        rebuildApps()

        handler.removeCallbacks(wallpaperRotator)
        if (cfg.wallpaperRotationMinutes > 0) {
            handler.postDelayed(wallpaperRotator, cfg.wallpaperRotationMinutes * 60_000L)
        }
    }

    private fun applyBackground() {
        val base = cfg.backgroundColor
        val accent = if (cfg.skyIntensity > 0) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            when (hour) {
                in 5..8 -> Color.parseColor("#1B3B48")
                in 9..16 -> Color.parseColor("#0E3B37")
                in 17..19 -> Color.parseColor("#2D2540")
                else -> Color.parseColor("#0B1118")
            }
        } else base
        rootFrame.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(base, accent)
        )
    }

    private fun applyWallpaper() {
        if (!cfg.wallpaperEnabled) {
            wallpaperView.visibility = View.GONE
            return
        }
        val dirFiles = ConfigStore.wallpapersDir(this).listFiles { f -> f.isFile }?.sortedBy { it.name }.orEmpty()
        val file: File? = if (dirFiles.isNotEmpty()) {
            dirFiles[wallpaperIndex % dirFiles.size]
        } else {
            ConfigStore.wallpaperFile(this).takeIf { it.isFile }
        }
        if (file == null) {
            wallpaperView.visibility = View.GONE
            return
        }
        val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        if (bmp != null) {
            wallpaperView.setImageBitmap(bmp)
            wallpaperView.scaleType = if (cfg.wallpaperFitFrame) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            wallpaperView.visibility = View.VISIBLE
        } else {
            wallpaperView.visibility = View.GONE
        }
    }

    private fun updateClock() {
        val now = Date()
        val fmt = if (cfg.showClockSeconds) "HH:mm:ss" else "HH:mm"
        clockText.text = SimpleDateFormat(fmt, Locale.getDefault()).format(now)
        val solar = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi", "VN")).format(now)
        dateText.text = if (cfg.showLunarCalendar) "$solar  ·  ${LunarCalendar.formatToday()}" else solar
        if (cfg.headerWhite) {
            clockText.setTextColor(Color.WHITE)
        }
    }

    private fun updateGreeting() {
        if (!cfg.showGreeting) {
            greetingText.visibility = View.GONE
            return
        }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val base = when (hour) {
            in 5..11 -> "Chào buổi sáng"
            in 12..17 -> "Chào buổi chiều"
            else -> "Chào buổi tối"
        }
        greetingText.text = if (cfg.greetingName.isNotBlank()) "$base, ${cfg.greetingName}" else "$base · CleanTxa Home"
        greetingText.visibility = View.VISIBLE
    }

    private fun updateWeather() {
        val lat = cfg.weatherLat
        val lon = cfg.weatherLon
        if (lat == null || lon == null) {
            weatherText.visibility = View.GONE
            return
        }
        scope.launch {
            val info = withContext(Dispatchers.IO) { WeatherClient.fetch(lat, lon) }
            if (info != null) {
                val place = cfg.weatherPlace.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                weatherText.text = "${info.tempC}°C · ${info.summary}$place"
                weatherText.visibility = View.VISIBLE
            }
        }
    }

    private fun rebuildChannels() {
        channelsRow.removeAllViews()
        if (cfg.linkTiles.isEmpty()) {
            channelsScroll.visibility = View.GONE
            return
        }
        channelsScroll.visibility = View.VISIBLE
        val tilesDir = ConfigStore.linkTilesDir(this)
        cfg.linkTiles.forEach { tile ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isFocusableInTouchMode = true
                background = focusableCardDrawable(tile.color, dp(14))
                setPadding(dp(8), dp(8), dp(8), dp(8))
                val lp = LinearLayout.LayoutParams(dp(140), dp(86)).apply { marginEnd = dp(12) }
                layoutParams = lp
                setOnClickListener { openLinkTile(tile) }
                setOnFocusChangeListener { v, hasFocus ->
                    v.animate().scaleX(if (hasFocus) 1.07f else 1f).scaleY(if (hasFocus) 1.07f else 1f).setDuration(120).start()
                }
            }
            val imgFile = tile.imageFile?.let { File(tilesDir, it) }?.takeIf { it.isFile }
            val bmp = imgFile?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
            if (bmp != null) {
                val iv = ImageView(this).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                card.addView(iv, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            val tv = TextView(this).apply {
                text = tile.label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                maxLines = 1
            }
            card.addView(tv, LinearLayout.LayoutParams(-1, -2))
            channelsRow.addView(card)
        }
    }

    private fun openLinkTile(tile: LinkTile) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tile.uri)).apply {
            if (tile.pkg.isNotBlank()) setPackage(tile.pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "Không mở được ${tile.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rebuildApps() {
        appsGrid.removeAllViews()
        appsGrid.columnCount = cfg.columns

        val pm = packageManager
        val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolved = LinkedHashMap<String, AppEntry>()
        fun collect(list: List<ResolveInfo>) {
            for (ri in list) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == packageName || pkg in cfg.hiddenApps) continue
                if (pkg !in resolved) {
                    val label = ri.loadLabel(pm).toString()
                    val icon = ri.loadIcon(pm)
                    resolved[pkg] = AppEntry(pkg, label, icon)
                }
            }
        }
        collect(pm.queryIntentActivities(leanbackIntent, PackageManager.MATCH_ALL))
        val mobileApps = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo?.packageName in cfg.libraryApps }
        collect(mobileApps)

        val ordered = mutableListOf<AppEntry>()
        cfg.dockApps.forEach { pkg -> resolved.remove(pkg)?.let { ordered += it } }
        ordered.addAll(resolved.values.sortedBy { it.label.lowercase(Locale.getDefault()) })

        val cardBg = if (cfg.dockGlass) Color.parseColor("#26FFFFFF") else Color.parseColor("#182430")
        val screenW = resources.displayMetrics.widthPixels - dp(80)
        val tileW = (screenW / cfg.columns) - dp(12)
        val (tileH, iconDp) = when (cfg.tileScale) {
            "small" -> dp(94) to dp(40)
            "large" -> dp(132) to dp(60)
            else -> dp(112) to dp(48)
        }

        ordered.forEach { app ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isFocusableInTouchMode = true
                background = focusableCardDrawable(cardBg, dp(16))
                setPadding(dp(12), dp(14), dp(12), dp(12))
                val lp = GridLayout.LayoutParams().apply {
                    width = tileW.coerceAtLeast(dp(120))
                    height = tileH
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
                layoutParams = lp
                setOnClickListener { launchApp(app.pkg) }
                setOnLongClickListener {
                    showAppMenu(app)
                    true
                }
                setOnFocusChangeListener { v, hasFocus ->
                    v.animate().scaleX(if (hasFocus) 1.06f else 1f).scaleY(if (hasFocus) 1.06f else 1f).setDuration(120).start()
                }
            }

            val iconView = ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            card.addView(iconView, LinearLayout.LayoutParams(iconDp, iconDp))

            val titleView = TextView(this).apply {
                text = app.label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(0, dp(8), 0, 0)
            }
            card.addView(titleView, LinearLayout.LayoutParams(-1, -2))
            appsGrid.addView(card)
        }
    }

    private fun localIpv4(): String? = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.flatMap { it.inetAddresses.toList() }
            ?.firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
            ?.hostAddress
    }.getOrNull()

    private fun launchApp(pkg: String) {
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) {
            runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private fun showAppMenu(app: AppEntry) {
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf("Ẩn khỏi màn hình chính", "Thông tin ứng dụng")) { _, which ->
                when (which) {
                    0 -> {
                        ConfigStore.updateRaw(this) { j ->
                            val arr = j.optJSONArray("hidden_apps") ?: JSONArray()
                            arr.put(app.pkg)
                            j.put("hidden_apps", arr)
                        }
                        reloadAll()
                    }
                    1 -> {
                        runCatching {
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            }
            .show()
    }

    private data class AppEntry(val pkg: String, val label: String, val icon: Drawable)
}

