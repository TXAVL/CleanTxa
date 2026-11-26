package com.gathin.tvhome

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.AudioManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var cfg = LauncherConfig()
    private var wallpaperIndex = 0
    private var sleepTimerUntilMs = 0L

    private lateinit var rootFrame: FrameLayout
    private lateinit var wallpaperView: ImageView
    private lateinit var scrimView: View
    private lateinit var ambientGlowView: View
    private lateinit var mainScroll: ScrollView
    private lateinit var headerBox: LinearLayout
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var greetingText: TextView
    private lateinit var weatherText: TextView
    private lateinit var statsPill: TextView
    private lateinit var ipPill: TextView
    private lateinit var nowPlayingBtn: TextView
    private lateinit var sleepTimerPill: TextView
    private lateinit var searchPill: TextView
    private lateinit var recentsPill: TextView
    private lateinit var sleepPill: TextView
    private lateinit var kidsPill: TextView
    private lateinit var wifiPill: TextView
    private lateinit var settingsPill: TextView
    private lateinit var channelsRow: LinearLayout
    private lateinit var channelsScroll: HorizontalScrollView
    private lateinit var dockSection: LinearLayout
    private lateinit var dockTitleText: TextView
    private lateinit var dockContainer: FrameLayout
    private lateinit var dockRow: LinearLayout
    private lateinit var appsLabel: TextView
    private lateinit var appsGrid: GridLayout
    private lateinit var quickSection: LinearLayout
    private lateinit var quickRow: LinearLayout
    private lateinit var inputsSection: LinearLayout
    private lateinit var inputsRow: LinearLayout
    private lateinit var recentSection: LinearLayout
    private lateinit var recentRow: LinearLayout

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
            updateSleepTimerPill()
            if (cfg.showStats && !cfg.kidsMode) updateStatsPill()
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
        sleepTimerUntilMs = recentPrefs.getLong("sleep_until", 0L)
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
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent != null) handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == ConfigStore.ACTION_OPEN_LIBRARY) {
            handler.post {
                mainScroll.smoothScrollTo(0, appsLabel.top)
                if (appsGrid.childCount > 0) appsGrid.getChildAt(0).requestFocus()
            }
        }
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH && !cfg.kidsMode) {
            showSearchDialog()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Luôn giữ trên màn hình chính TV
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun tileCornerRadiusPx(): Int = when (cfg.tileCornerStyle) {
        "pill" -> dp(26)
        "sharp" -> dp(8)
        else -> dp(18)
    }

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
                intArrayOf(Color.argb(165, 6, 11, 18), Color.argb(115, 6, 11, 18), Color.argb(215, 6, 11, 18))
            )
        }
        rootFrame.addView(scrimView, FrameLayout.LayoutParams(-1, -1))

        ambientGlowView = View(this).apply {
            alpha = 0.28f
        }
        rootFrame.addView(ambientGlowView, FrameLayout.LayoutParams(-1, dp(340), Gravity.TOP))

        mainScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(52), dp(30), dp(52), dp(42))
        }

        // Thanh đầu: Cụm đồng hồ bên trái + Dàn nút kính mờ bên phải
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        headerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        greetingText = TextView(this).apply {
            setTextColor(Color.parseColor("#2EC4B6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            letterSpacing = 0.03f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        clockText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
            letterSpacing = -0.02f
            includeFontPadding = false
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(14f, 0f, 2f, Color.argb(130, 0, 0, 0))
        }
        dateText = TextView(this).apply {
            setTextColor(Color.parseColor("#D2DBE4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(2), 0, 0)
            setShadowLayer(8f, 0f, 1f, Color.argb(110, 0, 0, 0))
        }
        weatherText = TextView(this).apply {
            setTextColor(Color.parseColor("#E6FFFA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            visibility = View.GONE
        }
        val weatherWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
            addView(weatherText, LinearLayout.LayoutParams(-2, -2))
        }
        headerBox.addView(greetingText)
        headerBox.addView(clockText)
        headerBox.addView(dateText)
        headerBox.addView(weatherWrap)

        val leftWrap = FrameLayout(this).apply {
            addView(headerBox, FrameLayout.LayoutParams(-2, -2))
        }
        topBar.addView(leftWrap, LinearLayout.LayoutParams(0, -2, 1f))

        val pillsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val pillsBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(4), dp(4))
        }

        statsPill = makePill("RAM") { freeRam() }.apply { visibility = View.GONE }
        pillsBar.addView(statsPill)

        ipPill = makePill("IP") {
            toast("IP TV: ${localIpv4() ?: tr("Chưa có mạng", "Offline")}")
        }.apply { visibility = View.GONE }
        pillsBar.addView(ipPill)

        nowPlayingBtn = makePill(tr("♪ Đang phát", "♪ Playing")) {
            val paused = NowPlayingListener.pauseAll(this)
            val msg = if (paused > 0) tr("Đã tạm dừng $paused ứng dụng phát tiếng", "Paused $paused playing app(s)")
            else tr("Không có ứng dụng nào đang phát", "Nothing is playing right now")
            toast(msg)
        }
        pillsBar.addView(nowPlayingBtn)

        sleepTimerPill = makePill(tr("⏱ Hẹn tắt", "⏱ Timer")) { cycleSleepTimer() }.apply { visibility = View.GONE }
        pillsBar.addView(sleepTimerPill)

        searchPill = makePill(tr("🔍 Tìm", "🔍 Search")) { showSearchDialog() }
        pillsBar.addView(searchPill)

        recentsPill = makePill(tr("Đa nhiệm", "Recent")) {
            if (!HomeKeyService.recentsNow()) {
                toast(tr("Nút Đa nhiệm cần bộ bắt phím của CleanTxa: mở CleanTxa trên điện thoại, bấm Áp dụng lên tivi lại", "Recent apps needs CleanTxa's key helper: open CleanTxa on your phone and press Apply again"))
            }
        }
        pillsBar.addView(recentsPill)

        sleepPill = makePill(tr("Ngủ", "Sleep")) {
            if (!HomeKeyService.sleepNow()) {
                toast(tr("Nút Ngủ cần bộ bắt phím của CleanTxa (Android 9+): mở CleanTxa trên điện thoại, bấm Áp dụng lên tivi lại", "Sleep needs CleanTxa's key helper (Android 9+): open CleanTxa on your phone and press Apply again"))
            }
        }
        pillsBar.addView(sleepPill)

        kidsPill = makePill(tr("Trẻ em", "Kids")) { onKidsPillClick() }.apply {
            setOnLongClickListener { onKidsPillLongClick(); true }
        }
        pillsBar.addView(kidsPill)

        wifiPill = makePill("Wi-Fi") {
            runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        pillsBar.addView(wifiPill)

        settingsPill = makePill(tr("Cài đặt", "Settings")) {
            openSystemSettings()
        }.apply {
            setOnLongClickListener { showQuickSettingsMenu(); true }
        }
        pillsBar.addView(settingsPill)

        pillsScroll.addView(pillsBar)
        topBar.addView(pillsScroll, LinearLayout.LayoutParams(-2, -2))
        content.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        // Hàng kênh truyền hình trực tiếp
        channelsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(18), 0, dp(4))
        }
        channelsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        channelsScroll.addView(channelsRow)
        content.addView(channelsScroll, LinearLayout.LayoutParams(-1, -2))

        // Khay Dock nổi (Floating Glass Dock) + Spotlight tên ứng dụng đang chọn
        dockSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(18), 0, dp(4))
        }
        val dockHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        val dockBadge = TextView(this).apply {
            text = tr("YÊU THÍCH", "FAVOURITES")
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        dockTitleText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(14), 0, 0, 0)
            maxLines = 1
        }
        dockHeaderRow.addView(dockBadge)
        dockHeaderRow.addView(dockTitleText)
        dockSection.addView(dockHeaderRow)

        dockContainer = FrameLayout(this)
        val dockScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        dockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        dockScroll.addView(dockRow)
        dockContainer.addView(dockScroll, FrameLayout.LayoutParams(-2, -2))
        dockSection.addView(dockContainer, LinearLayout.LayoutParams(-2, -2))
        content.addView(dockSection, LinearLayout.LayoutParams(-1, -2))

        // Tiện ích nhanh, Nguồn vào và Mở gần đây
        buildSection(tr("Tiện ích nhanh", "Quick actions")).let { (s, r) -> quickSection = s; quickRow = r }
        buildSection(tr("Nguồn vào", "Inputs")).let { (s, r) -> inputsSection = s; inputsRow = r }
        buildSection(tr("Mở gần đây", "Recent")).let { (s, r) -> recentSection = s; recentRow = r }
        content.addView(quickSection, LinearLayout.LayoutParams(-1, -2))
        content.addView(inputsSection, LinearLayout.LayoutParams(-1, -2))
        content.addView(recentSection, LinearLayout.LayoutParams(-1, -2))

        // Lưới tất cả ứng dụng
        appsLabel = TextView(this).apply {
            text = tr("Tất cả ứng dụng", "All apps")
            kieuNhanMuc(this)
        }
        content.addView(appsLabel)

        appsGrid = GridLayout(this).apply {
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        content.addView(appsGrid, LinearLayout.LayoutParams(-1, -2))

        mainScroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        rootFrame.addView(mainScroll, FrameLayout.LayoutParams(-1, -1))
    }

    private fun kieuNhanMuc(tv: TextView) {
        tv.setTextColor(Color.parseColor("#8DA0B3"))
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        tv.isAllCaps = true
        tv.letterSpacing = 0.12f
        tv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        tv.setPadding(dp(4), dp(22), 0, dp(10))
    }

    private fun isLightColor(color: Int): Boolean {
        val r = Color.red(color) * 0.299
        val g = Color.green(color) * 0.587
        val b = Color.blue(color) * 0.114
        return (r + g + b) > 165
    }

    private fun makePill(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            isFocusable = true
            isFocusableInTouchMode = true
            background = pillDrawable()
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) }
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, hasFocus ->
                val focusColor = cfg.accentColor
                (v as TextView).setTextColor(if (hasFocus) (if (isLightColor(focusColor)) Color.parseColor("#081018") else Color.WHITE) else Color.WHITE)
                v.animate().scaleX(if (hasFocus) 1.06f else 1f).scaleY(if (hasFocus) 1.06f else 1f).setDuration(120).start()
            }
        }
    }

    private fun pillDrawable(): StateListDrawable {
        val radius = dp(28).toFloat()
        val accent = cfg.accentColor
        val chon = GradientDrawable().apply {
            setColor(accent)
            cornerRadius = radius
            setStroke(dp(1), Color.WHITE)
        }
        val thuong = GradientDrawable().apply {
            setColor(Color.parseColor("#24FFFFFF"))
            cornerRadius = radius
            setStroke(dp(1), Color.parseColor("#30FFFFFF"))
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), chon)
            addState(intArrayOf(android.R.attr.state_selected), chon)
            addState(intArrayOf(), thuong)
        }
    }

    private fun refreshPillsStyle() {
        listOf(statsPill, ipPill, nowPlayingBtn, sleepTimerPill, searchPill, recentsPill, sleepPill, kidsPill, wifiPill, settingsPill).forEach {
            it.background = pillDrawable()
        }
        quickRow.let { row ->
            for (i in 0 until row.childCount) (row.getChildAt(i) as? TextView)?.background = pillDrawable()
        }
    }

    private fun focusableCardDrawable(normalColor: Int, radiusPx: Int): StateListDrawable {
        val strokeColor = if (cfg.appGlow) cfg.accentColor else Color.WHITE
        val focusedFill = if (cfg.appGlow) blendColor(normalColor, cfg.accentColor, 0.22f) else normalColor
        val focused = GradientDrawable().apply {
            setColor(focusedFill)
            cornerRadius = radiusPx.toFloat()
            setStroke(dp(3), strokeColor)
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

    private fun blendColor(base: Int, tint: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val a = Color.alpha(base).coerceAtLeast(180)
        val r = (Color.red(base) * inv + Color.red(tint) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(base) * inv + Color.green(tint) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(base) * inv + Color.blue(tint) * ratio).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    private fun reloadAll() {
        cfg = ConfigStore.load(this)
        applyBackground()
        applyWallpaper()
        applyHeaderStyle()
        refreshPillsStyle()
        updateClock()
        updateGreeting()
        updateWeather()

        val kids = cfg.kidsMode
        nowPlayingBtn.visibility = if (cfg.showNowPlaying && !kids) View.VISIBLE else View.GONE
        sleepTimerPill.visibility = if (cfg.showSleepTimerButton && !kids) View.VISIBLE else View.GONE
        updateSleepTimerPill()
        searchPill.visibility = if (kids) View.GONE else View.VISIBLE
        recentsPill.visibility = if (cfg.showRecentsButton && !kids) View.VISIBLE else View.GONE
        sleepPill.visibility = if (cfg.showSleepButton && !kids) View.VISIBLE else View.GONE
        wifiPill.visibility = if (kids || !cfg.showWifiButton) View.GONE else View.VISIBLE
        settingsPill.visibility = if (kids || !cfg.showSettingsButton) View.GONE else View.VISIBLE
        kidsPill.visibility = if (cfg.showKidsButton || kids) View.VISIBLE else View.GONE
        kidsPill.text = if (kids) tr("Thoát chế độ trẻ em", "Exit kids mode") else tr("Trẻ em", "Kids")
        appsLabel.text = if (kids) tr("Ứng dụng cho bé", "Apps for kids") else tr("Tất cả ứng dụng", "All apps")

        if (cfg.showStats && !kids) {
            updateStatsPill()
            statsPill.visibility = View.VISIBLE
        } else {
            statsPill.visibility = View.GONE
        }

        if (cfg.showNetworkIp && !kids) {
            ipPill.text = localIpv4() ?: "Offline"
            ipPill.visibility = View.VISIBLE
        } else {
            ipPill.visibility = View.GONE
        }

        scrimView.alpha = (cfg.wallpaperDim.coerceIn(0, 80) / 80f).coerceIn(0.12f, 1f)
        handler.removeCallbacks(clockTicker)
        handler.post(clockTicker)
        rebuildChannels()
        rebuildQuick()
        rebuildInputs()
        rebuildRecent()
        rebuildApps()

        handler.removeCallbacks(wallpaperRotator)
        if (cfg.wallpaperRotationMinutes > 0) {
            handler.postDelayed(wallpaperRotator, cfg.wallpaperRotationMinutes * 60_000L)
        }
    }

    private fun applyHeaderStyle() {
        greetingText.setTextColor(cfg.accentColor)
        val scale = cfg.headerScale / 100f
        clockText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 62f * scale)
        dateText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f * scale.coerceIn(0.85f, 1.25f))
        clockText.setTextColor(cfg.headerColor ?: Color.WHITE)
        dateText.setTextColor(
            cfg.headerColor ?: if (cfg.headerWhite) Color.WHITE else Color.parseColor("#D2DBE4")
        )
        weatherText.background = GradientDrawable().apply {
            setColor(blendColor(Color.parseColor("#330F172A"), cfg.accentColor, 0.25f))
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), Color.argb(70, Color.red(cfg.accentColor), Color.green(cfg.accentColor), Color.blue(cfg.accentColor)))
        }
        if (cfg.headerPanel) {
            headerBox.background = GradientDrawable().apply {
                setColor(Color.parseColor("#66080F18"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#24FFFFFF"))
            }
            headerBox.setPadding(dp(20), dp(14), dp(24), dp(14))
        } else {
            headerBox.background = null
            headerBox.setPadding(0, 0, 0, 0)
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
        updateAmbientGlow(cfg.accentColor)
    }

    private fun updateAmbientGlow(tint: Int) {
        val topTint = Color.argb(65, Color.red(tint), Color.green(tint), Color.blue(tint))
        ambientGlowView.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topTint, Color.TRANSPARENT)
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
            val wasVisible = wallpaperView.visibility == View.VISIBLE
            wallpaperView.setImageBitmap(bmp)
            wallpaperView.scaleType = if (cfg.wallpaperFitFrame) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            wallpaperView.visibility = View.VISIBLE
            if (!wasVisible || cfg.wallpaperRotationMinutes > 0) {
                wallpaperView.alpha = 0f
                wallpaperView.animate().alpha(1f).setDuration(280).start()
            }
        } else {
            wallpaperView.visibility = View.GONE
        }
    }

    private fun updateClock() {
        clockText.visibility = if (cfg.showClock) View.VISIBLE else View.GONE
        dateText.visibility = if (cfg.showClock && cfg.showDate) View.VISIBLE else View.GONE
        if (!cfg.showClock) return
        val now = Date()
        val fmt = when {
            cfg.clock24h && cfg.showClockSeconds -> "HH:mm:ss"
            cfg.clock24h -> "HH:mm"
            cfg.showClockSeconds -> "h:mm:ss a"
            else -> "h:mm a"
        }
        clockText.text = SimpleDateFormat(fmt, Locale.getDefault()).format(now)
        if (cfg.showDate) {
            val vi = Locale.getDefault().language == "vi"
            val solar = SimpleDateFormat(if (vi) "EEEE, dd/MM/yyyy" else "EEEE, d MMMM yyyy", if (vi) Locale("vi", "VN") else Locale.getDefault()).format(now)
            dateText.text = if (cfg.showLunarCalendar && vi) "$solar  ·  ${LunarCalendar.formatToday()}" else solar
        }
    }

    private fun updateGreeting() {
        if (!cfg.showGreeting) {
            greetingText.visibility = View.GONE
            return
        }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val base = when (hour) {
            in 5..11 -> tr("Chào buổi sáng", "Good morning")
            in 12..17 -> tr("Chào buổi chiều", "Good afternoon")
            else -> tr("Chào buổi tối", "Good evening")
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
                weatherText.text = "${info.icon} ${info.tempC}°C · ${info.summary}$place"
                weatherText.visibility = View.VISIBLE
            }
        }
    }

    // ---------- Thông số RAM / Bộ nhớ & Hẹn giờ tắt trên thanh đầu ----------

    private fun updateStatsPill() {
        runCatching {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val ramFreeGb = mi.availMem / (1024.0 * 1024.0 * 1024.0)
            val ramTotGb = mi.totalMem / (1024.0 * 1024.0 * 1024.0)
            val stat = StatFs(Environment.getDataDirectory().path)
            val diskFreeGb = stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
            statsPill.text = String.format(Locale.US, "RAM %.1f/%.1fG · %.1fG", ramFreeGb, ramTotGb, diskFreeGb)
        }
    }

    private fun cycleSleepTimer() {
        val steps = listOf(0, 15, 30, 60, 90, 120)
        val now = System.currentTimeMillis()
        val leftMin = if (sleepTimerUntilMs > now) ((sleepTimerUntilMs - now + 59_999L) / 60_000L).toInt() else 0
        val nextIdx = steps.indexOfFirst { it > leftMin }.let { if (it < 0) 0 else it }
        val chosen = steps[nextIdx]
        if (chosen == 0) {
            sleepTimerUntilMs = 0L
            recentPrefs.edit().remove("sleep_until").apply()
            updateSleepTimerPill()
            toast(tr("Đã huỷ hẹn giờ tắt tivi", "Sleep timer cancelled"))
        } else {
            sleepTimerUntilMs = now + chosen * 60_000L
            recentPrefs.edit().putLong("sleep_until", sleepTimerUntilMs).apply()
            updateSleepTimerPill()
            toast(tr("Tivi sẽ tự ngủ sau $chosen phút (bấm tiếp để đổi)", "TV will sleep in $chosen min (press again to change)"))
        }
    }

    private fun updateSleepTimerPill() {
        if (sleepTimerUntilMs <= 0L) {
            sleepTimerPill.text = tr("⏱ Hẹn tắt", "⏱ Timer")
            return
        }
        val now = System.currentTimeMillis()
        if (now >= sleepTimerUntilMs) {
            sleepTimerUntilMs = 0L
            recentPrefs.edit().remove("sleep_until").apply()
            sleepTimerPill.text = tr("⏱ Hẹn tắt", "⏱ Timer")
            HomeKeyService.sleepNow()
            return
        }
        val mins = ((sleepTimerUntilMs - now + 59_999L) / 60_000L).toInt()
        sleepTimerPill.text = tr("⏱ ${mins}p", "⏱ ${mins}m")
    }

    private fun rebuildChannels() {
        channelsRow.removeAllViews()
        if (cfg.linkTiles.isEmpty() || cfg.kidsMode) {
            channelsScroll.visibility = View.GONE
            return
        }
        channelsScroll.visibility = View.VISIBLE
        val tilesDir = ConfigStore.linkTilesDir(this)
        val radius = tileCornerRadiusPx()
        cfg.linkTiles.forEach { tile ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isFocusableInTouchMode = true
                background = focusableCardDrawable(tile.color, radius)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(dp(144), dp(88)).apply { marginEnd = dp(12) }
                setOnClickListener { openLinkTile(tile) }
                setOnFocusChangeListener { v, hasFocus ->
                    v.animate().scaleX(if (hasFocus) 1.07f else 1f).scaleY(if (hasFocus) 1.07f else 1f).setDuration(120).start()
                    if (hasFocus) updateAmbientGlow(tile.color)
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
            toast(tr("Không mở được ${tile.label}", "Could not open ${tile.label}"))
        }
    }

    private fun rebuildApps() {
        dockRow.removeAllViews()
        appsGrid.removeAllViews()
        appsGrid.columnCount = cfg.columns

        val resolved = collectApps()
        val cardBg = if (cfg.dockGlass) Color.parseColor("#29FFFFFF") else Color.parseColor("#182430")
        val radius = tileCornerRadiusPx()
        val screenW = resources.displayMetrics.widthPixels - dp(104)
        val tileW = (screenW / cfg.columns) - dp(12)
        val (tileH, iconDp) = when (cfg.tileScale) {
            "small" -> dp(102) to dp(44)
            "large" -> dp(148) to dp(68)
            else -> dp(124) to dp(56)
        }

        if (cfg.kidsMode) {
            dockSection.visibility = View.GONE
            appsLabel.visibility = View.VISIBLE
            appsGrid.visibility = View.VISIBLE
            resolved.values.filter { it.pkg in cfg.kidsApps }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }
                .forEach { app -> appsGrid.addView(makeAppTile(app, cardBg, radius, tileW, tileH, iconDp, inDock = false)) }
            return
        }

        // Chế độ 1 hàng (single_row): toàn bộ ứng dụng nằm gọn trên khay Dock ngang kiểu macOS / Apple TV.
        val dockEntries = mutableListOf<AppEntry>()
        if (cfg.singleRow) {
            cfg.dockApps.forEach { pkg -> resolved.remove(pkg)?.let { dockEntries += it } }
            dockEntries.addAll(resolved.values.sortedBy { it.label.lowercase(Locale.getDefault()) })
            resolved.clear()
        } else {
            cfg.dockApps.forEach { pkg -> resolved.remove(pkg)?.let { dockEntries += it } }
        }

        if (dockEntries.isNotEmpty()) {
            dockSection.visibility = View.VISIBLE
            dockTitleText.visibility = if (cfg.dockTitle) View.VISIBLE else View.GONE
            dockTitleText.text = dockEntries.first().label
            dockContainer.background = GradientDrawable().apply {
                setColor(if (cfg.dockGlass) Color.parseColor("#24FFFFFF") else Color.parseColor("#CC111924"))
                cornerRadius = (radius + dp(8)).toFloat()
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            val dockTileW = when (cfg.tileScale) {
                "small" -> dp(116)
                "large" -> dp(162)
                else -> dp(138)
            }
            dockEntries.forEach { app ->
                dockRow.addView(makeAppTile(app, cardBg, radius, dockTileW, tileH, iconDp, inDock = true))
            }
        } else {
            dockSection.visibility = View.GONE
        }

        val rest = resolved.values.sortedBy { it.label.lowercase(Locale.getDefault()) }
        if (rest.isEmpty() && dockEntries.isNotEmpty()) {
            appsLabel.visibility = View.GONE
            appsGrid.visibility = View.GONE
        } else {
            appsLabel.visibility = View.VISIBLE
            appsGrid.visibility = View.VISIBLE
            rest.forEach { app ->
                appsGrid.addView(makeAppTile(app, cardBg, radius, tileW, tileH, iconDp, inDock = false))
            }
        }
    }

    private fun makeAppTile(
        app: AppEntry,
        cardBg: Int,
        radius: Int,
        tileW: Int,
        tileH: Int,
        iconDp: Int,
        inDock: Boolean,
    ): LinearLayout {
        val isStartup = app.pkg == cfg.openOnStartPkg
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            background = focusableCardDrawable(cardBg, radius)
            setPadding(dp(12), dp(12), dp(12), dp(10))
            layoutParams = if (inDock) {
                LinearLayout.LayoutParams(tileW, tileH).apply { marginEnd = dp(10) }
            } else {
                GridLayout.LayoutParams().apply {
                    width = tileW.coerceAtLeast(dp(116))
                    height = tileH
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
            }
            setOnClickListener { launchApp(app.pkg) }
            setOnLongClickListener {
                if (!cfg.kidsMode) showAppMenu(app)
                true
            }
            setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.07f else 1f).scaleY(if (hasFocus) 1.07f else 1f).setDuration(120).start()
                if (hasFocus && inDock && cfg.dockTitle) {
                    dockTitleText.text = app.label
                }
            }

            addView(ImageView(context).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(iconDp, iconDp))

            addView(TextView(context).apply {
                text = if (isStartup) "★ ${app.label}" else app.label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(0, dp(8), 0, 0)
            }, LinearLayout.LayoutParams(-1, -2))
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
            pushRecent(pkg)
            runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    // ---------- Danh sách app & Tìm kiếm nhanh ----------

    private fun collectApps(): LinkedHashMap<String, AppEntry> {
        val pm = packageManager
        val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = LinkedHashMap<String, AppEntry>()
        fun collect(list: List<ResolveInfo>) {
            for (ri in list) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == packageName || pkg in cfg.hiddenApps) continue
                if (pkg !in resolved) {
                    resolved[pkg] = AppEntry(pkg, ri.loadLabel(pm).toString(), ri.loadIcon(pm))
                }
            }
        }
        collect(pm.queryIntentActivities(leanbackIntent, PackageManager.MATCH_ALL))
        collect(
            pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
                .filter { it.activityInfo?.packageName in cfg.libraryApps || it.activityInfo?.packageName in cfg.dockApps }
        )
        return resolved
    }

    private fun orderedApps(): MutableList<AppEntry> {
        val resolved = collectApps()
        val ordered = mutableListOf<AppEntry>()
        cfg.dockApps.forEach { pkg -> resolved.remove(pkg)?.let { ordered += it } }
        ordered.addAll(resolved.values.sortedBy { it.label.lowercase(Locale.getDefault()) })
        return ordered
    }

    private fun stripAccents(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')

    private fun showSearchDialog() {
        val allApps = orderedApps()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(10))
        }
        val queryInput = EditText(this).apply {
            hint = tr("Gõ tên ứng dụng (vd: youtube, vtv, kodi)…", "Type app name (e.g. youtube, netflix, vlc)…")
            isSingleLine = true
        }
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply {
            addView(listContainer, ViewGroup.LayoutParams(-1, -2))
        }
        root.addView(queryInput, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(300)).apply { topMargin = dp(10) })

        val dialog = AlertDialog.Builder(this)
            .setTitle(tr("Tìm nhanh ứng dụng (${allApps.size})", "Quick app search (${allApps.size})"))
            .setView(root)
            .setNegativeButton(tr("Đóng", "Close"), null)
            .create()

        fun renderResults(q: String) {
            listContainer.removeAllViews()
            val norm = stripAccents(q.trim())
            val matches = if (norm.isEmpty()) allApps else allApps.filter {
                stripAccents(it.label).contains(norm) || it.pkg.lowercase(Locale.US).contains(norm)
            }
            matches.take(30).forEach { app ->
                val row = TextView(this).apply {
                    text = "${app.label}  (${app.pkg})"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    isFocusable = true
                    isClickable = true
                    setOnClickListener {
                        dialog.dismiss()
                        launchApp(app.pkg)
                    }
                }
                listContainer.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
        }

        queryInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderResults(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        renderResults("")
        dialog.show()
        queryInput.requestFocus()
    }

    // ---------- Tiện ích nhanh ----------

    private fun buildSection(title: String): Pair<LinearLayout, LinearLayout> {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        section.addView(TextView(this).apply {
            text = title
            kieuNhanMuc(this)
        })
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scroll.addView(row)
        section.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        return section to row
    }

    private fun makeChip(label: String, onClick: () -> Unit): TextView =
        makePill(label, onClick).apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) }
        }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun rebuildQuick() {
        quickRow.removeAllViews()
        val show = cfg.showQuickActions && !cfg.kidsMode
        quickSection.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        quickRow.addView(makeChip(tr("Âm lượng −", "Volume −")) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        })
        quickRow.addView(makeChip(tr("Âm lượng +", "Volume +")) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        })
        quickRow.addView(makeChip(tr("Tắt tiếng", "Mute")) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
        })
        quickRow.addView(makeChip(tr("Dọn RAM", "Free RAM")) { freeRam() })
        quickRow.addView(makeChip(tr("Màn hình chờ", "Screensaver")) {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { toast(tr("Tivi không mở được màn hình chờ", "This TV could not start the screensaver")) }
        })
    }

    private fun freeRam() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        fun avail(): Long = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        val before = avail()
        val pkgs = collectApps().keys
        scope.launch {
            withContext(Dispatchers.IO) { pkgs.forEach { runCatching { am.killBackgroundProcesses(it) } } }
            kotlinx.coroutines.delay(800)
            val freed = ((avail() - before) / (1024 * 1024)).coerceAtLeast(0)
            if (cfg.showStats) updateStatsPill()
            toast(tr("Đã dọn RAM: trống thêm khoảng $freed MB", "RAM freed: about $freed MB more available"))
        }
    }

    // ---------- Nguồn vào ----------

    private fun rebuildInputs() {
        inputsRow.removeAllViews()
        val show = cfg.showInputs && !cfg.kidsMode
        var any = false
        if (show) {
            runCatching {
                val tim = getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
                val allInputs = tim.tvInputList
                val lastInput = recentPrefs.getString("last_input", null)
                val candidates = allInputs.filter { it.isPassthroughInput }.ifEmpty {
                    allInputs.filter { it.type != TvInputInfo.TYPE_TUNER }
                }
                candidates.forEach { info ->
                    val rawLabel = (info.loadCustomLabel(this) ?: info.loadLabel(this)).toString()
                    val label = if (info.id == lastInput) "● $rawLabel" else rawLabel
                    inputsRow.addView(makeChip(label) { switchInput(info.id) })
                    any = true
                }
            }
        }
        inputsSection.visibility = if (show && any) View.VISIBLE else View.GONE
    }

    private fun switchInput(inputId: String) {
        recentPrefs.edit().putString("last_input", inputId).apply()
        rebuildInputs()
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { toast(tr("Không chuyển được nguồn vào", "Could not switch input")) }
    }

    private fun openSystemSettings() {
        val primary = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(primary) }.isFailure) {
            runCatching {
                packageManager.getLeanbackLaunchIntentForPackage("com.android.tv.settings")
                    ?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }

    /** Giữ OK trên nút Cài đặt: mở nhanh Wi-Fi, Ứng dụng, Hiện lại app đã ẩn, Bố cục nhanh & Tuỳ chọn nhà phát triển. */
    private fun showQuickSettingsMenu() {
        val items = mutableListOf<Pair<String, () -> Unit>>(
            tr("Mạng & Wi-Fi", "Network & Wi-Fi") to {
                runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            tr("Quản lý ứng dụng (Bật lại)", "Apps (Re-enable)") to {
                runCatching { startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            tr("Đổi nhanh bố cục & cỡ ô trên TV", "Quick layout & tile size") to {
                showQuickLayoutDialog()
            },
        )
        if (cfg.hiddenApps.isNotEmpty()) {
            items += tr("Ứng dụng đã ẩn (${cfg.hiddenApps.size}) — Hiện lại", "Hidden apps (${cfg.hiddenApps.size}) — Unhide") to {
                showUnhideAppsDialog()
            }
        }
        val dev = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (dev.resolveActivity(packageManager) != null) {
            items += tr("Tuỳ chọn nhà phát triển (bật lại Gỡ lỗi USB)", "Developer options (turn USB debugging back on)") to {
                runCatching { startActivity(dev) }
            }
        }
        items += tr("Cài đặt hệ thống", "System Settings") to { openSystemSettings() }
        AlertDialog.Builder(this)
            .setTitle(tr("Cài đặt nhanh CleanTxa Home", "CleanTxa Home Quick Settings"))
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun showUnhideAppsDialog() {
        val pm = packageManager
        val hiddenList = cfg.hiddenApps.sorted()
        if (hiddenList.isEmpty()) return
        val labels = hiddenList.map { pkg ->
            val name = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            "$name ($pkg)"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(tr("Chọn ứng dụng để hiện lại trên Home", "Pick an app to restore to Home"))
            .setItems(labels) { _, which ->
                val target = hiddenList[which]
                ConfigStore.updateRaw(this) { j ->
                    val next = cfg.hiddenApps - target
                    j.put("hidden_apps", JSONArray(next.toList()))
                }
                reloadAll()
                toast(tr("Đã hiện lại ứng dụng trên Home", "App restored to Home"))
            }
            .setNeutralButton(tr("Hiện lại tất cả", "Unhide all")) { _, _ ->
                ConfigStore.updateRaw(this) { it.remove("hidden_apps") }
                reloadAll()
                toast(tr("Đã hiện lại toàn bộ ứng dụng", "All hidden apps restored"))
            }
            .setNegativeButton(tr("Đóng", "Close"), null)
            .show()
    }

    private fun showQuickLayoutDialog() {
        val options = listOf<Pair<String, () -> Unit>>(
            tr("4 cột · Ô lớn (dễ nhìn từ xa)", "4 columns · Large tiles") to {
                ConfigStore.updateRaw(this) { it.put("columns", 4).put("tile_scale", "large") }; reloadAll()
            },
            tr("5 cột · Ô vừa (cân đối)", "5 columns · Medium tiles") to {
                ConfigStore.updateRaw(this) { it.put("columns", 5).put("tile_scale", "medium") }; reloadAll()
            },
            tr("6 cột · Ô nhỏ (nhiều ứng dụng)", "6 columns · Small tiles") to {
                ConfigStore.updateRaw(this) { it.put("columns", 6).put("tile_scale", "small") }; reloadAll()
            },
            tr("Chế độ 1 hàng Dock gọn: ${if (cfg.singleRow) tr("Đang bật → Tắt", "On → Turn off") else tr("Đang tắt → Bật", "Off → Turn on")}",
                "Single-row Dock mode: ${if (cfg.singleRow) "On → Turn off" else "Off → Turn on"}") to {
                ConfigStore.updateRaw(this) { it.put("single_row", !cfg.singleRow) }; reloadAll()
            },
        )
        AlertDialog.Builder(this)
            .setTitle(tr("Bố cục nhanh", "Quick Layout"))
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> options[which].second() }
            .show()
    }

    // ---------- Mở gần đây ----------

    private val recentPrefs get() = getSharedPreferences("home", Context.MODE_PRIVATE)

    private fun recentPkgs(): List<String> =
        recentPrefs.getString("recent", "").orEmpty().split(',').filter { it.isNotBlank() }

    private fun pushRecent(pkg: String) {
        val list = (listOf(pkg) + recentPkgs().filter { it != pkg }).take(MAX_RECENT)
        recentPrefs.edit().putString("recent", list.joinToString(",")).apply()
    }

    private fun rebuildRecent() {
        recentRow.removeAllViews()
        val show = cfg.showRecent && !cfg.kidsMode
        if (!show) {
            recentSection.visibility = View.GONE
            return
        }
        val apps = collectApps()
        val recent = recentPkgs().mapNotNull { apps[it] }
        val radius = tileCornerRadiusPx()
        recent.forEach { app ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isFocusableInTouchMode = true
                background = focusableCardDrawable(Color.parseColor("#26FFFFFF"), radius)
                setPadding(dp(12), dp(8), dp(16), dp(8))
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) }
                setOnClickListener { launchApp(app.pkg) }
                setOnFocusChangeListener { v, hasFocus ->
                    v.animate().scaleX(if (hasFocus) 1.06f else 1f).scaleY(if (hasFocus) 1.06f else 1f).setDuration(120).start()
                }
            }
            card.addView(ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(32), dp(32)))
            card.addView(TextView(this).apply {
                text = app.label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 1
                setPadding(dp(10), 0, 0, 0)
            })
            recentRow.addView(card)
        }
        recentSection.visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE
    }

    // ---------- Chế độ trẻ em ----------

    private fun setKidsMode(on: Boolean) {
        ConfigStore.updateRaw(this) { it.put("kids_mode", on) }
        reloadAll()
    }

    private fun onKidsPillClick() {
        if (!cfg.kidsMode) {
            if (cfg.kidsApps.isEmpty()) {
                toast(tr("Giữ OK trên một app > Thêm vào danh sách trẻ em", "Hold OK on an app > Add to kids list first"))
            } else {
                setKidsMode(true)
            }
            return
        }
        if (cfg.kidsPin.isBlank()) {
            setKidsMode(false)
        } else {
            askPin(tr("Nhập mã PIN để thoát", "Enter the PIN to exit")) { pin ->
                if (pin == cfg.kidsPin) setKidsMode(false) else toast(tr("Sai mã PIN", "Wrong PIN"))
            }
        }
    }

    private fun onKidsPillLongClick() {
        if (cfg.kidsMode) return
        askPin(tr("Đặt mã PIN thoát chế độ trẻ em (để trống = không dùng PIN)", "Set a PIN to exit kids mode (empty = no PIN)")) { pin ->
            ConfigStore.updateRaw(this) { it.put("kids_pin", pin) }
            reloadAll()
            toast(if (pin.isBlank()) tr("Đã bỏ mã PIN", "PIN removed") else tr("Đã đặt mã PIN", "PIN set"))
        }
    }

    private fun askPin(title: String, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(8))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ -> onOk(input.text.toString().trim()) }
            .setNegativeButton(tr("Huỷ", "Cancel"), null)
            .show()
    }

    // ---------- Menu giữ OK trên app ----------

    private fun showAppMenu(app: AppEntry) {
        val inKids = app.pkg in cfg.kidsApps
        val inDock = app.pkg in cfg.dockApps
        val isStartup = app.pkg == cfg.openOnStartPkg
        val items = listOf<Pair<String, () -> Unit>>(
            (if (inDock) tr("Bỏ khỏi khay Yêu thích (Dock)", "Remove from Favourites Dock")
            else tr("Ghim lên khay Yêu thích (Dock)", "Pin to Favourites Dock")) to { toggleDockApp(app.pkg, !inDock) },
            tr("Đưa lên đầu", "Move to first") to { moveApp(app.pkg, MOVE_FIRST) },
            tr("Dịch sang trái", "Move left") to { moveApp(app.pkg, MOVE_LEFT) },
            tr("Dịch sang phải", "Move right") to { moveApp(app.pkg, MOVE_RIGHT) },
            (if (isStartup) tr("Tắt tự mở khi khởi động tivi", "Stop opening automatically at boot")
            else tr("★ Mở tự động khi bật tivi", "★ Open automatically at TV boot")) to {
                ConfigStore.updateRaw(this) { j ->
                    if (isStartup) j.remove("open_on_start_pkg") else j.put("open_on_start_pkg", app.pkg)
                }
                reloadAll()
                toast(if (isStartup) tr("Đã tắt tự mở lúc khởi động", "Auto-open on boot disabled")
                else tr("Sẽ tự mở ${app.label} khi khởi động tivi", "${app.label} will open at TV boot"))
            },
            tr("Ẩn khỏi màn hình chính", "Hide from Home") to {
                ConfigStore.updateRaw(this) { j ->
                    val set = LinkedHashSet(cfg.hiddenApps).apply { add(app.pkg) }
                    j.put("hidden_apps", JSONArray(set.toList()))
                }
                reloadAll()
                toast(tr("Đã ẩn. Giữ OK trên nút Cài đặt để hiện lại bất cứ lúc nào.", "Hidden. Hold OK on Settings to unhide anytime."))
            },
            (if (inKids) tr("Bỏ khỏi danh sách trẻ em", "Remove from kids list")
            else tr("Thêm vào danh sách trẻ em", "Add to kids list")) to { toggleKidsApp(app.pkg, !inKids) },
            tr("Thông tin ứng dụng", "App info") to {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            tr("Gỡ cài đặt", "Uninstall") to {
                runCatching {
                    startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { toast(tr("Không gỡ được app này", "Could not uninstall this app")) }
            },
        )
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun toggleDockApp(pkg: String, add: Boolean) {
        val next = cfg.dockApps.toMutableList()
        if (add) {
            if (pkg !in next) next.add(pkg)
        } else {
            next.remove(pkg)
        }
        ConfigStore.updateRaw(this) { it.put("dock_apps", JSONArray(next)) }
        reloadAll()
    }

    private fun moveApp(pkg: String, how: Int) {
        val list = orderedApps().map { it.pkg }.toMutableList()
        val i = list.indexOf(pkg)
        if (i < 0) return
        list.removeAt(i)
        val j = when (how) {
            MOVE_FIRST -> 0
            MOVE_LEFT -> maxOf(0, i - 1)
            else -> minOf(list.size, i + 1)
        }
        list.add(j, pkg)
        val giuLai = cfg.dockApps.filter { it !in list }
        ConfigStore.updateRaw(this) { it.put("dock_apps", JSONArray(list + giuLai)) }
        reloadAll()
    }

    private fun toggleKidsApp(pkg: String, add: Boolean) {
        ConfigStore.updateRaw(this) { j ->
            val set = LinkedHashSet<String>()
            j.optJSONArray("kids_apps")?.let { a -> for (k in 0 until a.length()) set += a.optString(k) }
            if (add) set += pkg else set -= pkg
            j.put("kids_apps", JSONArray(set.toList()))
        }
        reloadAll()
        toast(if (add) tr("Đã thêm vào danh sách trẻ em", "Added to kids list") else tr("Đã bỏ khỏi danh sách trẻ em", "Removed from kids list"))
    }

    private companion object {
        const val MAX_RECENT = 8
        const val MOVE_FIRST = 0
        const val MOVE_LEFT = -1
        const val MOVE_RIGHT = 1
    }

    private data class AppEntry(val pkg: String, val label: String, val icon: Drawable)
}
