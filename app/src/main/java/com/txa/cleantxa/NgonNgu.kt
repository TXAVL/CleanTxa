package com.txa.cleantxa

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

object NgonNgu {
    var tiengViet by mutableStateOf(true)
        private set

    fun nap(ctx: Context) {
        val p = ctx.getSharedPreferences("cleantxa", Context.MODE_PRIVATE)
        tiengViet = when (p.getString("lang", "")) {
            "vi" -> true
            "en" -> false
            else -> Locale.getDefault().language == "vi"
        }
    }

    fun doi(ctx: Context) {
        tiengViet = !tiengViet
        ctx.getSharedPreferences("cleantxa", Context.MODE_PRIVATE).edit()
            .putString("lang", if (tiengViet) "vi" else "en").apply()
    }
}

fun t(vi: String, en: String): String = if (NgonNgu.tiengViet) vi else en

