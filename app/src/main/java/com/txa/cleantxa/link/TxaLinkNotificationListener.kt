package com.txa.cleantxa.link

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Chạy trên ĐIỆN THOẠI: khi bật "Thông báo trên TV", mọi thông báo mới (tin nhắn, cuộc gọi đến, app…)
 * được đẩy sang các TV đã tin cậy để hiện banner — giống Microsoft Phone Link.
 */
class TxaLinkNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recent = HashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        if (!TxaLinkStore.phanChieuThongBao(this)) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0 && n.category != Notification.CATEGORY_CALL) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        // Chặn lặp: cùng nội dung trong 3 giây chỉ gửi một lần (app hay cập nhật thông báo liên tục).
        val key = "${sbn.packageName}|$title|$text"
        val now = System.currentTimeMillis()
        synchronized(recent) {
            if (now - (recent[key] ?: 0L) < 3_000) return
            recent[key] = now
            if (recent.size > 50) recent.entries.removeAll { now - it.value > 10_000 }
        }

        val appName = runCatching {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val isCall = n.category == Notification.CATEGORY_CALL

        val tvs = TxaLinkStore.dsTvTinCay(this).filter { it.lastIp.isNotBlank() }
        tvs.forEach { tv ->
            scope.launch {
                val sent = runCatching {
                    TxaLinkClient(tv.lastIp, tv.port, tv.token, tv.tvId)
                        .guiThongBaoDienThoai(appName, title, text, isCall)
                }.isSuccess
                if (!sent) guiLaiSauKhiDoLaiTv(tv.tvId, appName, title, text, isCall)
            }
        }
    }

    /** TV đổi IP (DHCP): dò lại mạng để cập nhật IP đã lưu rồi gửi lại, tối đa 1 lần dò mỗi 30 giây. */
    private suspend fun guiLaiSauKhiDoLaiTv(tvId: String, app: String, title: String, text: String, isCall: Boolean) {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastRediscover < 30_000) return
            lastRediscover = now
        }
        runCatching { TxaLinkClient.discoverLan(applicationContext, ipDienThoai()) }
        val tv = TxaLinkStore.timTvTinCay(this, tvId) ?: return
        runCatching {
            TxaLinkClient(tv.lastIp, tv.port, tv.token, tv.tvId).guiThongBaoDienThoai(app, title, text, isCall)
        }
    }

    private fun ipDienThoai(): String? = runCatching {
        val cm = getSystemService(android.net.ConnectivityManager::class.java)
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        lp.linkAddresses.map { it.address }.filterIsInstance<java.net.Inet4Address>().firstOrNull()?.hostAddress
    }.getOrNull()

    @Volatile private var lastRediscover = 0L

    companion object {
        fun daCapQuyen(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty()
            val me = ComponentName(ctx, TxaLinkNotificationListener::class.java).flattenToString()
            return flat.split(':').any { it == me }
        }

        fun moCaiDatQuyen(ctx: Context) {
            val i = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }
}
