package com.gathin.tvhome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val cfg = ConfigStore.load(context)
        val autoPkg = cfg.openOnStartPkg
        if (autoPkg.isNotBlank() && !cfg.kidsMode) {
            val pm = context.packageManager
            val launch = pm.getLeanbackLaunchIntentForPackage(autoPkg)
                ?: pm.getLaunchIntentForPackage(autoPkg)
            if (launch != null && runCatching {
                    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess) {
                return
            }
        }
        if (cfg.powerOnHomeAlways) {
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
