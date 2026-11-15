package com.txa.cleantxa

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
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
        i("Logger", "Logs cleared")
    }

    fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.filter { it.isUp && !it.isLoopback }
            ?.flatMap { it.inetAddresses.toList() }
            ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress
    }.getOrNull()

    fun deviceConfigReport(ctx: Context, extraTvInfo: String? = null): String = buildString {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        appendLine("=== CleanTxa Device & App Config ===")
        appendLine("Timestamp      : $now")
        appendLine("App            : CleanTxa ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
        appendLine("Package        : ${ctx.packageName}")
        appendLine("Mode           : ${if (ManHinh.laTv) "Android TV / Leanback" else "Phone / Tablet"}")
        appendLine("Language       : ${if (NgonNgu.tiengViet) "vi" else "en"}")
        appendLine("Android        : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Device         : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Board/Hardware : ${Build.BOARD} / ${Build.HARDWARE}")
        appendLine("ABIs           : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        appendLine("Local IP       : ${localIpv4() ?: "Unavailable"}")
        if (!extraTvInfo.isNullOrBlank()) {
            appendLine()
            appendLine("--- Connected TV Info ---")
            appendLine(extraTvInfo.trim())
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

