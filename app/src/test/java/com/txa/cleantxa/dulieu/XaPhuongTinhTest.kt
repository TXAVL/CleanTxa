package com.txa.cleantxa.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XaPhuongTinhTest {
    private val hn = XaPhuong.Tinh("Thành phố Hà Nội", 21.0285, 105.8542)
    private val ag = XaPhuong.Tinh("Tỉnh An Giang", 9.9784, 104.3139)
    private val xa = listOf(
        XaPhuong.Xa("Phường Hoàn Kiếm", hn.ten, 21.0285, 105.8522),
        XaPhuong.Xa("Phường Ba Đình", hn.ten, 21.0340, 105.8142),
        XaPhuong.Xa("Xã An Biên", ag.ten, 9.7786, 105.0947),
    )

    @Test
    fun tenNgan_boChuTinhVaThanhPho() {
        assertEquals("Hà Nội", hn.tenNgan)
        assertEquals("An Giang", ag.tenNgan)
    }

    @Test
    fun timTinh_khongDauVaDeTrongThiTraTatCa() {
        val ds = listOf(hn, ag)
        assertEquals(listOf(hn), XaPhuong.timTinh(ds, "ha noi"))
        assertEquals(listOf(ag), XaPhuong.timTinh(ds, "AN GIANG"))
        assertEquals(ds, XaPhuong.timTinh(ds, "  "))
    }

    @Test
    fun xaCuaTinh_chiLayDungTinhVaXepTheoTen() {
        assertEquals(listOf("Phường Ba Đình", "Phường Hoàn Kiếm"), XaPhuong.xaCuaTinh(xa, hn).map { it.ten })
        assertEquals(listOf("Xã An Biên"), XaPhuong.xaCuaTinh(xa, ag).map { it.ten })
    }

    @Test
    fun locXa_chiXetTenPhuongXaKhongXetTenTinh() {
        val ds = XaPhuong.xaCuaTinh(xa, hn)
        assertEquals(listOf("Phường Ba Đình"), XaPhuong.locXa(ds, "ba dinh").map { it.ten })
        // Gõ tên tỉnh không làm mọi phường khớp.
        assertTrue(XaPhuong.locXa(ds, "ha noi").isEmpty())
        assertEquals(ds, XaPhuong.locXa(ds, ""))
    }

    @Test
    fun gan_timDungPhuongGanNhatVaBoQuaKhiQuaXa() {
        assertEquals("Phường Hoàn Kiếm", XaPhuong.gan(xa, 21.0286, 105.8521)?.ten)
        assertNotNull(XaPhuong.gan(xa, 9.78, 105.09))
        // Tokyo cách rất xa mọi phường/xã trong danh sách.
        assertNull(XaPhuong.gan(xa, 35.68, 139.69))
    }

    @Test
    fun khoangCach_haDiemGiongNhauBangKhong() {
        assertEquals(0.0, XaPhuong.khoangCachKm(21.0, 105.0, 21.0, 105.0), 1e-9)
        // Hà Nội – TP.HCM khoảng 1.150 km đường chim bay.
        val km = XaPhuong.khoangCachKm(21.0285, 105.8542, 10.7769, 106.7009)
        assertTrue(km in 1100.0..1200.0)
    }
}
