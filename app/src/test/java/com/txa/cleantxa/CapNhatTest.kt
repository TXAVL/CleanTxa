package com.txa.cleantxa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapNhatTest {
    @Test
    fun tachGhiChuSongNgu_tachDungViVaEn() {
        val body = """
            - Khoá trẻ em có thêm **khoá từng app**.
            - Cập nhật giao diện `CleanTxa` mới.<br/>Xem [chi tiết](https://github.com/TXAVL/CleanTxa).

            ---
            - Kids lock can now **lock single apps**.
            - Updated `CleanTxa` UI.
        """.trimIndent()

        val (vi, en) = CapNhat.tachGhiChuSongNgu(body)
        assertTrue(vi.contains("• Khoá trẻ em có thêm khoá từng app."))
        assertTrue(vi.contains("CleanTxa"))
        assertTrue(!vi.contains("**") && !vi.contains("`") && !vi.contains("<br"))
        assertTrue(en.contains("• Kids lock can now lock single apps."))
    }

    @Test
    fun lamSachGhiChu_xuLyHtmlVaMarkdown() {
        val html = "<h3>Cập nhật</h3><ul><li><b>Nhanh hơn</b> &amp; ổn định</li><li>Hỗ trợ <code>tvhome</code></li></ul>"
        val cleaned = CapNhat.lamSachGhiChu(html)
        assertTrue(cleaned.contains("Cập nhật"))
        assertTrue(cleaned.contains("• Nhanh hơn & ổn định"))
        assertTrue(cleaned.contains("• Hỗ trợ tvhome"))
        assertTrue(!cleaned.contains("<") && !cleaned.contains(">"))
    }

    @Test
    fun tachGhiChuSongNgu_khongCoGachNgangThiDungChung() {
        val (vi, en) = CapNhat.tachGhiChuSongNgu("- Sửa lỗi kết nối")
        assertEquals("• Sửa lỗi kết nối", vi)
        assertEquals("• Sửa lỗi kết nối", en)
    }

    @Test
    fun soSanhPhienBan_dungThuTu() {
        assertTrue(CapNhat.soSanhPhienBan("1.2.0", "1.1.0") > 0)
        assertEquals(0, CapNhat.soSanhPhienBan("1.1.0", "1.1.0"))
        assertTrue(CapNhat.soSanhPhienBan("1.0.9", "1.1.0") < 0)
    }
}

