package com.txa.cleantxa.link

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Tự khởi động lại dịch vụ Liên kết ngầm ngay khi tivi bật nguồn hoặc vừa cập nhật phiên bản mới,
 * miễn là người dùng đã mở CleanTxa trên tivi ít nhất 1 lần (hoặc đã ghép tin cậy).
 */
class TxaLinkBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pm = context.packageManager
        val isTv = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        if (!isTv && intent?.action != ACTION_START_LINK) return

        // Cũng hỗ trợ tự tin cậy nhanh từ phiên ADB cục bộ (khi điện thoại đang nối ADB với TV lần đầu).
        if (intent?.action == ACTION_START_LINK) {
            val phoneId = intent.getStringExtra("phone_id")
            val phoneName = intent.getStringExtra("phone_name")
            val token = intent.getStringExtra("token")
            if (!phoneId.isNullOrBlank() && !token.isNullOrBlank()) {
                TxaLinkStore.danhDauDaMoTrenTv(context)
                TxaLinkStore.luuDienThoaiTinCay(
                    context,
                    TxaLinkStore.TrustedPhone(phoneId, phoneName ?: "Phone", token, System.currentTimeMillis()),
                )
            }
        }

        if (TxaLinkStore.daMoTrenTv(context) || TxaLinkStore.dsDienThoaiTinCay(context).isNotEmpty()) {
            TxaLinkService.start(context)
        }
    }

    companion object {
        const val ACTION_START_LINK = "com.txa.cleantxa.START_LINK"
    }
}
