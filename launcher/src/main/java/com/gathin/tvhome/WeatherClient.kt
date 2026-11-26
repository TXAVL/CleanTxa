package com.gathin.tvhome

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WeatherClient {
    data class WeatherInfo(val tempC: Int, val summary: String, val icon: String)

    @Volatile
    private var lastFetchMs = 0L
    @Volatile
    private var lastKey = ""
    @Volatile
    private var cached: WeatherInfo? = null

    fun fetch(lat: Double, lon: Double): WeatherInfo? {
        val lang = if (java.util.Locale.getDefault().language == "vi") "vi" else "en"
        val key = "$lat,$lon,$lang"
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastFetchMs < 30 * 60_000L && cached != null) {
            return cached
        }
        return runCatching {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true")
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 6000
            val json = c.inputStream.use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
            c.disconnect()
            val cw = json.optJSONObject("current_weather") ?: return@runCatching null
            val temp = Math.round(cw.optDouble("temperature", 0.0)).toInt()
            val code = cw.optInt("weathercode", 0)
            val (icon, desc) = describe(code)
            WeatherInfo(temp, desc, icon).also {
                lastKey = key
                lastFetchMs = now
                cached = it
            }
        }.getOrNull()
    }

    private fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> "☀" to tr("Quang đãng", "Clear")
        1, 2 -> "🌤" to tr("Ít mây", "Partly cloudy")
        3 -> "☁" to tr("Nhiều mây", "Overcast")
        45, 48 -> "🌫" to tr("Sương mù", "Foggy")
        51, 53, 55, 61, 63, 65 -> "🌧" to tr("Có mưa", "Rain")
        71, 73, 75, 77 -> "❄" to tr("Tuyết", "Snow")
        80, 81, 82 -> "🌦" to tr("Mưa rào", "Showers")
        95, 96, 99 -> "⛈" to tr("Giông bão", "Thunderstorm")
        else -> "🌤" to tr("Mát mẻ", "Mild")
    }
}
