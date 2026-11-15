package com.txa.cleantxa

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cập nhật qua mạng từ GitHub Releases của CleanTxa (`TXAVL/CleanTxa`):
 *  - dữ liệu (danh sách mẫu tivi, APK Home sạch) nếu có `cap-nhat.json` đính kèm release;
 *  - bản app mới: đọc tag & changelog trực tiếp từ GitHub Release, tải APK rồi mở hộp cài của Android.
 */
object CapNhat {
    const val REPO = "TXAVL/CleanTxa"
    const val RELEASE_API = "https://api.github.com/repos/$REPO/releases/latest"

    data class BanMoi(
        val versionCode: Int,
        val versionName: String,
        val apk: String,
        val ghiChuVi: String,
        val ghiChuEn: String = ghiChuVi,
    ) {
        val ghiChu: String get() = t(ghiChuVi, ghiChuEn)
    }

    /** File dữ liệu đã tải về (nếu có) — DuLieu và launcher đọc file này trước assets. */
    fun fileDuLieu(ctx: Context, ten: String) = File(ctx.filesDir, "ota/$ten")

    /**
     * App vừa lên bản mới → dữ liệu OTA tải lúc trước có thể cũ hơn đồ đóng sẵn trong bản mới.
     * Xoá đi, dùng đồ đóng sẵn cho tới lần tải kế.
     */
    fun donOtaCuNeuDoiBan(ctx: Context) {
        val p = ctx.getSharedPreferences("ota", Context.MODE_PRIVATE)
        if (p.getInt("cho_ban", -1) == BuildConfig.VERSION_CODE) return
        File(ctx.filesDir, "ota").deleteRecursively()
        p.edit().clear().putInt("cho_ban", BuildConfig.VERSION_CODE).apply()
    }

    fun moAsset(ctx: Context, ten: String): InputStream {
        val f = fileDuLieu(ctx, ten)
        return if (f.isFile && f.length() > 0) f.inputStream() else ctx.assets.open(ten)
    }

    private fun tai(url: String, giay: Int = 20, accept: String? = null): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = giay * 1000
        c.readTimeout = giay * 1000
        c.setRequestProperty("Cache-Control", "no-cache")
        c.setRequestProperty("User-Agent", "CleanTxa/${BuildConfig.VERSION_NAME}")
        if (accept != null) c.setRequestProperty("Accept", accept)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode} $url")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    /**
     * Đọc GitHub Releases API mới nhất, kéo dữ liệu OTA nếu có `cap-nhat.json` đính kèm.
     * Trả về bản app mới nếu có (version mới hơn bản đang chạy).
     */
    fun kiemTra(ctx: Context): BanMoi? {
        val rel = JSONObject(String(tai(RELEASE_API, accept = "application/vnd.github+json"), Charsets.UTF_8))
        val assets = rel.optJSONArray("assets")
        var otaUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name") == "cap-nhat.json") {
                    otaUrl = a.optString("browser_download_url").takeIf { it.startsWith("http") }
                    break
                }
            }
        }
        if (otaUrl != null) {
            runCatching {
                val j = JSONObject(String(tai(otaUrl), Charsets.UTF_8))
                val prefs = ctx.getSharedPreferences("ota", Context.MODE_PRIVATE)
                j.optJSONObject("duLieu")?.let { dl ->
                    for (ten in dl.keys()) {
                        val o = dl.getJSONObject(ten)
                        if (o.optInt("tuBan", 0) < BuildConfig.VERSION_CODE) {
                            fileDuLieu(ctx, ten).delete()
                            continue
                        }
                        val phienBan = o.getString("phienBan")
                        if (prefs.getString(ten, "") == phienBan && fileDuLieu(ctx, ten).isFile) continue
                        runCatching {
                            val u = o.getString("url")
                            val bytes = tai(u, 60)
                            if (bytes.size.toLong() != o.optLong("co", bytes.size.toLong())) error("Sai cỡ $ten")
                            val f = fileDuLieu(ctx, ten).apply { parentFile?.mkdirs() }
                            val tam = File(f.path + ".tam")
                            tam.writeBytes(bytes)
                            if (!tam.renameTo(f)) error("Không ghi được $ten")
                            prefs.edit().putString(ten, phienBan).apply()
                        }
                    }
                }
            }
        }
        return banTuRelease(rel)
    }

    /** Chỉ đọc bản app mới (không kéo dữ liệu) — cho việc kiểm tra chạy nền. */
    fun kiemTraApp(): BanMoi? = banTuRelease(
        JSONObject(String(tai(RELEASE_API, accept = "application/vnd.github+json"), Charsets.UTF_8))
    )

    internal fun banTuRelease(rel: JSONObject): BanMoi? {
        if (rel.optBoolean("draft", false)) return null
        val ver = rel.optString("tag_name").trim().removePrefix("v").removePrefix("V")
        if (ver.isBlank()) return null
        if (soSanhPhienBan(ver, BuildConfig.VERSION_NAME) <= 0) return null
        val assets = rel.optJSONArray("assets") ?: return null
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = a.optString("browser_download_url").takeIf { it.startsWith("http") }
                break
            }
        }
        val url = apkUrl ?: return null
        val code = maPhienBan(ver).coerceAtLeast(BuildConfig.VERSION_CODE + 1)
        val (vi, en) = tachGhiChuSongNgu(rel.optString("body"))
        return BanMoi(
            versionCode = code,
            versionName = ver,
            apk = url,
            ghiChuVi = vi,
            ghiChuEn = en,
        )
    }

    /**
     * Tách changelog song ngữ từ GitHub Release body theo chuẩn `---`
     * (trên là tiếng Việt, dưới `---` là tiếng Anh).
     */
    internal fun tachGhiChuSongNgu(body: String): Pair<String, String> {
        val raw = body.replace("\r\n", "\n").trim()
        if (raw.isEmpty()) return "" to ""
        val parts = raw.split(Regex("(?m)^\\s*---\\s*$"), limit = 2)
        val vi = parts[0].trim()
        val en = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() } ?: vi
        return vi to en
    }

    internal fun soSanhPhienBan(a: String, b: String): Int {
        val pa = a.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val pb = b.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    private fun maPhienBan(v: String): Int {
        val p = v.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        return p.getOrElse(0) { 0 } * 10000 + p.getOrElse(1) { 0 } * 100 + p.getOrElse(2) { 0 }
    }

    /** Người dùng chạm thông báo "có bản mới" để mở app → tải và cài luôn, khỏi hỏi lại. */
    @Volatile
    var moTuThongBao = false

    /** Tải APK bản mới vào cache với callback tiến trình (bytes đã tải, tổng bytes). */
    fun taiApk(ctx: Context, ban: BanMoi, onProgress: (Long, Long) -> Unit = { _, _ -> }): File {
        val f = File(ctx.cacheDir, "cap-nhat/CleanTxa-${ban.versionName}.apk").apply { parentFile?.mkdirs() }
        val tam = File(f.path + ".tam")
        val c = URL(ban.apk).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 120_000
        c.setRequestProperty("Cache-Control", "no-cache")
        c.setRequestProperty("User-Agent", "CleanTxa/${BuildConfig.VERSION_NAME}")
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode} ${ban.apk}")
            val tong = c.contentLengthLong
            var daTai = 0L
            c.inputStream.use { inp ->
                tam.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = inp.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        daTai += n
                        onProgress(daTai, tong)
                    }
                }
            }
            if (f.exists()) f.delete()
            if (!tam.renameTo(f)) error("Không lưu được file APK")
            return f
        } finally {
            c.disconnect()
        }
    }

    /** Mở hộp cài đặt APK của Android từ file đã tải. */
    fun moCaiApk(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /** Tải APK bản mới vào cache rồi mở hộp cài của Android. */
    fun taiVaCai(ctx: Context, ban: BanMoi) {
        moCaiApk(ctx, taiApk(ctx, ban))
    }
}
