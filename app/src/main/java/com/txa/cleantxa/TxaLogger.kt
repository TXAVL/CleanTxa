package com.txa.cleantxa

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val time: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null,
) {
    fun formattedLine(): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(time))
        val base = "[$ts] [${level.name}] [$tag] $message"
        return if (stackTrace.isNullOrBlank()) base else "$base\n$stackTrace"
    }
}

/**
 * Hệ thống ghi nhật ký tập trung của CleanTxa (`TxaLogger`):
 *  - Lưu logs vào bộ nhớ trong (`filesDir/logs/cleantxa.jsonl`), giữ tối đa 1500 dòng.
 *  - Bắt lỗi không mong muốn (`UncaughtExceptionHandler`): trên TV sẽ lưu âm thầm
 *    không bật hộp thoại lỗi hệ thống, tự mở lại app vào màn mã QR cổng 2311.
 *  - Xuất báo cáo đầy đủ gồm cấu hình thiết bị + toàn bộ logs để copy/chia sẻ/xem web.
 */
object TxaLogger {
    private const val PREF = "txa_logger"
    private const val KEY_TV_ERROR = "pending_tv_error"
    private const val KEY_TV_ERROR_MSG = "pending_tv_error_msg"
    private const val MAX_ENTRIES = 1500

    private val lock = Any()
    private var appCtx: Context? = null
    private var initialized = false
    private var prevHandler: Thread.UncaughtExceptionHandler? = null

    val entries = mutableStateListOf<LogEntry>()

    fun init(ctx: Context) {
        synchronized(lock) {
            if (initialized) return
            initialized = true
            appCtx = ctx.applicationContext
            loadFromDisk(ctx.applicationContext)
            installCrashHandler(ctx.applicationContext)
        }
        i("App", "CleanTxa ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) started · TV=${ManHinh.laTv}")
    }

    private fun logFile(ctx: Context): File =
        File(ctx.filesDir, "logs/cleantxa.jsonl").apply { parentFile?.mkdirs() }

    private fun loadFromDisk(ctx: Context) {
        val f = logFile(ctx)
        if (!f.isFile) return
        val loaded = runCatching {
            f.readLines(Charsets.UTF_8)
                .takeLast(MAX_ENTRIES)
                .mapNotNull { line ->
                    runCatching {
                        val o = JSONObject(line)
                        LogEntry(
                            time = o.optLong("t", System.currentTimeMillis()),
                            level = runCatching { LogLevel.valueOf(o.optString("l", "INFO")) }.getOrDefault(LogLevel.INFO),
                            tag = o.optString("tag", "App"),
                            message = o.optString("msg", ""),
                            stackTrace = o.optString("st").takeIf { it.isNotBlank() },
                        )
                    }.getOrNull()
                }
        }.getOrDefault(emptyList())
        entries.clear()
        entries.addAll(loaded)
    }

    private fun installCrashHandler(ctx: Context) {
        prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stack = Log.getStackTraceString(throwable)
            val summary = "${throwable.javaClass.simpleName}: ${throwable.message ?: "Unknown error"}"
            recordSync(ctx, LogLevel.ERROR, "Crash/${thread.name}", summary, stack)
            if (ManHinh.laTv) {
                markPendingTvError(ctx, summary)
                runCatching {
                    val i = Intent(ctx, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    ctx.startActivity(i)
                }
                Process.killProcess(Process.myPid())
                exitProcess(2)
            } else {
                prevHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun markPendingTvError(ctx: Context, summary: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TV_ERROR, true)
            .putString(KEY_TV_ERROR_MSG, summary)
            .commit()
    }

    fun hasPendingTvError(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_TV_ERROR, false)

    fun pendingTvErrorSummary(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_TV_ERROR_MSG, "") ?: ""

    fun clearPendingTvError(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TV_ERROR)
            .remove(KEY_TV_ERROR_MSG)
            .apply()
    }

    fun i(tag: String, msg: String) {
        Log.i("CleanTxa/$tag", msg)
        append(LogLevel.INFO, tag, msg, null)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        val st = tr?.let { Log.getStackTraceString(it) }
        Log.w("CleanTxa/$tag", msg, tr)
        append(LogLevel.WARN, tag, msg, st)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        val st = tr?.let { Log.getStackTraceString(it) }
        Log.e("CleanTxa/$tag", msg, tr)
        append(LogLevel.ERROR, tag, msg, st)
    }

    private fun append(level: LogLevel, tag: String, msg: String, stack: String?) {
        val ctx = appCtx
        val entry = LogEntry(System.currentTimeMillis(), level, tag, msg, stack)
        synchronized(lock) {
            entries.add(entry)
            if (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
            }
            if (ctx != null) {
                writeLine(ctx, entry)
            }
        }
    }

    private fun recordSync(ctx: Context, level: LogLevel, tag: String, msg: String, stack: String?) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, msg, stack)
        synchronized(lock) {
            entries.add(entry)
            writeLine(ctx, entry)
        }
    }

    private fun writeLine(ctx: Context, entry: LogEntry) {
        runCatching {
            val f = logFile(ctx)
            val o = JSONObject()
                .put("t", entry.time)
                .put("l", entry.level.name)
                .put("tag", entry.tag)
                .put("msg", entry.message)
                .apply { if (!entry.stackTrace.isNullOrBlank()) put("st", entry.stackTrace) }
            f.appendText(o.toString() + "\n", Charsets.UTF_8)
            if (f.length() > 512 * 1024) {
                val tail = f.readLines(Charsets.UTF_8).takeLast(MAX_ENTRIES)
                f.writeText(tail.joinToString("\n") + "\n", Charsets.UTF_8)
            }
        }
    }

    fun clear(ctx: Context) {
        synchronized(lock) {
            entries.clear()
            runCatching { logFile(ctx).delete() }
        }
    }

    fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.filter { it.isUp && !it.isLoopback }
            ?.flatMap { it.inetAddresses.toList() }
            ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress
    }.getOrNull()

    private fun networkTransport(ctx: Context): String = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return "Offline"
        val caps = cm.getNetworkCapabilities(net) ?: return "Unknown"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }
    }.getOrDefault("Unknown")

    private fun ramInfo(ctx: Context): String = runCatching {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        "${TxaFormat.bytes(mi.availMem)} / ${TxaFormat.bytes(mi.totalMem)} free${if (mi.lowMemory) " (LOW)" else ""}"
    }.getOrDefault("Unknown")

    private fun storageInfo(): String = runCatching {
        val st = StatFs(Environment.getDataDirectory().path)
        "${TxaFormat.bytes(st.availableBytes)} / ${TxaFormat.bytes(st.totalBytes)} free"
    }.getOrDefault("Unknown")

    fun compactConfigPairs(ctx: Context, extraTvInfo: String? = null): List<Pair<String, String>> = buildList {
        val mode = if (ManHinh.laTv) "TV" else "Phone"
        add("App" to "CleanTxa ${BuildConfig.VERSION_NAME} ($mode)")
        add("Device" to "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        add("RAM" to ramInfo(ctx))
        add("Storage" to storageInfo())
        add("Network" to "${networkTransport(ctx)} · ${localIpv4() ?: "Unavailable"}")
        if (!extraTvInfo.isNullOrBlank()) {
            extraTvInfo.trim().lines().forEach { line ->
                val k = line.substringBefore(':').trim()
                val v = line.substringAfter(':', "").trim()
                if (k.isNotBlank() && v.isNotBlank()) add(k to v)
            }
        }
    }

    fun deviceConfigPairs(ctx: Context, extraTvInfo: String? = null): List<Pair<String, String>> {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val dm = ctx.resources.displayMetrics
        val ram = ramInfo(ctx)
        val storage = storageInfo()

        return buildList {
            add("Timestamp" to now)
            add("App" to "CleanTxa ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
            add("Package" to ctx.packageName)
            add("Build Type" to if (BuildConfig.DEBUG) "debug" else "release")
            add("Mode" to if (ManHinh.laTv) "Android TV / Leanback" else "Phone / Tablet")
            add("Language" to "${if (NgonNgu.tiengViet) "vi" else "en"} (${Locale.getDefault()})")
            add("Android OS" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}, Patch ${Build.VERSION.SECURITY_PATCH})")
            add("Build ID" to "${Build.DISPLAY} (${Build.ID})")
            add("Brand / Maker" to "${Build.BRAND} / ${Build.MANUFACTURER}")
            add("Model / Device" to "${Build.MODEL} (${Build.DEVICE} · ${Build.PRODUCT})")
            add("Board / Hardware" to "${Build.BOARD} / ${Build.HARDWARE}")
            add("CPU ABIs" to Build.SUPPORTED_ABIS.joinToString(", "))
            add("Display" to "${dm.widthPixels}x${dm.heightPixels}px · ${dm.densityDpi}dpi (${TxaFormat.number2f(dm.density)}x)")
            add("RAM" to ram)
            add("Storage" to storage)
            add("Network" to "${networkTransport(ctx)} · IP ${localIpv4() ?: "Unavailable"}")
            add("Fingerprint" to Build.FINGERPRINT)
            if (!extraTvInfo.isNullOrBlank()) {
                extraTvInfo.trim().lines().forEach { line ->
                    val k = line.substringBefore(':').trim()
                    val v = line.substringAfter(':', "").trim()
                    if (k.isNotBlank() && v.isNotBlank()) add(k to v)
                }
            }
        }
    }

    fun deviceConfigReport(ctx: Context, extraTvInfo: String? = null): String = buildString {
        appendLine("=== CleanTxa Device & App Config ===")
        val pairs = deviceConfigPairs(ctx, extraTvInfo)
        val pad = pairs.maxOfOrNull { it.first.length } ?: 16
        pairs.forEach { (k, v) ->
            appendLine("${k.padEnd(pad)} : $v")
        }
    }.trim()

    fun fullReport(ctx: Context, extraTvInfo: String? = null): String = buildString {
        appendLine(deviceConfigReport(ctx, extraTvInfo))
        appendLine()
        appendLine("=== Logs (${entries.size}) ===")
        synchronized(lock) {
            if (entries.isEmpty()) {
                appendLine("(empty)")
            } else {
                entries.forEach { appendLine(it.formattedLine()) }
            }
        }
    }.trim()

    fun copyToClipboard(ctx: Context, text: String): Boolean = runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("CleanTxa Logs", text))
        true
    }.getOrDefault(false)
}

