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
    val skyIntensity: Int = 55,
    val wallpaperEnabled: Boolean = false,
    val wallpaperFitFrame: Boolean = false,
    val wallpaperRotationMinutes: Int = 0,
    val dockGlass: Boolean = true,
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
    val showNowPlaying: Boolean = true,
    val showClockSeconds: Boolean = false,
    val showNetworkIp: Boolean = false,
    val tileScale: String = "medium",
    val wallpaperDim: Int = 35,
    val stockHomeRedirect: Boolean = false,
    val powerOnHomeAlways: Boolean = false,
    val volumeKeyRepeat: Boolean = false,
    val appKeys: Map<Int, String> = emptyMap(),
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
    val raw: JSONObject = JSONObject(),
)

object ConfigStore {
    const val ACTION_RELOAD_CONFIG = "zone.vgs.cleantv.RELOAD_CONFIG"
    const val ACTION_LEARN_KEY = "com.gathin.tvhome.LEARN_KEY"
    const val ACTION_KEY_LEARNED = "com.gathin.tvhome.KEY_LEARNED"
    const val ACTION_TYPE_TEXT = "com.gathin.tvhome.TYPE_TEXT"

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

        val appKeysMap = HashMap<Int, String>()
        j.optJSONArray("app_keys")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.optString(i)
                val code = item.substringBefore(':').toIntOrNull()
                val pkg = item.substringAfter(':', "")
                if (code != null && pkg.isNotBlank()) appKeysMap[code] = pkg
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
            skyIntensity = j.optInt("sky_intensity", 55).coerceIn(0, 100),
            wallpaperEnabled = j.optBoolean("wallpaper", false),
            wallpaperFitFrame = j.optBoolean("wallpaper_fit", false),
            wallpaperRotationMinutes = j.optInt("wallpaper_rotation_minutes", 0).coerceAtLeast(0),
            dockGlass = j.optBoolean("dock_glass", true),
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
            showSleepButton = j.optBoolean("show_sleep_button", false),
            showNowPlaying = j.optBoolean("show_now_playing", true),
            showClockSeconds = j.optBoolean("show_clock_seconds", false),
            showNetworkIp = j.optBoolean("show_network_ip", false),
            tileScale = j.optString("tile_scale", "medium").ifBlank { "medium" },
            wallpaperDim = j.optInt("wallpaper_dim", 35).coerceIn(0, 80),
            stockHomeRedirect = j.optBoolean("stock_home_redirect", false),
            powerOnHomeAlways = j.optBoolean("power_on_home_always", false),
            volumeKeyRepeat = j.optBoolean("volume_key_repeat", false),
            appKeys = appKeysMap,
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

