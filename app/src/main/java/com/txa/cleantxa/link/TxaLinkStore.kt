package com.txa.cleantxa.link

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

/**
 * Lưu trữ định danh thiết bị và danh sách tin cậy hai chiều (Điện thoại ↔ Tivi) cho chế độ Liên kết ngầm không cần ADB.
 */
object TxaLinkStore {
    const val PORT = 2312
    const val SERVICE_TYPE = "_cleantxa-link._tcp."
    const val DISCOVER_MAGIC = "CLEANTXA_LINK_DISCOVER_V1"
    private const val PREF_NAME = "txa_link"

    data class TrustedPhone(
        val id: String,
        val name: String,
        val token: String,
        val pairedAt: Long,
    )

    data class TrustedTv(
        val tvId: String,
        val name: String,
        val model: String,
        val lastIp: String,
        val port: Int,
        val token: String,
        val pairedAt: Long,
    )

    data class PendingPairRequest(
        val requestId: String,
        val phoneId: String,
        val phoneName: String,
        val pinCode: String,
        val remoteIp: String,
        val createdAt: Long = System.currentTimeMillis(),
    )

    /** Yêu cầu liên kết đang chờ người dùng bấm "Tin tưởng" trên màn hình tivi. */
    var pendingPair by mutableStateOf<PendingPairRequest?>(null)

    /** Phiên bản trạng thái tin cậy trên TV để giao diện Compose tự vẽ lại khi thêm/xoá máy. */
    var trustRevision by mutableStateOf(0)

    /** Nội dung văn bản / link hoặc ảnh được người dùng bấm "Chia sẻ → CleanTxa" từ ứng dụng khác trên điện thoại. */
    var pendingSharedText by mutableStateOf<String?>(null)
    var pendingSharedImageUri by mutableStateOf<android.net.Uri?>(null)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun deviceId(ctx: Context): String {
        val p = prefs(ctx)
        val existing = p.getString("device_id", null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        p.edit().putString("device_id", created).apply()
        return created
    }

    fun deviceName(): String {
        val maker = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return when {
            maker.isEmpty() -> model.ifEmpty { "Android" }
            model.startsWith(maker, ignoreCase = true) -> model
            else -> "$maker $model"
        }
    }

    fun daMoTrenTv(ctx: Context): Boolean = prefs(ctx).getBoolean("tv_opened_once", false)

    fun danhDauDaMoTrenTv(ctx: Context) {
        prefs(ctx).edit().putBoolean("tv_opened_once", true).apply()
    }

    fun tuKetNoiNgam(ctx: Context): Boolean = prefs(ctx).getBoolean("auto_connect_bg", true)

    fun datTuKetNoiNgam(ctx: Context, bat: Boolean) {
        prefs(ctx).edit().putBoolean("auto_connect_bg", bat).apply()
    }

    /** Phản chiếu thông báo điện thoại lên TV (kiểu Phone Link). */
    fun phanChieuThongBao(ctx: Context): Boolean = prefs(ctx).getBoolean("mirror_notifications", false)

    fun datPhanChieuThongBao(ctx: Context, bat: Boolean) {
        prefs(ctx).edit().putBoolean("mirror_notifications", bat).apply()
    }

    fun taoTokenNgauNhien(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ---------- Phía Tivi: danh sách điện thoại đã tin cậy ----------

    fun dsDienThoaiTinCay(ctx: Context): List<TrustedPhone> {
        val raw = prefs(ctx).getString("trusted_phones", "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val tok = o.optString("token")
                if (id.isBlank() || tok.isBlank()) null
                else TrustedPhone(id, o.optString("name", "Phone"), tok, o.optLong("pairedAt", 0L))
            }
        }.getOrDefault(emptyList())
    }

    fun luuDienThoaiTinCay(ctx: Context, phone: TrustedPhone) {
        val list = dsDienThoaiTinCay(ctx).filterNot { it.id == phone.id } + phone
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("token", it.token)
                    .put("pairedAt", it.pairedAt)
            )
        }
        prefs(ctx).edit().putString("trusted_phones", arr.toString()).apply()
        trustRevision++
    }

    fun xoaDienThoaiTinCay(ctx: Context, phoneId: String) {
        val list = dsDienThoaiTinCay(ctx).filterNot { it.id == phoneId }
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("token", it.token)
                    .put("pairedAt", it.pairedAt)
            )
        }
        prefs(ctx).edit().putString("trusted_phones", arr.toString()).apply()
        trustRevision++
    }

    fun kiemTraToken(ctx: Context, token: String?): TrustedPhone? {
        if (token.isNullOrBlank()) return null
        return dsDienThoaiTinCay(ctx).firstOrNull { it.token == token }
    }

    // ---------- Phía Điện thoại: danh sách Tivi đã tin cậy ----------

    fun dsTvTinCay(ctx: Context): List<TrustedTv> {
        val raw = prefs(ctx).getString("trusted_tvs", "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val tvId = o.optString("tvId")
                val tok = o.optString("token")
                if (tvId.isBlank() || tok.isBlank()) null
                else TrustedTv(
                    tvId = tvId,
                    name = o.optString("name", "Android TV"),
                    model = o.optString("model", ""),
                    lastIp = o.optString("lastIp", ""),
                    port = o.optInt("port", PORT),
                    token = tok,
                    pairedAt = o.optLong("pairedAt", 0L),
                )
            }
        }.getOrDefault(emptyList()).sortedByDescending { it.pairedAt }
    }

    fun timTvTinCay(ctx: Context, tvId: String): TrustedTv? =
        dsTvTinCay(ctx).firstOrNull { it.tvId == tvId }

    fun luuTvTinCay(ctx: Context, tv: TrustedTv) {
        val list = dsTvTinCay(ctx).filterNot { it.tvId == tv.tvId } + tv
        ghiDsTvTinCay(ctx, list)
    }

    fun capNhatIpTv(ctx: Context, tvId: String, ip: String, port: Int = PORT) {
        val existing = timTvTinCay(ctx, tvId) ?: return
        if (existing.lastIp == ip && existing.port == port) return
        luuTvTinCay(ctx, existing.copy(lastIp = ip, port = port))
    }

    fun xoaTvTinCay(ctx: Context, tvId: String) {
        ghiDsTvTinCay(ctx, dsTvTinCay(ctx).filterNot { it.tvId == tvId })
    }

    private fun ghiDsTvTinCay(ctx: Context, list: List<TrustedTv>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("tvId", it.tvId)
                    .put("name", it.name)
                    .put("model", it.model)
                    .put("lastIp", it.lastIp)
                    .put("port", it.port)
                    .put("token", it.token)
                    .put("pairedAt", it.pairedAt)
            )
        }
        prefs(ctx).edit().putString("trusted_tvs", arr.toString()).apply()
        trustRevision++
    }
}
