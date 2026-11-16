package com.txa.cleantxa.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TenFileTest {
    @Test
    fun cauHinh_batDauBangTenAppVaLamSachModel() {
        assertEquals("cleantxa-sdk-google-atv64-arm64.json", TenFile.cauHinh("sdk_google_atv64_arm64"))
        assertEquals("cleantxa-tivi.json", TenFile.cauHinh(null))
    }

    @Test
    fun kichBanKhoiPhuc_batDauBangTenAppVaGioiHanDoDai() {
        assertEquals("cleantxa-khoi-phuc-sony-bravia-x85j.bat", TenFile.kichBanKhoiPhuc("Sony BRAVIA X85J"))
        assertEquals("cleantxa-khoi-phuc-tivi.bat", TenFile.kichBanKhoiPhuc(null))
        assertTrue(TenFile.kichBanKhoiPhuc("a".repeat(100)).length <= "cleantxa-khoi-phuc-".length + 30 + ".bat".length)
    }

    @Test
    fun khongConTenCu() {
        assertFalse(TenFile.cauHinh("x").contains("cleantv"))
        assertFalse(TenFile.kichBanKhoiPhuc("x").contains("cleantv"))
    }
}
