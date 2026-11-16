package com.txa.cleantxa.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GiayPhepTest {
    // Test chạy với thư mục làm việc là app/.
    private fun doc(ten: String) = File("src/main/assets/$ten").readText(Charsets.UTF_8)

    @Test
    fun tenFile_theoNgonNgu() {
        assertEquals("giay-phep-vi.txt", tenFileGiayPhep(true))
        assertEquals("giay-phep-en.txt", tenFileGiayPhep(false))
    }

    @Test
    fun tach_dienPhienBanVaTachHaiPhan() {
        val (gioiThieu, toanVan) = tachGiayPhep("CleanTxa {{VERSION}}\nabc\n$DAU_TACH_GIAY_PHEP\nLICENSE TEXT", "9.9.9")
        assertEquals("CleanTxa 9.9.9\nabc", gioiThieu)
        assertEquals("LICENSE TEXT", toanVan)
    }

    @Test
    fun cacFileGiayPhep_khongCoLinkGitVaDayDuPhan() {
        for (ten in listOf("giay-phep-vi.txt", "giay-phep-en.txt")) {
            val s = doc(ten)
            assertFalse("$ten có link git", Regex("github|gitlab|\\.git\\b", RegexOption.IGNORE_CASE).containsMatchIn(s))
            assertTrue("$ten thiếu dấu tách", s.contains(DAU_TACH_GIAY_PHEP))
            assertTrue("$ten thiếu số phiên bản", s.contains("{{VERSION}}"))
            assertTrue("$ten thiếu Bouncy Castle", s.contains("Bouncy Castle"))
            assertFalse("$ten còn mục font không dùng", s.contains("Lexend"))
        }
        assertTrue(doc("giay-phep-vi.txt").contains("Giấy phép Apache"))
        assertTrue(doc("giay-phep-en.txt").contains("Apache License"))
    }

    @Test
    fun linkDuyNhatLaCuaChinhGiayPhepApache() {
        for (ten in listOf("giay-phep-vi.txt", "giay-phep-en.txt")) {
            val links = Regex("https?://\\S+").findAll(doc(ten)).map { it.value }.toList()
            assertTrue("$ten: $links", links.all { it.startsWith("http://www.apache.org/licenses") })
        }
    }
}
