package com.txa.cleantxa

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * App ghi cấu hình cho CleanTxa Home bằng khoá nào thì launcher phải đọc đúng khoá đó, nếu lệch tên thì tuỳ chọn trong app
 * không có tác dụng gì trên tivi. Test chạy với thư mục làm việc là app/.
 */
class KhoaCauHinhLauncherTest {
    private val launcher = File("../launcher/src/main/java/com/gathin/tvhome/ConfigStore.kt").readText(Charsets.UTF_8)
    private val app = File("src/main/java/com/txa/cleantxa/ui/PhienLamViec.kt").readText(Charsets.UTF_8)

    private val khoaCuaCleanTxaHome = listOf(
        "show_quick_actions", "show_recent", "show_inputs", "show_kids_button",
        "show_now_playing", "show_input_button", "columns",
        "show_clock", "show_clock_seconds", "show_date", "clock_24h", "show_lunar_calendar",
        "tile_scale", "tile_corner_style", "accent_color",
        "show_wifi_button", "show_settings_button", "show_network_ip",
        "recents_button",
    )

    @Test
    fun appGhiDungCacKhoaMaLauncherDoc() {
        val thieuTrongApp = khoaCuaCleanTxaHome.filter { !app.contains("put(\"$it\"") }
        val thieuTrongLauncher = khoaCuaCleanTxaHome.filter { !launcher.contains("\"$it\"") }
        assertTrue("App không ghi khoá: $thieuTrongApp", thieuTrongApp.isEmpty())
        assertTrue("Launcher không đọc khoá: $thieuTrongLauncher", thieuTrongLauncher.isEmpty())
    }
}
