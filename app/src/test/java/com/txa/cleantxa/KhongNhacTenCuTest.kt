package com.txa.cleantxa

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chữ hiện cho người dùng không được nhắc "VGS" (tên cũ); khoá nội bộ như vgs.kenh, zone.vgs.* viết thường nên không dính. */
class KhongNhacTenCuTest {
    @Test
    fun khongCoVgsTrongChuHienChoNguoiDung() {
        // Test chạy với thư mục làm việc là app/.
        val loi = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines(Charsets.UTF_8).withIndex().filter { (_, l) ->
                val s = l.trim()
                "VGS" in s && !s.startsWith("//") && !s.startsWith("*") && !s.startsWith("/*")
            }.map { (i, l) -> "${f.name}:${i + 1}: ${l.trim().take(100)}" }
        }.toList()
        assertTrue("Còn chữ VGS hiện cho người dùng:\n" + loi.joinToString("\n"), loi.isEmpty())
    }

    @Test
    fun khongCoVgsTrongFileDuLieuVaGiayPhep() {
        val loi = File("src/main/assets").listFiles { f -> f.extension in setOf("json", "txt") }.orEmpty()
            .filter { "VGS" in it.readText(Charsets.UTF_8) }.map { it.name }
        assertTrue("Còn chữ VGS trong: $loi", loi.isEmpty())
    }
}
