package com.txa.cleantxa

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object ManHinh {
    var laTv = false
        private set

    fun doc(ctx: Context) {
        val um = ctx.getSystemService(UiModeManager::class.java)
        laTv = um?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}

