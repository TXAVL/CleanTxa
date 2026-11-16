package com.txa.cleantxa.adb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DichVuGoiTest {
    @Test
    fun nhanRaCacDongLoiKhiDichVuGoiKhongChay() {
        assertTrue(ThaoTacTv.laLoiDichVuGoi("cmd: Can't find service: package"))
        assertTrue(ThaoTacTv.laLoiDichVuGoi("Error: Could not access the Package Manager.  Is the system running?"))
        assertTrue(ThaoTacTv.laLoiDichVuGoi("Failure calling service package: Broken pipe"))
        assertTrue(ThaoTacTv.laLoiDichVuGoi("android.os.DeadSystemException"))
    }

    @Test
    fun khongNhamVoiLoiCaiDatThuong() {
        assertFalse(ThaoTacTv.laLoiDichVuGoi("Success"))
        assertFalse(ThaoTacTv.laLoiDichVuGoi("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]"))
        assertFalse(ThaoTacTv.laLoiDichVuGoi("Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]"))
    }
}
