package com.gathin.tvhome

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LinkTile(
    val id: String,
    val label: String,
    val pkg: String,
    val uri: String,
    val color: Int,
    val imageFile: String?,
)

data class LauncherConfig(
    val backgroundColor: Int = Color.parseColor("#0B1118"),
    val accentColor: Int = Color.parseColor("#2EC4B6"),
    val headerColor: Int? = null,
    val headerScale: Int = 100,
    val headerPanel: Boolean = false,
    val skyIntensity: Int = 55,
    val wallpaperEnabled: Boolean = false,
    val wallpaperFitFrame: Boolean = false,
    val wallpaperRotationMinutes: Int = 0,
    val dockGlass: Boolean = true,
    val dockTitle: Boolean = true,
    val appGlow: Boolean = false,
    val headerWhite: Boolean = true,
    val showGreeting: Boolean = true,
    val greetingName: String = "",
    val showLunarCalendar: Boolean = true,
    val weatherLat: Double? = null,
    val weatherLon: Double? = null,
    val weatherPlace: String = "",
    val columns: Int = 5,
    val singleRow: Boolean = false,
    val uniformTiles: Boolean = true,
    val showInputButton: Boolean = true,
    val showSleepButton: Boolean = false,
    val showSleepTimerButton: Boolean = false,
    val showRecentsButton: Boolean = false,
    val showNowPlaying: Boolean = true,
    val showStats: Boolean = false,
    val dreamMotion: Boolean = false,
    val showClockSeconds: Boolean = false,
    val showNetworkIp: Boolean = false,
    val tileScale: String = "medium",
    val tileCornerStyle: String = "rounded",
    val wallpaperDim: Int = 35,
    val stockHomeRedirect: Boolean = false,
    val stockHomePackage: String = "",
    val powerOnHomeAlways: Boolean = false,
    val openOnStartPkg: String = "",
    val volumeKeyRepeat: Boolean = false,
    val appKeys: Map<Int, String> = emptyMap(),
    val appScancodes: Map<Int, String> = emptyMap(),
    val settingsKeycodes: Set<Int> = emptySet(),
    val dockApps: List<String> = emptyList(),
    val hiddenApps: Set<String> = emptySet(),
    val libraryApps: Set<String> = emptySet(),
    val linkTiles: List<LinkTile> = emptyList(),
    /** Hàng tiện ích nhanh (âm lượng, tắt tiếng, dọn RAM, Wi-Fi, màn chờ). */
    val showQuickActions: Boolean = true,
    /** Hàng "Mở gần đây". */
    val showRecent: Boolean = true,
    /** Hàng nguồn vào (HDMI, AV...). */
    val showInputs: Boolean = true,
    /** Nút Trẻ em trên thanh trên cùng. */
    val showKidsButton: Boolean = true,
    /** Chế độ trẻ em đang bật: chỉ hiện [kidsApps], thoát bằng [kidsPin] nếu có đặt. */
    val kidsMode: Boolean = false,
    val kidsApps: Set<String> = emptySet(),
    val kidsPin: String = "",
    /** Đồng hồ: ẩn thì ẩn luôn dòng ngày; hiện thì có thêm giây ([showClockSeconds]), ngày ([showDate]), 24 giờ ([clock24h]). */
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val clock24h: Boolean = true,
    /** Hai nút trên thanh trên cùng. */
    val showWifiButton: Boolean = true,
    val showSettingsButton: Boolean = true,
    val raw: JSONObject = JSONObject(),
)

object ConfigStore {
    const val ACTION_RELOAD_CONFIG = "zone.vgs.cleantv.RELOAD_CONFIG"
    const val ACTION_LEARN_KEY = "zone.vgs.cleantv.LEARN_KEY"
    const val ACTION_KEY_LEARNED = "com.gathin.tvhome.KEY_LEARNED"
    const val ACTION_TYPE_TEXT = "zone.vgs.cleantv.TYPE_TEXT"
    const val ACTION_OPEN_LIBRARY = "com.gathin.tvhome.OPEN_LIBRARY"
    const val KEY_UNKNOWN_SCAN = 240

    fun configFile(ctx: Context): File = File(ctx.filesDir, "config.json")
    fun wallpaperFile(ctx: Context): File = File(ctx.filesDir, "wallpaper.jpg")
    fun wallpapersDir(ctx: Context): File = File(ctx.filesDir, "wallpapers").apply { mkdirs() }
    fun linkTilesDir(ctx: Context): File = File(ctx.filesDir, "link_tiles").apply { mkdirs() }

    fun load(ctx: Context): LauncherConfig {
        val f = configFile(ctx)
        val j = runCatching {
            if (f.isFile) JSONObject(f.readText(Charsets.UTF_8)) else JSONObject()
        }.getOrDefault(JSONObject())

        val bgStr = j.optString("background_color", "#0B1118")
        val bg = runCatching { Color.parseColor(bgStr) }.getOrDefault(Color.parseColor("#0B1118"))

        val accentStr = j.optString("accent_color", "#2EC4B6")
        val accent = runCatching { Color.parseColor(accentStr) }.getOrDefault(Color.parseColor("#2EC4B6"))

        val hdrColor = j.optString("header_color", "").takeIf { it.startsWith("#") && it.length in 7..9 }
            ?.let { runCatching { Color.parseColor(it) }.getOrNull() }

        val appKeysMap = LinkedHashMap<Int, String>()
        val appScanMap = LinkedHashMap<Int, String>()
        j.optJSONArray("app_keys")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).trim()
                if (item.startsWith("scan:", ignoreCase = true)) {
                    val rest = item.substringAfter(':')
                    val scan = rest.substringBefore(':').trim().toIntOrNull()
                    val pkg = rest.substringAfter(':', "").trim()
                    if (scan != null && scan > 0 && scan != KEY_UNKNOWN_SCAN && pkg.isNotBlank()) {
                        appScanMap.putIfAbsent(scan, pkg)
                    }
                } else {
                    val code = item.substringBefore(':').trim().toIntOrNull()
                    val pkg = item.substringAfter(':', "").trim()
                    if (code != null && code > 0 && pkg.isNotBlank()) {
                        appKeysMap.putIfAbsent(code, pkg)
                    }
                }
            }
        }

        val settingsCodes = HashSet<Int>()
        j.optJSONArray("settings_key_keycodes")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optInt(i, -1).takeIf { it > 0 }?.let { settingsCodes.add(it) }
            }
        }

        val tiles = mutableListOf<LinkTile>()
        j.optJSONArray("link_tiles")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val label = o.optString("label").ifBlank { o.optString("name") }
                val pkg = o.optString("package").ifBlank { o.optString("pkg") }
                val uri = o.optString("uri")
                val id = o.optString("id").ifBlank { label.lowercase().replace(Regex("[^a-z0-9]"), "") }
                val col = runCatching { Color.parseColor(o.optString("color", "#0E3B37")) }.getOrDefault(Color.parseColor("#0E3B37"))
                val img = o.optString("image").ifBlank { "$id.jpg" }
                if (label.isNotBlank() && uri.isNotBlank()) {
                    tiles += LinkTile(id, label, pkg, uri, col, img)
                }
            }
        }

        fun strList(key: String): List<String> {
            val a = j.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optString(it).trim().takeIf { s -> s.isNotEmpty() } }
        }

        val lat = if (j.has("weather_lat")) j.optDouble("weather_lat", Double.NaN).takeIf { !it.isNaN() } else null
        val lon = if (j.has("weather_lon")) j.optDouble("weather_lon", Double.NaN).takeIf { !it.isNaN() } else null

        return LauncherConfig(
            backgroundColor = bg,
            accentColor = accent,
            headerColor = hdrColor,
            headerScale = j.optInt("header_scale", 100).coerceIn(70, 160),
            headerPanel = j.optBoolean("header_panel", false),
            skyIntensity = j.optInt("sky_intensity", 55).coerceIn(0, 100),
            wallpaperEnabled = j.optBoolean("wallpaper", false),
            wallpaperFitFrame = j.optBoolean("wallpaper_fit", false),
            wallpaperRotationMinutes = j.optInt("wallpaper_rotation_minutes", 0).coerceAtLeast(0),
            dockGlass = j.optBoolean("dock_glass", true),
            dockTitle = j.optBoolean("dock_title", true),
            appGlow = j.optBoolean("app_glow", false),
            headerWhite = j.optBoolean("header_white", true),
            showGreeting = j.optBoolean("show_greeting", true),
            greetingName = j.optString("greeting_name", ""),
            showLunarCalendar = j.optBoolean("show_lunar_calendar", true),
            weatherLat = lat,
            weatherLon = lon,
            weatherPlace = j.optString("weather_place", ""),
            columns = j.optInt("columns", 5).coerceIn(3, 8),
            singleRow = j.optBoolean("single_row", false),
            uniformTiles = j.optBoolean("uniform_tiles", true),
            showInputButton = j.optBoolean("show_input_button", true),
            showSleepButton = j.optBoolean("sleep_button", j.optBoolean("show_sleep_button", false)),
            showSleepTimerButton = j.optBoolean("sleep_timer_button", false),
            showRecentsButton = j.optBoolean("recents_button", false),
            showNowPlaying = j.optBoolean("show_now_playing", true),
            showStats = j.optBoolean("show_stats", false),
            dreamMotion = j.optBoolean("dream_motion", false),
            showClockSeconds = j.optBoolean("show_clock_seconds", false),
            showNetworkIp = j.optBoolean("show_network_ip", false),
            tileScale = j.optString("tile_scale", "medium").ifBlank { "medium" },
            tileCornerStyle = j.optString("tile_corner_style", "rounded").ifBlank { "rounded" },
            wallpaperDim = j.optInt("wallpaper_dim", 35).coerceIn(0, 80),
            stockHomeRedirect = j.optString("stock_home_redirect", "").isNotBlank() || j.optBoolean("stock_home_redirect", false),
            stockHomePackage = j.optString("stock_home_redirect", "").takeIf { it != "true" && it != "false" }.orEmpty(),
            powerOnHomeAlways = j.optBoolean("power_on_home_always", false),
            openOnStartPkg = j.optString("open_on_start_pkg", ""),
            volumeKeyRepeat = j.optBoolean("volume_key_repeat", !j.optBoolean("key_filter", true)),
            appKeys = appKeysMap,
            appScancodes = appScanMap,
            settingsKeycodes = settingsCodes,
            dockApps = strList("dock_apps"),
            hiddenApps = strList("hidden_apps").toSet(),
            libraryApps = strList("library_apps").toSet(),
            linkTiles = tiles,
            showQuickActions = j.optBoolean("show_quick_actions", true),
            showRecent = j.optBoolean("show_recent", true),
            showInputs = j.optBoolean("show_inputs", true),
            showKidsButton = j.optBoolean("show_kids_button", true),
            kidsMode = j.optBoolean("kids_mode", false),
            kidsApps = strList("kids_apps").toSet(),
            kidsPin = j.optString("kids_pin", ""),
            showClock = j.optBoolean("show_clock", true),
            showDate = j.optBoolean("show_date", true),
            clock24h = j.optBoolean("clock_24h", true),
            showWifiButton = j.optBoolean("show_wifi_button", true),
            showSettingsButton = j.optBoolean("show_settings_button", true),
            raw = j,
        )
    }

    fun updateRaw(ctx: Context, block: (JSONObject) -> Unit) {
        val f = configFile(ctx)
        val j = runCatching { if (f.isFile) JSONObject(f.readText(Charsets.UTF_8)) else JSONObject() }.getOrDefault(JSONObject())
        block(j)
        f.writeText(j.toString(2), Charsets.UTF_8)
    }
}
