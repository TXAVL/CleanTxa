package com.txa.cleantxa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapNhatTest {
    @Test
    fun tachGhiChuSongNgu_tachDungViVaEn() {
        val body = """
            - Khoá trẻ em có thêm **khoá từng app**.
            - Cập nhật giao diện mới.

            ---
            - Kids lock can now **lock single apps**.
            - Updated UI.
        """.trimIndent()

        val (vi, en) = CapNhat.tachGhiChuSongNgu(body)
        assertTrue(vi.contains("khoá từng app"))
        assertTrue(en.contains("lock single apps"))
    }

    @Test
    fun tachGhiChuSongNgu_khongCoGachNgangThiDungChung() {
        val (vi, en) = CapNhat.tachGhiChuSongNgu("- Sửa lỗi kết nối")
        assertEquals("- Sửa lỗi kết nối", vi)
        assertEquals("- Sửa lỗi kết nối", en)
    }

    @Test
    fun soSanhPhienBan_dungThuTu() {
        assertTrue(CapNhat.soSanhPhienBan("1.2.0", "1.1.0") > 0)
        assertEquals(0, CapNhat.soSanhPhienBan("1.1.0", "1.1.0"))
        assertTrue(CapNhat.soSanhPhienBan("1.0.9", "1.1.0") < 0)
    }
}

