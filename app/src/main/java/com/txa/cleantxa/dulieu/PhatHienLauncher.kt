package com.txa.cleantxa.dulieu

import com.txa.cleantxa.adb.ThaoTacTv

/**
 * Nhận ra launcher (màn Home) đang chạy trên tivi: có phải một trong các launcher của app không, và tivi còn cài
 * launcher nào khác. Chỉ là phép phân loại từ dữ liệu đã đọc (Home hiện tại + các gói có màn HOME), không gọi tivi.
 */
object PhatHienLauncher {
    /** Các gói có màn HOME nhưng không phải launcher thật (trình cài đặt, Cài đặt, chính CleanTxa) nên không báo. */
    private val BO_QUA = setOf(
        ThaoTacTv.SETUP, "com.android.tv.settings", "com.android.settings", "com.android.provision", "com.txa.cleantxa", "android",
    )

    data class KetQua(
        /** Gói Home tivi đang dùng ("" nếu không đọc được). */
        val homeHienTai: String,
        /** Home đang dùng là một launcher của app (CleanTxa Home, Home sạch CleanYourTV, bản cho Xiaomi). */
        val laCuaApp: Boolean,
        /** Tivi đang dùng launcher không phải của app. */
        val dangDungLauncherKhac: Boolean,
        /** Launcher khác (không phải của app) có trên tivi, trừ cái đang dùng: có thể giành nút Home. */
        val launcherKhacConLai: List<String>,
    )

    fun phanLoai(homeHienTai: String, coHome: Set<String>): KetQua {
        val home = homeHienTai.substringBefore('/').trim()
        val laCuaApp = home in ThaoTacTv.HOME_SACH
        val dangKhac = home.isNotEmpty() && !laCuaApp && home !in BO_QUA
        val conLai = coHome.filter { it !in ThaoTacTv.HOME_SACH && it !in BO_QUA && it != home }.distinct().sorted()
        return KetQua(home, laCuaApp, dangKhac, conLai)
    }
}
