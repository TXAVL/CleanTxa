package com.txa.cleantxa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Máy chủ HTTP cục bộ trên cổng 2311 để thiết bị khác cùng Wi-Fi quét mã QR
 * và đọc trực tiếp file logs + cấu hình thiết bị trên trình duyệt web.
 */
object LogWebServer {
    const val PORT = 2311

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    private var workerPool = Executors.newCachedThreadPool()

    val isRunning: Boolean get() = running

    fun urlForDevice(): String {
        val ip = TxaLogger.localIpv4() ?: "127.0.0.1"
        return "http://$ip:$PORT/"
    }

    fun start(ctx: Context, extraInfoProvider: () -> String? = { null }): Boolean {
        if (running && serverSocket != null && !serverSocket!!.isClosed) return true
        stop()
        return runCatching {
            val ss = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT))
            }
            serverSocket = ss
            running = true
            if (workerPool.isShutdown) {
                workerPool = Executors.newCachedThreadPool()
            }
            val appCtx = ctx.applicationContext
            Thread({
                while (running && !ss.isClosed) {
                    val client = runCatching { ss.accept() }.getOrNull() ?: break
                    workerPool.execute { handleClient(client, appCtx, extraInfoProvider) }
                }
            }, "CleanTxa-LogWebServer").apply { isDaemon = true }.start()
            TxaLogger.i("LogWebServer", "Started on port $PORT (${urlForDevice()})")
            true
        }.onFailure {
            TxaLogger.e("LogWebServer", "Failed to start on port $PORT", it)
            stop()
        }.getOrDefault(false)
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleClient(socket: Socket, ctx: Context, extraInfoProvider: () -> String?) {
        socket.use { s ->
            runCatching {
                s.soTimeout = 5_000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                val path = requestLine.split(' ').getOrNull(1)?.substringBefore('?') ?: "/"
                val extra = runCatching { extraInfoProvider() }.getOrNull()
                val report = TxaLogger.fullReport(ctx, extra)

                val out = s.getOutputStream()
                when (path) {
                    "/logs.txt" -> writeResponse(
                        out = out,
                        contentType = "text/plain; charset=utf-8",
                        body = report.toByteArray(Charsets.UTF_8),
                    )
                    "/download" -> writeResponse(
                        out = out,
                        contentType = "text/plain; charset=utf-8",
                        body = report.toByteArray(Charsets.UTF_8),
                        extraHeaders = listOf("Content-Disposition: attachment; filename=\"cleantxa-logs.txt\""),
                    )
                    else -> {
                        val html = buildHtmlPage(ctx, extra, report)
                        writeResponse(
                            out = out,
                            contentType = "text/html; charset=utf-8",
                            body = html.toByteArray(Charsets.UTF_8),
                        )
                    }
                }
                out.flush()
            }
        }
    }

    private fun writeResponse(
        out: java.io.OutputStream,
        contentType: String,
        body: ByteArray,
        extraHeaders: List<String> = emptyList(),
    ) {
        val headers = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            extraHeaders.forEach { append("$it\r\n") }
            append("\r\n")
        }
        out.write(headers.toByteArray(Charsets.US_ASCII))
        out.write(body)
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun buildHtmlPage(ctx: Context, extra: String?, fullReport: String): String {
        val config = escapeHtml(TxaLogger.deviceConfigReport(ctx, extra))
        val rawLogs = escapeHtml(
            TxaLogger.entries.joinToString("\n") { it.formattedLine() }.ifBlank { "(empty)" }
        )
        val rawEscapedForJs = fullReport
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\${", "\\${'$'}{")
        return """
            <!doctype html>
            <html lang="vi">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width,initial-scale=1" />
              <title>CleanTxa Logs (Port $PORT)</title>
              <style>
                body { margin: 0; font-family: system-ui, -apple-system, sans-serif; background: #0B1118; color: #E8F1F5; }
                header { padding: 16px 20px; background: #101A24; border-bottom: 1px solid #1E2E3E; display: flex; flex-wrap: wrap; gap: 10px; align-items: center; justify-content: space-between; }
                h1 { margin: 0; font-size: 18px; color: #2EC4B6; }
                .actions { display: flex; gap: 8px; flex-wrap: wrap; }
                a.btn, button { background: #2EC4B6; color: #061318; border: 0; border-radius: 8px; padding: 8px 14px; font-weight: 700; font-size: 13px; text-decoration: none; cursor: pointer; }
                a.btn.sec, button.sec { background: #182735; color: #E8F1F5; border: 1px solid #2A3F54; }
                main { padding: 16px 20px; max-width: 1100px; margin: 0 auto; display: grid; gap: 16px; }
                section { background: #101A24; border: 1px solid #1E2E3E; border-radius: 12px; padding: 14px; }
                h2 { margin: 0 0 10px 0; font-size: 14px; color: #84D2C5; text-transform: uppercase; letter-spacing: 0.04em; }
                pre { margin: 0; white-space: pre-wrap; word-break: break-word; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; line-height: 1.5; color: #D8E6EE; }
              </style>
            </head>
            <body>
              <header>
                <h1>CleanTxa · Web Logs (Port $PORT)</h1>
                <div class="actions">
                  <button onclick="copyAll()">Copy Logs + Config</button>
                  <a class="btn sec" href="/logs.txt" target="_blank">Open Raw .txt</a>
                  <a class="btn sec" href="/download">Download .txt</a>
                  <button class="sec" onclick="location.reload()">Refresh</button>
                </div>
              </header>
              <main>
                <section>
                  <h2>Device &amp; App Configuration</h2>
                  <pre>$config</pre>
                </section>
                <section>
                  <h2>Logs (${TxaLogger.entries.size})</h2>
                  <pre>$rawLogs</pre>
                </section>
              </main>
              <script>
                const FULL_REPORT = `$rawEscapedForJs`;
                async function copyAll() {
                  try {
                    await navigator.clipboard.writeText(FULL_REPORT);
                    alert('Copied device config & logs!');
                  } catch (e) {
                    window.open('/logs.txt', '_blank');
                  }
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    }.getOrNull()
}

