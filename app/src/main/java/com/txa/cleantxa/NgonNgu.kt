package com.txa.cleantxa

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Ngôn ngữ app: mặc định THEO HỆ THỐNG, người dùng có thể ép tiếng Việt hoặc English.
 *
 * Android 13+: lựa chọn được ghi vào "Ngôn ngữ ứng dụng" của hệ thống (Cài đặt > Ứng dụng > CleanTxa > Ngôn ngữ, khai báo
 * trong xml/locales_config.xml), nên hai nơi luôn khớp nhau; đổi bên nào cũng được. Android thấp hơn: lưu trong app.
 */
object NgonNgu {
    enum class Che(val ma: String) { HE_THONG(""), VI("vi"), EN("en") }

    var che by mutableStateOf(Che.HE_THONG)
        private set

    /** Ngôn ngữ đang dùng sau khi đã xét [che]: true = tiếng Việt, false = English. */
    var tiengViet by mutableStateOf(true)
        private set

    private fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences("cleantxa", Context.MODE_PRIVATE)

    private fun cheTuMa(ma: String?) = Che.entries.firstOrNull { it.ma == ma } ?: Che.HE_THONG

    private fun localeManager(ctx: Context): LocaleManager? =
        if (Build.VERSION.SDK_INT >= 33) ctx.getSystemService(LocaleManager::class.java) else null

    /**
     * Gọi khi mở app. [chuyenLuaChonCu]: bản cũ lưu lựa chọn VI/EN trong app; trên Android 13+ chuyển nó sang cài đặt
     * hệ thống đúng một lần (chỉ gọi từ màn hình, vì đặt ngôn ngữ app làm Activity dựng lại).
     */
    fun nap(ctx: Context, chuyenLuaChonCu: Boolean = false) {
        val p = prefs(ctx)
        val lm = localeManager(ctx)
        che = if (lm != null) {
            if (chuyenLuaChonCu && !p.getBoolean("lang_sys", false)) {
                p.edit().putBoolean("lang_sys", true).apply()
                val cu = cheTuMa(p.getString("lang", ""))
                if (cu != Che.HE_THONG) runCatching { lm.applicationLocales = LocaleList.forLanguageTags(cu.ma) }
            }
            val ds = lm.applicationLocales
            if (ds.isEmpty) Che.HE_THONG else if (ds[0].language == "vi") Che.VI else Che.EN
        } else {
            cheTuMa(p.getString("lang", ""))
        }
        capNhat()
    }

    fun chon(ctx: Context, moi: Che) {
        che = moi
        prefs(ctx).edit().putString("lang", moi.ma).apply()
        capNhat()
        localeManager(ctx)?.let { lm ->
            runCatching {
                lm.applicationLocales = if (moi == Che.HE_THONG) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(moi.ma)
            }
        }
    }

    private fun capNhat() {
        tiengViet = when (che) {
            Che.VI -> true
            Che.EN -> false
            Che.HE_THONG -> Locale.getDefault().language == "vi"
        }
    }
}

fun t(vi: String, en: String): String = if (NgonNgu.tiengViet) vi else en
