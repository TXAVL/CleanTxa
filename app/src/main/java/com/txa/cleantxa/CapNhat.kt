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
        val apkName: String = "CleanTxa-$versionName.apk",
        val apkSizeBytes: Long = -1L,
        val publishedAt: String = "",
        val releaseUrl: String = "",
    ) {
        val ghiChu: String get() = t(ghiChuVi, ghiChuEn)
    }

    data class TaiTienTrinh(
        val daTai: Long = 0L,
        val tong: Long = -1L,
        val tocDoBytesPerSec: Long = 0L,
    ) {
        val phanTram: Int? get() = if (tong > 0L) ((daTai * 100L) / tong).toInt().coerceIn(0, 100) else null
        val giayConLai: Long? get() = if (tong > daTai && tocDoBytesPerSec > 0L) (tong - daTai) / tocDoBytesPerSec else null
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
        // Mẫu tivi đã gộp từ cleanyourtv không được mất theo: giữ những mẫu bản đóng sẵn chưa có, nếu không mỗi lần
        // đổi bản app lại gộp lại đúng các mẫu cũ và ghi "Merged N new TV model(s)" như thể mới.
        val giuDuLieu = runCatching { duLieuSauKhiDoiBan(ctx) }.getOrNull()
        File(ctx.filesDir, "ota").deleteRecursively()
        if (giuDuLieu != null) {
            runCatching {
                fileDuLieu(ctx, "du-lieu.json").apply { parentFile?.mkdirs() }.writeText(giuDuLieu, Charsets.UTF_8)
            }
        }
        p.edit().clear().putInt("cho_ban", BuildConfig.VERSION_CODE).apply()
    }

    /** `du-lieu.json` mới = bản đóng sẵn + các mẫu đã tải mà bản đóng sẵn chưa có; null nếu không có gì để giữ. */
    private fun duLieuSauKhiDoiBan(ctx: Context): String? {
        val f = fileDuLieu(ctx, "du-lieu.json")
        if (!f.isFile || f.length() == 0L) return null
        val daTai = JSONObject(f.readText(Charsets.UTF_8))
        val dongGoi = JSONObject(ctx.assets.open("du-lieu.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        return if (themMauChuaCo(dongGoi, daTai) > 0) dongGoi.toString() else null
    }

    /** Thêm vào [dich] các mẫu của [nguon] có id chưa có trong [dich]; trả về số mẫu đã thêm. */
    internal fun themMauChuaCo(dich: JSONObject, nguon: JSONObject): Int {
        val mauDich = dich.getJSONArray("mau")
        val daCo = HashSet<String>()
        for (i in 0 until mauDich.length()) daCo.add(mauDich.getJSONObject(i).optString("id"))
        val mauNguon = nguon.optJSONArray("mau") ?: return 0
        var them = 0
        for (i in 0 until mauNguon.length()) {
            val m = mauNguon.getJSONObject(i)
            val id = m.optString("id")
            if (id.isNotBlank() && daCo.add(id)) {
                mauDich.put(m)
                them++
            }
        }
        return them
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
        var apkName = "CleanTxa-$ver.apk"
        var apkSize = -1L
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = a.optString("browser_download_url").takeIf { it.startsWith("http") }
                if (apkUrl != null) {
                    apkName = name
                    apkSize = a.optLong("size", -1L)
                    break
                }
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
            apkName = apkName,
            apkSizeBytes = apkSize,
            publishedAt = rel.optString("published_at").take(16).replace('T', ' '),
            releaseUrl = rel.optString("html_url"),
        )
    }

    /**
     * Tách changelog song ngữ từ GitHub Release body theo chuẩn `---`
     * (trên là tiếng Việt, dưới `---` là tiếng Anh), đồng thời làm sạch HTML/Markdown thô.
     */
    internal fun tachGhiChuSongNgu(body: String): Pair<String, String> {
        val raw = body.replace("\r\n", "\n").trim()
        if (raw.isEmpty()) return "" to ""
        val parts = raw.split(Regex("(?m)^\\s*(?:---|<hr\\s*/?>)\\s*$"), limit = 2)
        val vi = lamSachGhiChu(parts[0], giuInlineMarkdown = false)
        val en = parts.getOrNull(1)?.let { lamSachGhiChu(it, giuInlineMarkdown = false) }
            ?.takeIf { it.isNotEmpty() } ?: vi
        return vi to en
    }

    /**
     * Làm sạch các thẻ HTML (`<br>`, `<b>`, `<li>`, `&amp;`, …) và cú pháp Markdown block (`###`, `- `, `[a](url)`).
     * Khi `giuInlineMarkdown = false`, bỏ luôn ký tự `**`, `*`, `` ` `` để hiện đẹp ở thông báo / text thường.
     */
    internal fun lamSachGhiChu(raw: String, giuInlineMarkdown: Boolean = false): String {
        var s = raw.replace("\r\n", "\n")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</?(?:p|div|h[1-6]|ul|ol)\\b[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<li\\b[^>]*>", RegexOption.IGNORE_CASE), "\n• ")
            .replace(Regex("</li>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<(?:b|strong)\\b[^>]*>(.*?)</(?:b|strong)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), if (giuInlineMarkdown) "**$1**" else "$1")
            .replace(Regex("<(?:i|em)\\b[^>]*>(.*?)</(?:i|em)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), if (giuInlineMarkdown) "*$1*" else "$1")
            .replace(Regex("<code\\b[^>]*>(.*?)</code>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), if (giuInlineMarkdown) "`$1`" else "$1")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

        // Markdown links [label](url) -> label, headings ### Title -> Title, bullets -/* -> •
        s = s.replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
            .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
            .replace(Regex("(?m)^\\s*[-*+]\\s+"), "• ")

        if (!giuInlineMarkdown) {
            s = s.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                .replace(Regex("__(.+?)__"), "$1")
                .replace(Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)"), "$1")
                .replace(Regex("`([^`\\n]+)`"), "$1")
        }

        return s.lines()
            .map { it.trimEnd() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
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

    private val downloadLock = Any()

    /** Tải APK bản mới vào cache với callback tiến trình (bytes đã tải, tổng bytes, tốc độ bytes/s). */
    fun taiApk(ctx: Context, ban: BanMoi, onProgress: (TaiTienTrinh) -> Unit = {}): File = synchronized(downloadLock) {
        fileApkDaTai(ctx, ban)?.let { cached ->
            onProgress(TaiTienTrinh(cached.length(), cached.length(), 0L))
            return@synchronized cached
        }
        val f = File(ctx.cacheDir, "cap-nhat/CleanTxa-${ban.versionName}.apk").apply { parentFile?.mkdirs() }
        val tam = File(f.path + ".tam")
        val c = URL(ban.apk).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 120_000
        c.setRequestProperty("Cache-Control", "no-cache")
        c.setRequestProperty("User-Agent", "CleanTxa/${BuildConfig.VERSION_NAME}")
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode} ${ban.apk}")
            val tong = c.contentLengthLong.takeIf { it > 0L } ?: ban.apkSizeBytes
            var daTai = 0L
            val startedAt = System.currentTimeMillis()
            var lastNotify = 0L
            onProgress(TaiTienTrinh(0L, tong, 0L))
            c.inputStream.use { inp ->
                tam.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = inp.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        daTai += n
                        val now = System.currentTimeMillis()
                        if (now - lastNotify >= 150L) {
                            val elapsedSec = ((now - startedAt).coerceAtLeast(1L)) / 1000.0
                            val speed = (daTai / elapsedSec).toLong()
                            onProgress(TaiTienTrinh(daTai, tong, speed))
                            lastNotify = now
                        }
                    }
                }
            }
            val elapsedSec = ((System.currentTimeMillis() - startedAt).coerceAtLeast(1L)) / 1000.0
            onProgress(TaiTienTrinh(daTai, tong, (daTai / elapsedSec).toLong()))
            if (f.exists()) f.delete()
            if (!tam.renameTo(f)) {
                runCatching {
                    tam.copyTo(f, overwrite = true)
                    tam.delete()
                }.getOrElse {
                    error("Không lưu được file APK")
                }
            }
            f
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

    /** Đường dẫn file APK của bản mới nếu đã tải sẵn trong cache. */
    fun fileApkDaTai(ctx: Context, ban: BanMoi): File? {
        val f = File(ctx.cacheDir, "cap-nhat/CleanTxa-${ban.versionName}.apk")
        if (!f.isFile || f.length() <= 0L) return null
        if (ban.apkSizeBytes > 0L && f.length() != ban.apkSizeBytes) return null
        return f
    }

    /** Tải ngầm file APK bản mới vào cache nếu chưa có sẵn (dùng cho WorkManager chạy nền). */
    fun taiApkNeuChuaCo(ctx: Context, ban: BanMoi): File? {
        fileApkDaTai(ctx, ban)?.let { return it }
        return runCatching { taiApk(ctx, ban) }
            .onSuccess { TxaLogger.i("CapNhat", "Background pre-downloaded ${it.name} (${it.length()} bytes)") }
            .onFailure { TxaLogger.w("CapNhat", "Background pre-download failed", it) }
            .getOrNull()
    }

    /**
     * Đồng bộ động danh sách mẫu tivi (`examples/`) và APK màn Home sạch mới nhất
     * từ `Gathin23/cleanyourtv` (tối đa mỗi 6 giờ, chạy nền không chặn giao diện).
     */
    fun dongBoTuCleanYourTv(ctx: Context) {
        val prefs = ctx.getSharedPreferences("ota", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong("cleanyourtv_sync_at", 0L)
        if (now - last < 6L * 3600_000L) return
        prefs.edit().putLong("cleanyourtv_sync_at", now).apply()

        // 1. Đồng bộ APK launcher từ release mới nhất của Gathin23/cleanyourtv (nếu có asset khớp)
        runCatching {
            val rel = JSONObject(
                String(
                    tai("https://api.github.com/repos/Gathin23/cleanyourtv/releases/latest", accept = "application/vnd.github+json"),
                    Charsets.UTF_8,
                )
            )
            val tag = rel.optString("tag_name")
            if (tag.isNotBlank() && prefs.getString("cleanyourtv_rel", "") != tag) {
                val assets = rel.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val name = a.optString("name")
                        val url = a.optString("browser_download_url")
                        val targetName = when {
                            name.equals("tvhome.apk", true) -> "tvhome.apk"
                            name.equals("tvhome-xiaomi.apk", true) -> "tvhome-xiaomi.apk"
                            else -> null
                        } ?: continue
                        if (url.startsWith("http")) {
                            val bytes = tai(url, 60)
                            if (bytes.size > 100_000) {
                                val f = fileDuLieu(ctx, targetName).apply { parentFile?.mkdirs() }
                                val tam = File(f.path + ".tam")
                                tam.writeBytes(bytes)
                                if (tam.renameTo(f)) {
                                    TxaLogger.i("Sync", "Updated $targetName ($tag)")
                                }
                            }
                        }
                    }
                }
                prefs.edit().putString("cleanyourtv_rel", tag).apply()
            }
        }

        // 2. Đồng bộ các mẫu tivi mới từ thư mục examples/ của Gathin23/cleanyourtv
        runCatching {
            val arr = org.json.JSONArray(
                String(
                    tai("https://api.github.com/repos/Gathin23/cleanyourtv/contents/examples", accept = "application/vnd.github+json"),
                    Charsets.UTF_8,
                )
            )
            val baseText = moAsset(ctx, "du-lieu.json").bufferedReader().readText()
            val root = JSONObject(baseText)
            val mauArr = root.getJSONArray("mau")
            val existingIds = HashSet<String>()
            for (i in 0 until mauArr.length()) {
                existingIds.add(mauArr.getJSONObject(i).optString("id"))
            }
            var added = 0
            val addedModels = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                if (item.optString("type") != "dir") continue
                val id = item.optString("name").trim()
                if (id.isBlank() || id in existingIds) continue
                val debloatRaw = runCatching {
                    String(tai("https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/examples/$id/debloat.txt", 15), Charsets.UTF_8)
                }.getOrNull() ?: continue
                val goiArr = org.json.JSONArray()
                var currentGroup = ""
                for (line in debloatRaw.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    if (trimmed.startsWith("#")) {
                        currentGroup = trimmed.removePrefix("#").trim()
                        continue
                    }
                    val pkg = trimmed.substringBefore('#').substringBefore(' ').trim()
                    if (pkg.contains('.') && !pkg.contains('/')) {
                        val note = trimmed.substringAfter('#', "").trim()
                        goiArr.put(
                            JSONObject()
                                // Khoá phải khớp DuLieu.docJson (p / g / n), thiếu "p" là "No value for p" lúc đọc tivi.
                                .put("p", pkg)
                                .put("g", currentGroup)
                                .put("n", note)
                        )
                    }
                }
                if (goiArr.length() == 0) continue
                val configObj = runCatching {
                    JSONObject(String(tai("https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/examples/$id/config.json", 10), Charsets.UTF_8))
                }.getOrNull()
                val ten = id.replace('-', ' ').replaceFirstChar { it.uppercase() }
                val mauObj = JSONObject()
                    .put("id", id)
                    .put("ten", ten)
                    .put("header", "")
                    .put("goi", goiArr)
                if (configObj != null) mauObj.put("phim", configObj)
                mauArr.put(mauObj)
                existingIds.add(id)
                addedModels.add("$ten (${goiArr.length()} pkg)")
                added++
                if (added >= 20) break
            }
            // Không lưu file mà chính app không đọc nổi: đọc thử trước khi thay.
            if (added > 0 && runCatching { com.txa.cleantxa.dulieu.DuLieu.docJson(root.toString()) }.isFailure) {
                TxaLogger.w("Sync", "Merged data failed validation, not saving $added model(s)")
                added = 0
            }
            if (added > 0) {
                val f = fileDuLieu(ctx, "du-lieu.json").apply { parentFile?.mkdirs() }
                val tam = File(f.path + ".tam")
                tam.writeText(root.toString(), Charsets.UTF_8)
                if (tam.renameTo(f)) {
                    com.txa.cleantxa.dulieu.DuLieu.lamMoiCache()
                    TxaLogger.i(
                        "Sync",
                        "Merged $added new TV model(s)\n" + addedModels.joinToString("\n") { "• $it" },
                    )
                }
            }
        }
    }

    /** Tải APK bản mới vào cache rồi mở hộp cài của Android. */
    fun taiVaCai(ctx: Context, ban: BanMoi) {
        val cached = fileApkDaTai(ctx, ban) ?: taiApk(ctx, ban)
        moCaiApk(ctx, cached)
    }
}
