package com.gathin.tvhome

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WeatherClient {
    data class WeatherInfo(val tempC: Int, val summary: String)

    @Volatile
    private var lastFetchMs = 0L
    @Volatile
    private var lastKey = ""
    @Volatile
    private var cached: WeatherInfo? = null

    fun fetch(lat: Double, lon: Double): WeatherInfo? {
        val key = "$lat,$lon"
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
            WeatherInfo(temp, describe(code)).also {
                lastKey = key
                lastFetchMs = now
                cached = it
            }
        }.getOrNull()
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "Quang đãng"
        1, 2, 3 -> "Có mây"
        45, 48 -> "Sương mù"
        51, 53, 55, 61, 63, 65 -> "Có mưa"
        80, 81, 82 -> "Mưa rào"
        95, 96, 99 -> "Giông"
        else -> "Mát mẻ"
    }
}

