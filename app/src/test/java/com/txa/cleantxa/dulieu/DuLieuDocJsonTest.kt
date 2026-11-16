package com.txa.cleantxa.dulieu

import org.junit.Assert.assertEquals
import org.junit.Test

class DuLieuDocJsonTest {
    private fun json(goi: String) = """{"mau":[{"id":"x","ten":"X","header":"","goi":[$goi]}],"goi":{}}"""

    @Test
    fun docKhoaChuan_p_g_n() {
        val d = DuLieu.docJson(json("""{"p":"com.a","g":"Quảng cáo","n":"ghi chú"}"""))
        assertEquals(listOf(GoiMau("com.a", "Quảng cáo", "ghi chú")), d.mau[0].goi)
    }

    @Test
    fun docKhoaCuTuBanDongBo_pkg_nhom_ghiChu() {
        // Bản 1.3.0 trở về trước lưu nhầm kiểu này; trước đây đọc là sập "No value for p".
        val d = DuLieu.docJson(json("""{"pkg":"com.b","nhom":"Telemetry","ghiChu":"x"}"""))
        assertEquals(listOf(GoiMau("com.b", "Telemetry", "x")), d.mau[0].goi)
    }

    @Test
    fun dongKhongCoGoi_biBoQuaThayVoSap() {
        val d = DuLieu.docJson(json("""{"g":"nhóm"},{"p":"com.c"},{"pkg":""}"""))
        assertEquals(listOf("com.c"), d.mau[0].goi.map { it.pkg })
    }
}
