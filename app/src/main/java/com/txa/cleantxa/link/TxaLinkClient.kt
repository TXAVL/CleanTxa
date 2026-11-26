package com.txa.cleantxa.link

import android.content.Context
import android.util.Base64
import com.txa.cleantxa.dulieu.ThongTinTv
import com.txa.cleantxa.dulieu.TrangThaiGoi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder

/**
 * Máy khách trên Điện thoại: tự dò tìm TV chạy ngầm trong mạng Wi-Fi, xác thực tin cậy 1 lần,
 * và gửi mọi thao tác điều khiển / đổi giao diện Home sạch qua cổng 2312 ngay cả khi ADB đã tắt.
 */
class TxaLinkClient(
    val host: String,
    val port: Int = TxaLinkStore.PORT,
    val token: String,
    val tvId: String = "",
) {
    data class DiscoveredTv(
        val tvId: String,
        val name: String,
        val brand: String,
        val model: String,
        val androidVer: String,
        val ip: String,
        val port: Int,
        val hasA11y: Boolean,
        val trustedToken: String?,
    ) {
        val isTrusted: Boolean get() = !trustedToken.isNullOrBlank()
    }

    data class TvLinkStatus(
        val info: ThongTinTv,
        val trangThai: TrangThaiGoi,
        val boNho: Pair<Long, Long>,
        val ram: Pair<Long, Long>,
        val pkgHome: String,
        val hasA11y: Boolean,
        val sleepTimerSec: Long?,
        val appTuCai: Set<String>,
        val volCur: Int = 0,
        val volMax: Int = 15,
        val dimPercent: Int = 0,
        val photoShowing: Boolean = false,
        val activePkg: String = "",
    )

    private val baseUrl get() = "http://$host:$port"

    fun docTrangThaiDayDu(): TvLinkStatus {
        val j = getJson("/status", timeoutMs = 6_000)
        if (!j.optBoolean("ok", false)) error(j.optString("error", "Không đọc được trạng thái TV"))
        val info = ThongTinTv(
            brand = j.optString("brand"),
            manufacturer = j.optString("manufacturer"),
            model = j.optString("model"),
            device = j.optString("device"),
            productName = j.optString("productName"),
            buildProduct = j.optString("buildProduct"),
            android = j.optString("android"),
            sdk = j.optInt("sdk", 0),
            serial = j.optString("tvId").take(12),
            adbSecure = "1",
        )
        fun jsonSet(key: String): Set<String> {
            val a = j.optJSONArray(key) ?: return emptySet()
            return (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
        }
        val tt = TrangThaiGoi(
            caiDat = jsonSet("caiDat"),
            daTat = jsonSet("daTat"),
            coBieuTuong = jsonSet("coBieuTuong"),
            homeHienTai = j.optString("homeHienTai"),
            banPhim = emptySet(),
            coHome = jsonSet("coHome"),
        )
        val sleepSec = j.optLong("sleepTimerEpochSec", 0L).takeIf { it > System.currentTimeMillis() / 1000L }
        return TvLinkStatus(
            info = info,
            trangThai = tt,
            boNho = j.optLong("freeBytes", 0L) to j.optLong("totalBytes", 0L),
            ram = j.optLong("ramFreeBytes", 0L) to j.optLong("ramTotalBytes", 0L),
            pkgHome = j.optString("pkgHome", "com.gathin.tvhome"),
            hasA11y = j.optBoolean("a11y", false),
            sleepTimerSec = sleepSec,
            appTuCai = jsonSet("appTuCai"),
            volCur = j.optInt("volCur", 0),
            volMax = j.optInt("volMax", 15).coerceAtLeast(1),
            dimPercent = j.optInt("dimPercent", 0),
            photoShowing = j.optBoolean("photoShowing", false),
            activePkg = j.optString("activePkg", ""),
        )
    }

    fun guiPhim(code: Int): JSONObject = postJson("/key", JSONObject().put("code", code), timeoutMs = 4_000)

    fun goChu(text: String, enter: Boolean): JSONObject =
        postJson("/text", JSONObject().put("text", text).put("enter", enter), timeoutMs = 5_000)

    fun datAmLuong(level: Int): Pair<Int, Int> {
        val j = postJson("/volume", JSONObject().put("level", level), timeoutMs = 4_000)
        return j.optInt("volCur", level) to j.optInt("volMax", 15).coerceAtLeast(1)
    }

    fun banLinkLenTv(urlOrText: String): JSONObject =
        postJson("/beam/url", JSONObject().put("url", urlOrText), timeoutMs = 6_000)

    fun chieuAnhLenTv(jpegBytes: ByteArray, caption: String = ""): JSONObject {
        val q = if (caption.isNotBlank()) "?caption=" + URLEncoder.encode(caption, "UTF-8") else ""
        return postRaw("/beam/photo$q", jpegBytes, "image/jpeg", timeoutMs = 20_000)
    }

    fun dongAnhTrenTv(): JSONObject = postJson("/beam/photo/close", JSONObject(), timeoutMs = 4_000)

    fun guiThongBaoNoi(message: String, tts: Boolean, seconds: Int = 10): JSONObject =
        postJson("/announce", JSONObject().put("message", message).put("tts", tts).put("seconds", seconds), timeoutMs = 6_000)

    fun guiThongBaoDienThoai(app: String, title: String, text: String, isCall: Boolean): JSONObject =
        postJson(
            "/notify",
            JSONObject().put("app", app).put("title", title).put("text", text).put("call", isCall),
            timeoutMs = 4_000,
        )

    fun docClipboardTv(): String = getJson("/clipboard", timeoutMs = 5_000).optString("text", "")

    fun ghiClipboardTv(text: String, pasteNow: Boolean): JSONObject =
        postJson("/clipboard", JSONObject().put("text", text).put("pasteNow", pasteNow), timeoutMs = 5_000)

    fun datDoToiManHinh(percent: Int): JSONObject =
        postJson("/screen-dim", JSONObject().put("percent", percent), timeoutMs = 5_000)

    fun chupManHinhQuaLink(): ByteArray? {
        val j = getJson("/screenshot", timeoutMs = 10_000)
        if (!j.optBoolean("ok", false)) return null
        val b64 = j.optString("jpegB64", "")
        return if (b64.isBlank()) null else runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
    }

    fun henTat(phut: Int): Long? {
        val j = postJson("/sleep-timer", JSONObject().put("minutes", phut), timeoutMs = 5_000)
        return j.optLong("sleepTimerEpochSec", 0L).takeIf { it > 0L }
    }

    fun giaiPhongRam(): Pair<Long, Pair<Long, Long>> {
        val j = postJson("/free-ram", JSONObject(), timeoutMs = 8_000)
        return j.optLong("freedBytes", 0L) to (j.optLong("freeBytes", 0L) to j.optLong("totalBytes", 0L))
    }

    fun moApp(pkg: String): Boolean =
        postJson("/app/open", JSONObject().put("pkg", pkg), timeoutMs = 5_000).optBoolean("ok", false)

    fun goApp(pkg: String): Boolean =
        postJson("/app/uninstall", JSONObject().put("pkg", pkg), timeoutMs = 5_000).optBoolean("ok", false)

    fun caiApk(apkBytes: ByteArray): Boolean =
        postRaw("/app/install", apkBytes, "application/vnd.android.package-archive", timeoutMs = 60_000).optBoolean("ok", false)

    fun docCauHinhHome(): Pair<JSONObject?, ByteArray?> {
        val j = getJson("/home/config", timeoutMs = 8_000)
        val cfg = j.optString("config").takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
        val wp = j.optString("wallpaperB64").takeIf { it.isNotBlank() }?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
        return cfg to wp
    }

    fun apDungHome(
        pkgHome: String,
        configJson: String,
        wallpaper: ByteArray?,
        rotating: List<Pair<String, ByteArray>>?,
        channelLogos: List<Pair<String, ByteArray>>?,
    ): JSONObject {
        val body = JSONObject()
            .put("pkgHome", pkgHome)
            .put("config", configJson)
        if (wallpaper != null) {
            body.put("wallpaperB64", Base64.encodeToString(wallpaper, Base64.NO_WRAP))
        }
        if (!rotating.isNullOrEmpty()) {
            val arr = JSONArray()
            rotating.forEach { (name, bytes) ->
                arr.put(JSONObject().put("name", name).put("b64", Base64.encodeToString(bytes, Base64.NO_WRAP)))
            }
            body.put("rotatingWallpapers", arr)
        }
        if (!channelLogos.isNullOrEmpty()) {
            val arr = JSONArray()
            channelLogos.forEach { (name, bytes) ->
                arr.put(JSONObject().put("name", name).put("b64", Base64.encodeToString(bytes, Base64.NO_WRAP)))
            }
            body.put("channelLogos", arr)
        }
        return postJson("/home/apply", body, timeoutMs = 30_000)
    }

    private fun getJson(path: String, timeoutMs: Int): JSONObject {
        val c = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("X-Txa-Token", token)
        }
        try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code == 401) error("TV chưa tin cậy điện thoại này (mã uỷ quyền không khớp). Hãy bấm Ghép liên kết lại.")
            return JSONObject(text.ifBlank { "{}" })
        } finally {
            c.disconnect()
        }
    }

    private fun postJson(path: String, body: JSONObject, timeoutMs: Int): JSONObject =
        postRaw(path, body.toString().toByteArray(Charsets.UTF_8), "application/json; charset=utf-8", timeoutMs)

    private fun postRaw(path: String, bytes: ByteArray, contentType: String, timeoutMs: Int): JSONObject {
        val c = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("X-Txa-Token", token)
            setFixedLengthStreamingMode(bytes.size)
        }
        try {
            c.outputStream.use { it.write(bytes) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code == 401) error("TV chưa tin cậy điện thoại này. Hãy bấm Ghép liên kết lại.")
            return JSONObject(text.ifBlank { "{}" })
        } finally {
            c.disconnect()
        }
    }

    companion object {
        /** Thử gọi /ping tới một IP trên cổng 2312. */
        fun pingHost(ctx: Context, ip: String, port: Int = TxaLinkStore.PORT, timeoutMs: Int = 600): DiscoveredTv? = runCatching {
            val c = (URL("http://$ip:$port/ping").openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
            }
            try {
                if (c.responseCode != 200) return null
                val j = JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                if (j.optString("magic") != TxaLinkStore.DISCOVER_MAGIC) return null
                parseDiscovered(ctx, j, ip)
            } finally {
                c.disconnect()
            }
        }.getOrNull()

        private fun parseDiscovered(ctx: Context, j: JSONObject, ip: String): DiscoveredTv {
            val tvId = j.optString("tvId")
            val trusted = TxaLinkStore.timTvTinCay(ctx, tvId)?.also {
                TxaLinkStore.capNhatIpTv(ctx, tvId, ip, j.optInt("port", TxaLinkStore.PORT))
            }
            return DiscoveredTv(
                tvId = tvId,
                name = j.optString("name", "Android TV"),
                brand = j.optString("brand", ""),
                model = j.optString("model", ""),
                androidVer = j.optString("android", ""),
                ip = ip,
                port = j.optInt("port", TxaLinkStore.PORT),
                hasA11y = j.optBoolean("a11y", false),
                trustedToken = trusted?.token,
            )
        }

        /**
         * Dò tự động toàn bộ TV đang chạy ngầm CleanTxa Link trên cùng mạng Wi-Fi:
         *  1) Hỏi thẳng các IP của TV đã tin cậy (nhanh < 300ms)
         *  2) Phát gói UDP Broadcast tới 255.255.255.255:2312 và <subnet>.255:2312
         *  3) Quét song song cổng 2312 trên dải mạng /24 (bắt được cả khi router chặn broadcast/mDNS)
         */
        suspend fun discoverLan(ctx: Context, phoneIp: String?): List<DiscoveredTv> = coroutineScope {
            val found = LinkedHashMap<String, DiscoveredTv>()

            // Bước 1: Kiểm tra ngay các TV đã tin cậy ở lần trước
            val savedJobs = TxaLinkStore.dsTvTinCay(ctx)
                .filter { it.lastIp.isNotBlank() }
                .map { tv -> async(Dispatchers.IO) { pingHost(ctx, tv.lastIp, tv.port, 450) } }
            savedJobs.awaitAll().filterNotNull().forEach { found[it.tvId] = it }

            // Bước 2: Phát UDP Broadcast
            withContext(Dispatchers.IO) {
                runCatching {
                    DatagramSocket().use { ds ->
                        ds.broadcast = true
                        ds.soTimeout = 450
                        val msg = TxaLinkStore.DISCOVER_MAGIC.toByteArray(Charsets.UTF_8)
                        val targets = buildList {
                            add("255.255.255.255")
                            phoneIp?.substringBeforeLast('.')?.let { add("$it.255") }
                        }.distinct()
                        targets.forEach { addr ->
                            runCatching {
                                ds.send(DatagramPacket(msg, msg.size, InetAddress.getByName(addr), TxaLinkStore.PORT))
                            }
                        }
                        val buf = ByteArray(2048)
                        val deadline = System.currentTimeMillis() + 500L
                        while (System.currentTimeMillis() < deadline) {
                            val pkt = DatagramPacket(buf, buf.size)
                            if (runCatching { ds.receive(pkt) }.isFailure) break
                            val j = runCatching { JSONObject(String(pkt.data, 0, pkt.length, Charsets.UTF_8)) }.getOrNull() ?: continue
                            val hostIp = pkt.address?.hostAddress ?: continue
                            if (j.optString("magic") == TxaLinkStore.DISCOVER_MAGIC) {
                                val d = parseDiscovered(ctx, j, hostIp)
                                found[d.tvId] = d
                            }
                        }
                    }
                }
            }

            // Bước 3: Quét nhanh cổng TCP 2312 trong mạng /24 nếu cần tìm TV đổi IP
            if (phoneIp != null && phoneIp.count { it == '.' } == 3) {
                val prefix = phoneIp.substringBeforeLast('.')
                val sem = Semaphore(64)
                val sweep = (1..254).map { i ->
                    async(Dispatchers.IO) {
                        val candidate = "$prefix.$i"
                        if (candidate == phoneIp || found.values.any { it.ip == candidate }) return@async null
                        sem.withPermit {
                            val open = runCatching {
                                Socket().use { it.connect(InetSocketAddress(candidate, TxaLinkStore.PORT), 240) }; true
                            }.getOrDefault(false)
                            if (open) pingHost(ctx, candidate, TxaLinkStore.PORT, 600) else null
                        }
                    }
                }
                sweep.awaitAll().filterNotNull().forEach { found[it.tvId] = it }
            }

            found.values.sortedWith(compareByDescending<DiscoveredTv> { it.isTrusted }.thenBy { it.name })
        }

        /** Gửi yêu cầu liên kết tin cậy kèm mã PIN 4 số và chờ người dùng bấm Cho phép trên TV (tối đa 60 giây). */
        suspend fun pairWithTv(ctx: Context, tv: DiscoveredTv, pinCode: String): TxaLinkStore.TrustedTv = withContext(Dispatchers.IO) {
            val reqBody = JSONObject()
                .put("phoneId", TxaLinkStore.deviceId(ctx))
                .put("phoneName", TxaLinkStore.deviceName())
                .put("pin", pinCode)
                .toString()
                .toByteArray(Charsets.UTF_8)

            val reqConn = (URL("http://${tv.ip}:${tv.port}/pair/request").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val startJson = try {
                reqConn.outputStream.use { it.write(reqBody) }
                JSONObject(reqConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            } finally {
                reqConn.disconnect()
            }

            if (startJson.optString("status") == "approved") {
                val tok = startJson.getString("token")
                return@withContext TxaLinkStore.TrustedTv(tv.tvId, tv.name, tv.model, tv.ip, tv.port, tok, System.currentTimeMillis())
                    .also { TxaLinkStore.luuTvTinCay(ctx, it) }
            }

            val reqId = startJson.optString("requestId")
            if (reqId.isBlank()) error("TV từ chối yêu cầu ghép đôi.")

            val deadline = System.currentTimeMillis() + 60_000L
            while (System.currentTimeMillis() < deadline) {
                delay(1_000L)
                val pollConn = (URL("http://${tv.ip}:${tv.port}/pair/poll?id=$reqId").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4_000
                    readTimeout = 4_000
                }
                val pollJson = try {
                    JSONObject(pollConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                } finally {
                    pollConn.disconnect()
                }
                when (pollJson.optString("status")) {
                    "approved" -> {
                        val tok = pollJson.getString("token")
                        return@withContext TxaLinkStore.TrustedTv(tv.tvId, tv.name, tv.model, tv.ip, tv.port, tok, System.currentTimeMillis())
                            .also { TxaLinkStore.luuTvTinCay(ctx, it) }
                    }
                    "rejected" -> error("Người dùng trên TV đã bấm Từ chối liên kết.")
                    "expired" -> error("Hết thời gian chờ xác nhận trên TV.")
                }
            }
            error("Hết thời gian chờ bấm Tin tưởng trên màn hình TV.")
        }
    }
}
