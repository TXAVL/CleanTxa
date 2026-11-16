package com.txa.cleantxa.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhatHienLauncherTest {
    @Test
    fun launcherCuaApp_nhanRaTungLoai() {
        for (pkg in listOf("com.txa.home", "com.gathin.tvhome", "com.ts.launcher")) {
            val kq = PhatHienLauncher.phanLoai("$pkg/com.gathin.tvhome.MainActivity", setOf(pkg))
            assertTrue(pkg, kq.laCuaApp)
            assertFalse(pkg, kq.dangDungLauncherKhac)
        }
    }

    @Test
    fun launcherKhac_baoVaLietKeCaiConLai() {
        val kq = PhatHienLauncher.phanLoai(
            "com.mitv.tvhome/.MainActivity",
            setOf("com.mitv.tvhome", "com.google.android.tvlauncher", "com.txa.home", "com.android.tv.settings"),
        )
        assertFalse(kq.laCuaApp)
        assertTrue(kq.dangDungLauncherKhac)
        assertEquals("com.mitv.tvhome", kq.homeHienTai)
        // Cài đặt tivi và launcher của app không tính; cái đang dùng cũng không lặp lại.
        assertEquals(listOf("com.google.android.tvlauncher"), kq.launcherKhacConLai)
    }

    @Test
    fun homeLaTrinhCaiDatHoacKhongDoc_khongBaoLauncherKhac() {
        assertFalse(PhatHienLauncher.phanLoai("", emptySet()).dangDungLauncherKhac)
        assertFalse(PhatHienLauncher.phanLoai("com.android.tv.settings/.MainActivity", setOf("com.android.tv.settings")).dangDungLauncherKhac)
    }
}
