package com.txa.cleantxa.link

import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.txa.cleantxa.MainActivity
import com.txa.cleantxa.R
import com.txa.cleantxa.TxaLogger
import com.txa.cleantxa.adb.ThaoTacTv
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Dịch vụ chạy ngầm trên TV (kiểu Microsoft Phone Link):
 *  - Quảng bá sự hiện diện qua UDP Broadcast (cổng 2312) và mDNS (_cleantxa-link._tcp.)
 *  - Xác thực tin cậy 2 chiều (hiện hộp thoại mã 4 số trên TV cho người dùng bấm Cho phép)
 *  - Nhận lệnh Remote, Gõ tiếng Việt, Cài/Mở/Gỡ App, Dọn RAM và Đổi giao diện Home sạch trực tiếp qua Wi-Fi
 *    ngay cả khi TV đã TẮT hoàn toàn Gỡ lỗi USB / ADB.
 */
class TxaLinkService : Service() {

    private val pool = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    private var nsdManager: NsdManager? = null
    private var nsdRegListener: NsdManager.RegistrationListener? = null
    private var overlayDialog: AlertDialog? = null

    // Các thành phần độc quyền của CleanTxa Link trên màn hình TV
    private var dimOverlayView: View? = null
    @Volatile private var screenDimPercent: Int = 0
    private var photoOverlayView: View? = null
    private var photoBitmap: Bitmap? = null
    private var bannerOverlayView: View? = null
    private val hideBannerRunnable = Runnable { dismissBannerOverlay() }
    private var ttsEngine: TextToSpeech? = null
    @Volatile private var ttsReady: Boolean = false
    @Volatile private var pendingTtsMessage: String? = null

    /** Kết quả xác nhận ghép đôi: requestId -> ("approved" to token) hoặc ("rejected" to ""). */
    private val pairResults = ConcurrentHashMap<String, Pair<String, String>>()

    /** Hẹn giờ ngủ cho TV ngay trong dịch vụ ngầm. */
    @Volatile private var sleepDeadlineSec: Long? = null
    private val sleepRunnable = Runnable {
        sleepDeadlineSec = null
        TxaLinkAccessibilityService.instance?.performRemoteKey(KeyEvent.KEYCODE_SLEEP)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        activeInstance = this
        startForegroundQuietly()
        startServers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) startServers()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        isRunning = false
        if (activeInstance === this) activeInstance = null
        mainHandler.removeCallbacks(sleepRunnable)
        mainHandler.removeCallbacks(hideBannerRunnable)
        mainHandler.post {
            dismissPhotoOverlay()
            dismissBannerOverlay()
            dismissDimOverlay()
        }
        runCatching { ttsEngine?.stop(); ttsEngine?.shutdown() }
        ttsEngine = null
        ttsReady = false
        runCatching { overlayDialog?.dismiss() }
        runCatching { serverSocket?.close() }
        runCatching { udpSocket?.close() }
        nsdRegListener?.let { runCatching { nsdManager?.unregisterService(it) } }
        pool.shutdownNow()
        super.onDestroy()
    }

    private fun startForegroundQuietly() {
        val chId = "txa_link_bg"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(chId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(chId, "CleanTxa TV Link", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                    description = "Background Wi-Fi link with your trusted phone"
                }
            )
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = Notification.Builder(this, chId)
            .setSmallIcon(R.drawable.ic_thong_bao)
            .setContentTitle("CleanTxa Link")
            .setContentText("Sẵn sàng kết nối ngầm với điện thoại tin cậy")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        runCatching { startForeground(NOTIF_ID, notif) }
    }

    private fun startServers() {
        running = true
        // 1. Máy chủ HTTP trên cổng 2312
        pool.execute {
            runCatching {
                val ss = ServerSocket(TxaLinkStore.PORT).also {
                    it.reuseAddress = true
                    serverSocket = it
                }
                registerMdns(TxaLinkStore.PORT)
                TxaLogger.i("TxaLink", "TV Link server listening on port ${TxaLinkStore.PORT}")
                while (running && !ss.isClosed) {
                    val client = runCatching { ss.accept() }.getOrNull() ?: continue
                    pool.execute { handleClient(client) }
                }
            }.onFailure { e ->
                if (running) TxaLogger.w("TxaLink", "HTTP server stopped", e)
            }
        }

        // 2. Bộ trả lời UDP Broadcast trên cổng 2312 để điện thoại tự tìm ra TV ngay cả khi mạng chặn mDNS
        pool.execute {
            runCatching {
                val ds = DatagramSocket(TxaLinkStore.PORT).also {
                    it.reuseAddress = true
                    it.broadcast = true
                    udpSocket = it
                }
                val buf = ByteArray(512)
                while (running && !ds.isClosed) {
                    val pkt = DatagramPacket(buf, buf.size)
                    ds.receive(pkt)
                    val msg = String(pkt.data, 0, pkt.length, Charsets.UTF_8).trim()
                    if (msg.startsWith(TxaLinkStore.DISCOVER_MAGIC)) {
                        val reply = pingJson().toString().toByteArray(Charsets.UTF_8)
                        ds.send(DatagramPacket(reply, reply.size, pkt.address, pkt.port))
                    }
                }
            }
        }
    }

    private fun registerMdns(port: Int) {
        runCatching {
            val nsd = getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = nsd
            val info = NsdServiceInfo().apply {
                serviceName = "CleanTxa-${TxaLinkStore.deviceId(this@TxaLinkService).take(8)}"
                serviceType = TxaLinkStore.SERVICE_TYPE
                setPort(port)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            nsdRegListener = listener
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    // ---------- Xử lý HTTP ----------

    private fun handleClient(sock: Socket) {
        sock.use { s ->
            s.soTimeout = 30_000
            val inp = BufferedInputStream(s.getInputStream())
            val headerLine = readLineUtf8(inp) ?: return
            val parts = headerLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val path = fullPath.substringBefore('?')
            val query = fullPath.substringAfter('?', "")

            val headers = HashMap<String, String>()
            while (true) {
                val line = readLineUtf8(inp) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                }
            }

            val contentLen = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 40_000_000) ?: 0
            val body = ByteArray(contentLen)
            var readTotal = 0
            while (readTotal < contentLen) {
                val n = inp.read(body, readTotal, contentLen - readTotal)
                if (n <= 0) break
                readTotal += n
            }

            // Các đường dẫn công khai dùng cho dò tìm & ghép đôi tin cậy
            if (method == "GET" && path == "/ping") {
                writeJson(s, 200, pingJson())
                return
            }
            if (method == "POST" && path == "/pair/request") {
                val req = runCatching { JSONObject(String(body, 0, readTotal, Charsets.UTF_8)) }.getOrNull()
                if (req == null) { writeJson(s, 400, JSONObject().put("error", "bad_json")); return }
                val res = handlePairRequest(req, s.inetAddress?.hostAddress.orEmpty())
                writeJson(s, 200, res)
                return
            }
            if (method == "GET" && path == "/pair/poll") {
                val reqId = parseQuery(query)["id"].orEmpty()
                val state = pairResults[reqId]
                val out = JSONObject()
                when {
                    state == null && TxaLinkStore.pendingPair?.requestId == reqId -> out.put("status", "pending")
                    state != null -> {
                        out.put("status", state.first)
                        if (state.second.isNotEmpty()) out.put("token", state.second)
                    }
                    else -> out.put("status", "expired")
                }
                writeJson(s, 200, out)
                return
            }

            // Mọi đường dẫn điều khiển đều bắt buộc kèm mã thông báo tin cậy hợp lệ
            val token = headers["x-txa-token"]
                ?: headers["authorization"]?.removePrefix("Bearer ")?.trim()
            val trusted = TxaLinkStore.kiemTraToken(this, token)
            if (trusted == null) {
                writeJson(s, 401, JSONObject().put("error", "untrusted"))
                return
            }

            val response = runCatching { routeAuthed(method, path, query, body, readTotal) }.getOrElse { e ->
                TxaLogger.w("TxaLink", "Error handling $path", e)
                JSONObject().put("ok", false).put("error", e.message ?: e.javaClass.simpleName)
            }
            writeJson(s, 200, response)
        }
    }

    private fun pingJson(): JSONObject = JSONObject()
        .put("magic", TxaLinkStore.DISCOVER_MAGIC)
        .put("tvId", TxaLinkStore.deviceId(this))
        .put("name", TxaLinkStore.deviceName())
        .put("brand", Build.BRAND.orEmpty())
        .put("manufacturer", Build.MANUFACTURER.orEmpty())
        .put("model", Build.MODEL.orEmpty())
        .put("device", Build.DEVICE.orEmpty())
        .put("android", Build.VERSION.RELEASE.orEmpty())
        .put("sdk", Build.VERSION.SDK_INT)
        .put("port", TxaLinkStore.PORT)
        .put("a11y", TxaLinkAccessibilityService.isEnabled(this))
        .put("overlay", Settings.canDrawOverlays(this))
        .put("install", Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls())

    private fun handlePairRequest(req: JSONObject, remoteIp: String): JSONObject {
        val phoneId = req.optString("phoneId").trim()
        val phoneName = req.optString("phoneName", "Điện thoại Android").trim()
        val pin = req.optString("pin", "0000").trim()
        if (phoneId.isBlank()) return JSONObject().put("ok", false).put("error", "missing_phone_id")

        // Nếu điện thoại này đã được tin cậy sẵn trước đó thì cấp lại ngay token của nó!
        TxaLinkStore.dsDienThoaiTinCay(this).firstOrNull { it.id == phoneId }?.let { already ->
            val id = UUID.randomUUID().toString()
            pairResults[id] = "approved" to already.token
            return JSONObject().put("ok", true).put("requestId", id).put("status", "approved").put("token", already.token)
        }

        val reqId = UUID.randomUUID().toString()
        val pending = TxaLinkStore.PendingPairRequest(reqId, phoneId, phoneName, pin, remoteIp)
        TxaLinkStore.pendingPair = pending
        mainHandler.post { showPairPromptOnTv(pending) }
        return JSONObject().put("ok", true).put("requestId", reqId).put("status", "pending")
    }

    /** Duyệt hoặc từ chối yêu cầu liên kết (gọi từ hộp thoại nổi trên TV hoặc thẻ trong màn chính của app TV). */
    private fun resolvePair(pending: TxaLinkStore.PendingPairRequest, approve: Boolean) {
        if (TxaLinkStore.pendingPair?.requestId == pending.requestId) {
            TxaLinkStore.pendingPair = null
        }
        runCatching { overlayDialog?.dismiss() }
        overlayDialog = null
        if (approve) {
            val tok = TxaLinkStore.taoTokenNgauNhien()
            TxaLinkStore.luuDienThoaiTinCay(
                this,
                TxaLinkStore.TrustedPhone(pending.phoneId, pending.phoneName, tok, System.currentTimeMillis()),
            )
            pairResults[pending.requestId] = "approved" to tok
            TxaLogger.i("TxaLink", "Trusted phone paired: ${pending.phoneName}")
        } else {
            pairResults[pending.requestId] = "rejected" to ""
        }
    }

    private fun showPairPromptOnTv(pending: TxaLinkStore.PendingPairRequest) {
        activeInstance = this
        if (!Settings.canDrawOverlays(this)) {
            // Chưa có quyền hiện đè: mở nổi giao diện CleanTxa lên để người dùng bấm Cho phép ngay trên màn chính.
            runCatching {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        runCatching { overlayDialog?.dismiss() }
        runCatching {
            val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Liên kết Điện thoại · Mã ${pending.pinCode}")
                .setMessage(
                    "Điện thoại \"${pending.phoneName}\" (${pending.remoteIp}) muốn liên kết tin cậy với tivi này.\n\n" +
                        "Sau khi tin tưởng, điện thoại có thể tự kết nối ngầm, điều khiển remote và đổi giao diện Home sạch ngay cả khi đã tắt ADB."
                )
                .setPositiveButton("Tin tưởng & Cho phép") { _, _ -> resolvePair(pending, true) }
                .setNegativeButton("Từ chối") { _, _ -> resolvePair(pending, false) }
                .setOnCancelListener { resolvePair(pending, false) }
                .create()
            dlg.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dlg.show()
            overlayDialog = dlg
        }
    }

    // ---------- Các thao tác điều khiển không cần ADB ----------

    private fun routeAuthed(method: String, path: String, query: String, body: ByteArray, len: Int): JSONObject {
        if (method == "GET" && path == "/status") return buildFullStatus()
        if (method == "GET" && path == "/home/config") return readHomeConfig()
        if (method == "GET" && path == "/clipboard") return handleGetClipboard()
        if (method == "GET" && path == "/screenshot") return handleScreenshot()

        if (method == "POST" && path == "/app/install") {
            return installApkBytes(body.copyOf(len))
        }
        if (method == "POST" && path == "/beam/photo") {
            val cap = parseQuery(query)["caption"].orEmpty()
            return handleBeamPhoto(body.copyOf(len), cap)
        }
        if (method == "POST" && path == "/beam/photo/close") {
            return handleClosePhoto()
        }

        val req = if (len > 0) JSONObject(String(body, 0, len, Charsets.UTF_8)) else JSONObject()
        return when (path) {
            "/key" -> handleKey(req.optInt("code", 0))
            "/text" -> handleText(req.optString("text", ""), req.optBoolean("enter", false))
            "/volume" -> handleSetVolume(req.optInt("level", 0))
            "/beam/url" -> handleBeamUrl(req.optString("url", ""))
            "/announce" -> handleAnnounce(
                req.optString("message", ""),
                req.optBoolean("tts", false),
                req.optInt("seconds", 10),
            )
            "/notify" -> handleNotify(
                req.optString("app", ""),
                req.optString("title", ""),
                req.optString("text", ""),
                req.optBoolean("call", false),
            )
            "/clipboard" -> handleSetClipboard(
                req.optString("text", ""),
                req.optBoolean("pasteNow", false),
            )
            "/screen-dim" -> handleScreenDim(req.optInt("percent", 0))
            "/sleep-timer" -> handleSleepTimer(req.optInt("minutes", 0))
            "/free-ram" -> handleFreeRam()
            "/app/open" -> handleOpenApp(req.optString("pkg", ""))
            "/app/uninstall" -> handleUninstallApp(req.optString("pkg", ""))
            "/home/apply" -> handleApplyHome(req)
            else -> JSONObject().put("ok", false).put("error", "unknown_endpoint")
        }
    }

    private fun buildFullStatus(): JSONObject {
        val pm = packageManager
        val installed = HashSet<String>()
        val disabled = HashSet<String>()
        val userApps = HashSet<String>()
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS).forEach { ai ->
            installed += ai.packageName
            if (!ai.enabled) disabled += ai.packageName
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
                userApps += ai.packageName
            }
        }
        val leanback = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
        val launcher = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
        val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }.filterNot { it == "com.android.settings" || it == "com.android.tv.settings" }

        val curHome = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" }.orEmpty()

        val stat = StatFs(Environment.getDataDirectory().path)
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val volCur = runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        val volMax = runCatching { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(15).coerceAtLeast(1)
        val activePkg = runCatching {
            TxaLinkAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString().orEmpty()
        }.getOrDefault("")

        val pkgHome = detectInstalledCleanHome(installed)
        return pingJson()
            .put("ok", true)
            .put("productName", Build.PRODUCT.orEmpty())
            .put("buildProduct", Build.PRODUCT.orEmpty())
            .put("freeBytes", stat.availableBytes)
            .put("totalBytes", stat.totalBytes)
            .put("ramFreeBytes", mi.availMem)
            .put("ramTotalBytes", mi.totalMem)
            .put("volCur", volCur)
            .put("volMax", volMax)
            .put("dimPercent", screenDimPercent)
            .put("photoShowing", photoOverlayView != null)
            .put("activePkg", activePkg)
            .put("homeHienTai", curHome)
            .put("pkgHome", pkgHome)
            .put("sleepTimerEpochSec", sleepDeadlineSec ?: 0L)
            .put("caiDat", JSONArray(installed.toList()))
            .put("daTat", JSONArray(disabled.toList()))
            .put("coBieuTuong", JSONArray((leanback + launcher).distinct()))
            .put("coHome", JSONArray(homes.distinct()))
            .put("appTuCai", JSONArray(userApps.toList()))
    }

    private fun detectInstalledCleanHome(installed: Set<String>): String = when {
        ThaoTacTv.PKG_HOME_TXA in installed -> ThaoTacTv.PKG_HOME_TXA
        ThaoTacTv.PKG_HOME_XIAOMI in installed -> ThaoTacTv.PKG_HOME_XIAOMI
        else -> ThaoTacTv.PKG_HOME
    }

    private fun handleKey(code: Int): JSONObject {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ok = when (code) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); true
            }
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                true
            }
            KeyEvent.KEYCODE_SETTINGS -> {
                runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            }
            KeyEvent.KEYCODE_TV_INPUT -> {
                runCatching { startActivity(Intent("com.android.tv.action.VIEW_INPUTS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            }
            KeyEvent.KEYCODE_SEARCH -> {
                runCatching { startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            }
            KeyEvent.KEYCODE_HOME -> {
                TxaLinkAccessibilityService.instance?.performRemoteKey(code) == true || runCatching {
                    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
            }
            else -> TxaLinkAccessibilityService.instance?.performRemoteKey(code) == true
        }
        return JSONObject().put("ok", ok).put("needA11y", !ok && !TxaLinkAccessibilityService.isEnabled(this))
    }

    private fun handleText(text: String, enter: Boolean): JSONObject {
        val a11y = TxaLinkAccessibilityService.instance
        if (a11y != null && a11y.injectText(text, append = true)) {
            if (enter) a11y.performRemoteKey(KeyEvent.KEYCODE_ENTER)
            return JSONObject().put("ok", true)
        }
        // Dự phòng qua HomeKeyService của Home sạch nếu đang bật
        val b64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        for (pkg in ThaoTacTv.HOME_SACH) {
            runCatching {
                sendBroadcast(Intent("zone.vgs.cleantv.TYPE_TEXT").setPackage(pkg).putExtra("b64", b64))
            }
        }
        return JSONObject().put("ok", a11y != null).put("needA11y", a11y == null)
    }

    private fun handleSleepTimer(minutes: Int): JSONObject {
        mainHandler.removeCallbacks(sleepRunnable)
        if (minutes <= 0) {
            sleepDeadlineSec = null
        } else {
            val delayMs = minutes * 60_000L
            sleepDeadlineSec = System.currentTimeMillis() / 1000L + minutes * 60L
            mainHandler.postDelayed(sleepRunnable, delayMs)
        }
        return JSONObject().put("ok", true).put("sleepTimerEpochSec", sleepDeadlineSec ?: 0L)
    }

    private fun handleFreeRam(): JSONObject {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        @Suppress("DEPRECATION")
        packageManager.getInstalledApplications(0).forEach { ai ->
            if (ai.packageName != packageName && ai.packageName !in ThaoTacTv.HOME_SACH) {
                runCatching { am.killBackgroundProcesses(ai.packageName) }
            }
        }
        Thread.sleep(500)
        val after = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        val stat = StatFs(Environment.getDataDirectory().path)
        return JSONObject()
            .put("ok", true)
            .put("freedBytes", (after - before).coerceAtLeast(0L))
            .put("freeBytes", stat.availableBytes)
            .put("totalBytes", stat.totalBytes)
    }

    private fun handleOpenApp(pkg: String): JSONObject {
        if (pkg.isBlank()) return JSONObject().put("ok", false)
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
            ?: return JSONObject().put("ok", false)
        val ok = runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        return JSONObject().put("ok", ok)
    }

    private fun handleUninstallApp(pkg: String): JSONObject {
        if (pkg.isBlank()) return JSONObject().put("ok", false)
        val ok = runCatching {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        return JSONObject().put("ok", ok)
    }

    private fun installApkBytes(bytes: ByteArray): JSONObject {
        if (bytes.size < 4 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) {
            return JSONObject().put("ok", false).put("error", "File không phải định dạng APK")
        }
        val dir = File(cacheDir, "cap-nhat").apply { mkdirs() }
        val f = File(dir, "remote-install.apk").apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
        val ok = runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        return JSONObject().put("ok", ok).put("openedInstaller", true)
    }

    // ---------- Đặc quyền độc quyền CleanTxa Link (Chỉ hoạt động nhờ App chạy ngầm trên TV) ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun handleSetVolume(level: Int): JSONObject {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = runCatching { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(15).coerceAtLeast(1)
        val clamped = level.coerceIn(0, max)
        runCatching {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, AudioManager.FLAG_SHOW_UI)
        }
        val cur = runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(clamped)
        return JSONObject().put("ok", true).put("volCur", cur).put("volMax", max)
    }

    private fun handleBeamUrl(rawInput: String): JSONObject {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return JSONObject().put("ok", false).put("error", "Chưa có đường dẫn để mở.")
        // Tự bóc URL đầu tiên nếu người dùng bấm "Chia sẻ" từ YouTube / trình duyệt kèm tiêu đề
        val extracted = Regex("""[a-zA-Z][a-zA-Z0-9+.-]*://\S+""").find(trimmed)?.value?.trimEnd('.', ',', ')', ']')
            ?: if (trimmed.contains('.') && !trimmed.contains(' ')) "https://$trimmed" else trimmed

        // Chuẩn hoá link YouTube rút gọn / Shorts sang dạng chuẩn để YouTube TV / SmartTube bắt được ngay
        val normalized = normalizeTvUrl(extracted)
        val uri = runCatching { Uri.parse(normalized) }.getOrNull()
            ?: return JSONObject().put("ok", false).put("error", "Đường dẫn không hợp lệ.")

        var opened = runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess

        // Dự phòng cho YouTube trên các bản ROM TV ẩn bộ lọc mặc định
        if (!opened && (normalized.contains("youtube.com") || normalized.contains("youtu.be"))) {
            for (ytPkg in listOf("com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv", "com.google.android.youtube.tv", "com.google.android.youtube.tvmusic")) {
                if (runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(ytPkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.isSuccess
                ) {
                    opened = true
                    break
                }
            }
        }
        return JSONObject().put("ok", opened).put("url", normalized)
    }

    private fun normalizeTvUrl(url: String): String {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val host = u.host?.lowercase().orEmpty()
        if (host == "youtu.be") {
            val id = u.pathSegments.firstOrNull()
            if (!id.isNullOrBlank()) return "https://www.youtube.com/watch?v=$id"
        }
        if (host.endsWith("youtube.com")) {
            val segs = u.pathSegments
            if (segs.size >= 2 && (segs[0] == "shorts" || segs[0] == "live")) {
                return "https://www.youtube.com/watch?v=${segs[1]}"
            }
            if (host == "m.youtube.com") {
                return url.replaceFirst("m.youtube.com", "www.youtube.com")
            }
        }
        return url
    }

    private fun handleBeamPhoto(bytes: ByteArray, caption: String): JSONObject {
        if (bytes.isEmpty()) return JSONObject().put("ok", false).put("error", "Ảnh rỗng.")
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return JSONObject().put("ok", false).put("error", "Không giải mã được ảnh trên tivi.")

        if (!Settings.canDrawOverlays(this)) {
            bmp.recycle()
            val dir = File(cacheDir, "share").apply { mkdirs() }
            val f = File(dir, "beam-photo.jpg").apply { writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
            val ok = runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "image/jpeg")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            return JSONObject().put("ok", ok).put("overlay", false)
        }

        mainHandler.post { showPhotoOverlay(bmp, caption) }
        return JSONObject().put("ok", true).put("overlay", true)
    }

    private fun showPhotoOverlay(bmp: Bitmap, caption: String) {
        dismissPhotoOverlay()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        photoBitmap = bmp
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(244, 0, 0, 0))
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val iv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(bmp)
        }
        root.addView(iv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val pill = TextView(this).apply {
            val label = if (caption.isNotBlank()) "$caption  ·  Bấm OK / Back trên remote để đóng" else "Ảnh chiếu từ điện thoại  ·  Bấm OK / Back trên remote để đóng"
            text = label
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(18), dp(8), dp(18), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.argb(185, 20, 24, 28))
            }
        }
        val pillLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(28)
        }
        root.addView(pill, pillLp)

        root.setOnClickListener { dismissPhotoOverlay() }
        root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP && (
                    keyCode == KeyEvent.KEYCODE_BACK ||
                        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyCode == KeyEvent.KEYCODE_ESCAPE
                    )
            ) {
                dismissPhotoOverlay()
                true
            } else {
                false
            }
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        )
        runCatching {
            wm.addView(root, lp)
            photoOverlayView = root
            root.requestFocus()
        }
    }

    private fun handleClosePhoto(): JSONObject {
        mainHandler.post { dismissPhotoOverlay() }
        return JSONObject().put("ok", true)
    }

    private fun dismissPhotoOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        photoOverlayView?.let { v -> runCatching { wm.removeViewImmediate(v) } }
        photoOverlayView = null
        photoBitmap?.let { b -> if (!b.isRecycled) b.recycle() }
        photoBitmap = null
    }

    private fun handleAnnounce(message: String, tts: Boolean, seconds: Int): JSONObject {
        val msg = message.trim()
        if (msg.isEmpty()) return JSONObject().put("ok", false).put("error", "Nội dung thông báo trống.")
        val durSec = seconds.coerceIn(4, 60)
        mainHandler.post {
            showBannerOnTv(msg, durSec)
            if (tts) speakOnTv(msg)
        }
        return JSONObject().put("ok", true)
    }

    /** Thông báo phản chiếu từ điện thoại (kiểu Phone Link): hiện banner trên TV, cuộc gọi đến hiện lâu hơn. */
    private fun handleNotify(app: String, title: String, text: String, call: Boolean): JSONObject {
        val body = listOf(title.trim(), text.trim()).filter { it.isNotEmpty() }.joinToString("\n")
        if (body.isEmpty()) return JSONObject().put("ok", false).put("error", "Thông báo trống.")
        val header = (if (call) "📞 " else "🔔 ") + app.trim().ifEmpty { "Điện thoại" }
        mainHandler.post { showBannerOnTv(body.take(300), if (call) 20 else 8, header) }
        return JSONObject().put("ok", true)
    }

    private fun showBannerOnTv(msg: String, seconds: Int, headerText: String = "📱 Lời nhắn từ điện thoại") {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "📱 $msg", Toast.LENGTH_LONG).show()
            return
        }
        dismissBannerOverlay()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.argb(235, 16, 26, 34))
                setStroke(dp(2), Color.rgb(46, 196, 182))
            }
        }
        val header = TextView(this).apply {
            text = headerText
            setTextColor(Color.rgb(46, 196, 182))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        }
        val content = TextView(this).apply {
            text = msg
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        }
        box.addView(header)
        box.addView(content)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(36)
        }
        runCatching {
            wm.addView(box, lp)
            bannerOverlayView = box
            mainHandler.removeCallbacks(hideBannerRunnable)
            mainHandler.postDelayed(hideBannerRunnable, seconds * 1000L)
        }
    }

    private fun dismissBannerOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        bannerOverlayView?.let { v -> runCatching { wm.removeViewImmediate(v) } }
        bannerOverlayView = null
    }

    private fun speakOnTv(msg: String) {
        pendingTtsMessage = msg
        val existing = ttsEngine
        if (existing != null && ttsReady) {
            runCatching { existing.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "txa_msg") }
            return
        }
        if (existing == null) {
            ttsEngine = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val eng = ttsEngine ?: return@TextToSpeech
                    val viRes = eng.setLanguage(Locale("vi", "VN"))
                    if (viRes == TextToSpeech.LANG_MISSING_DATA || viRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                        eng.setLanguage(Locale.getDefault())
                    }
                    ttsReady = true
                    pendingTtsMessage?.let { text ->
                        runCatching { eng.speak(text, TextToSpeech.QUEUE_FLUSH, null, "txa_msg") }
                    }
                }
            }
        }
    }

    private fun handleGetClipboard(): JSONObject {
        var clipText = ""
        val latch = CountDownLatch(1)
        mainHandler.post {
            runCatching {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipText = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            }
            latch.countDown()
        }
        runCatching { latch.await(2, TimeUnit.SECONDS) }
        val focused = runCatching { TxaLinkAccessibilityService.instance?.readFocusedText().orEmpty() }.getOrDefault("")
        val best = clipText.ifBlank { focused }
        return JSONObject().put("ok", true).put("text", best).put("focusedText", focused)
    }

    private fun handleSetClipboard(text: String, pasteNow: Boolean): JSONObject {
        val latch = CountDownLatch(1)
        mainHandler.post {
            runCatching {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("CleanTxa", text))
            }
            latch.countDown()
        }
        runCatching { latch.await(2, TimeUnit.SECONDS) }
        var pasted = false
        if (pasteNow && text.isNotEmpty()) {
            pasted = TxaLinkAccessibilityService.instance?.injectText(text, append = false) == true
        }
        return JSONObject().put("ok", true).put("pasted", pasted)
    }

    private fun handleScreenDim(percent: Int): JSONObject {
        val clamped = percent.coerceIn(0, 75)
        if (clamped > 0 && !Settings.canDrawOverlays(this)) {
            return JSONObject().put("ok", false).put("needOverlay", true)
                .put("error", "Cần cấp quyền Hiện đè (Overlay) 1 lần trên TV để bật bộ lọc dịu mắt ban đêm.")
        }
        screenDimPercent = clamped
        mainHandler.post { applyDimOverlay(clamped) }
        return JSONObject().put("ok", true).put("dimPercent", clamped)
    }

    private fun applyDimOverlay(percent: Int) {
        if (percent <= 0) {
            dismissDimOverlay()
            return
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Ánh ấm nhẹ giảm ánh sáng xanh ban đêm, hoàn toàn cho phép bấm xuyên qua (không cản remote hay chuột)
        val alpha = (percent * 255 / 100).coerceIn(0, 200)
        val color = Color.argb(alpha, 10, 7, 4)
        val cur = dimOverlayView
        if (cur != null) {
            cur.setBackgroundColor(color)
            return
        }
        val v = View(this).apply { setBackgroundColor(color) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        runCatching {
            wm.addView(v, lp)
            dimOverlayView = v
        }
    }

    private fun dismissDimOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        dimOverlayView?.let { v -> runCatching { wm.removeViewImmediate(v) } }
        dimOverlayView = null
    }

    private fun handleScreenshot(): JSONObject {
        if (Build.VERSION.SDK_INT < 30) {
            return JSONObject().put("ok", false).put("error", "Chụp màn hình không cần ADB yêu cầu Android TV 11 trở lên.")
        }
        val a11y = TxaLinkAccessibilityService.instance
            ?: return JSONObject().put("ok", false).put("needA11y", true)
                .put("error", "Hãy bật quyền Trợ năng của CleanTxa trên TV để chụp màn hình không cần ADB.")
        val jpeg = a11y.captureScreenJpeg()
            ?: return JSONObject().put("ok", false).put("error", "Không chụp được (một số video DRM như Netflix chặn chụp hình).")
        return JSONObject().put("ok", true).put("jpegB64", Base64.encodeToString(jpeg, Base64.NO_WRAP))
    }

    // ---------- Đọc & Đẩy giao diện Home sạch qua ThemeProvider (KHÔNG cần ADB!) ----------

    private fun resolveTargetHomePkg(requested: String? = null): String {
        if (!requested.isNullOrBlank() && runCatching { packageManager.getPackageInfo(requested, 0) }.isSuccess) {
            return requested
        }
        for (p in listOf(ThaoTacTv.PKG_HOME_TXA, ThaoTacTv.PKG_HOME, ThaoTacTv.PKG_HOME_XIAOMI)) {
            if (runCatching { packageManager.getPackageInfo(p, 0) }.isSuccess) return p
        }
        return ThaoTacTv.PKG_HOME
    }

    private fun readHomeConfig(): JSONObject {
        val pkg = resolveTargetHomePkg()
        val auth = "content://$pkg.theme"
        val cfgText = runCatching {
            contentResolver.openInputStream(Uri.parse("$auth/config.json"))?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull().orEmpty()
        val wpB64 = runCatching {
            contentResolver.openInputStream(Uri.parse("$auth/wallpaper.jpg"))?.use { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
        }.getOrNull().orEmpty()
        return JSONObject()
            .put("ok", cfgText.isNotBlank())
            .put("pkgHome", pkg)
            .put("config", cfgText)
            .put("wallpaperB64", wpB64)
    }

    private fun handleApplyHome(req: JSONObject): JSONObject {
        val pkg = resolveTargetHomePkg(req.optString("pkgHome"))
        val auth = "content://$pkg.theme"
        var configStr = req.optString("config")
        if (pkg == ThaoTacTv.PKG_HOME_TXA) {
            // Giữ các lựa chọn cục bộ người dùng đã đặt ngay trên TV (chế độ trẻ em, mã PIN, app ẩn, app mở lúc bật TV).
            val cur = runCatching {
                contentResolver.openInputStream(Uri.parse("$auth/config.json"))?.bufferedReader(Charsets.UTF_8)?.use { JSONObject(it.readText()) }
            }.getOrNull()
            val next = runCatching { JSONObject(configStr) }.getOrNull()
            if (cur != null && next != null) {
                for (k in listOf("kids_mode", "kids_apps", "kids_pin", "hidden_apps", "open_on_start_pkg")) {
                    if (!next.has(k) && cur.has(k)) next.put(k, cur.get(k))
                }
                configStr = next.toString()
            }
        }

        fun writeProviderFile(relPath: String, bytes: ByteArray): Boolean = runCatching {
            contentResolver.openOutputStream(Uri.parse("$auth/$relPath"), "wt")?.use { it.write(bytes) } != null
        }.getOrDefault(false)

        if (!writeProviderFile("config.json", configStr.toByteArray(Charsets.UTF_8))) {
            return JSONObject().put("ok", false).put("error", "Chưa cài Home sạch ($pkg) trên tivi hoặc không mở được ThemeProvider.")
        }

        val wpB64 = req.optString("wallpaperB64")
        if (wpB64.isNotBlank()) {
            writeProviderFile("wallpaper.jpg", Base64.decode(wpB64, Base64.DEFAULT))
        } else {
            runCatching { contentResolver.delete(Uri.parse("$auth/wallpaper.jpg"), null, null) }
        }

        runCatching { contentResolver.delete(Uri.parse("$auth/wallpapers"), null, null) }
        req.optJSONArray("rotatingWallpapers")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                val data = o.optString("b64")
                if (name.isNotBlank() && data.isNotBlank()) {
                    writeProviderFile("wallpapers/$name", Base64.decode(data, Base64.DEFAULT))
                }
            }
        }

        req.optJSONArray("channelLogos")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                val data = o.optString("b64")
                if (name.isNotBlank() && data.isNotBlank()) {
                    writeProviderFile("kenh-$name", Base64.decode(data, Base64.DEFAULT))
                }
            }
        }

        writeProviderFile("theme_revision", UUID.randomUUID().toString().replace("-", "").toByteArray(Charsets.UTF_8))
        runCatching { sendBroadcast(Intent(ThaoTacTv.RELOAD_CONFIG).setPackage(pkg)) }
        runCatching {
            startActivity(
                Intent().setClassName(pkg, "${ThaoTacTv.NS_HOME}.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
        return JSONObject().put("ok", true).put("pkgHome", pkg)
    }

    // ---------- Tiện ích dòng & phản hồi HTTP ----------

    private fun readLineUtf8(inp: BufferedInputStream): String? {
        val bos = ByteArrayOutputStream()
        while (true) {
            val b = inp.read()
            if (b == -1) return if (bos.size() == 0) null else bos.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) bos.write(b)
            if (bos.size() > 8192) break
        }
        return bos.toString("UTF-8")
    }

    private fun parseQuery(q: String): Map<String, String> =
        q.split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i) to Uri.decode(part.substring(i + 1))
        }.toMap()

    private fun writeJson(sock: Socket, status: Int, json: JSONObject) {
        val body = json.toString().toByteArray(Charsets.UTF_8)
        val statusText = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            else -> "Error"
        }
        val header = "HTTP/1.1 $status $statusText\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n"
        val out = sock.getOutputStream()
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    companion object {
        private const val NOTIF_ID = 2312
        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var activeInstance: TxaLinkService? = null

        fun start(ctx: Context) {
            val i = Intent(ctx, TxaLinkService::class.java)
            runCatching {
                ctx.startForegroundService(i)
            }.onFailure {
                runCatching { ctx.startService(i) }
            }
        }

        fun approvePendingFromUi(approve: Boolean) {
            val p = TxaLinkStore.pendingPair ?: return
            activeInstance?.resolvePair(p, approve)
        }

        fun localIpv4(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        }.getOrNull()
    }
}
