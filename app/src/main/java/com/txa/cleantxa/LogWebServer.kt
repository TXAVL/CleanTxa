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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

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
        val pairs = TxaLogger.deviceConfigPairs(ctx, extra)
        val entries = TxaLogger.entries.toList().asReversed()
        val infoCount = entries.count { it.level == LogLevel.INFO }
        val warnCount = entries.count { it.level == LogLevel.WARN }
        val errorCount = entries.count { it.level == LogLevel.ERROR }

        val configCards = pairs.joinToString("\n") { (k, v) ->
            """<div class="cfg-item"><div class="cfg-k">${escapeHtml(k)}</div><div class="cfg-v">${escapeHtml(v)}</div></div>"""
        }

        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val logsJsonArray = JSONArray()
        for (e in entries) {
            logsJsonArray.put(
                JSONObject().apply {
                    put("time", fmt.format(Date(e.time)))
                    put("level", e.level.name)
                    put("tag", e.tag)
                    put("message", e.message)
                    put("stack", e.stackTrace ?: "")
                }
            )
        }
        val safeLogsJson = logsJsonArray.toString().replace("</", "<\\/")
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
              <title>CleanTxa v${escapeHtml(BuildConfig.VERSION_NAME)} · Web Logs (Port $PORT)</title>
              <style>
                :root {
                  --bg: #071017;
                  --card: #0E1924;
                  --card2: #132230;
                  --border: #1E3245;
                  --text: #E9F2F7;
                  --muted: #8AA3B5;
                  --accent: #2EC4B6;
                  --info: #38BDF8;
                  --warn: #FBBF24;
                  --err: #F87171;
                }
                * { box-sizing: border-box; }
                body { margin: 0; font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif; background: var(--bg); color: var(--text); }
                header { position: sticky; top: 0; z-index: 10; padding: 14px 20px; background: rgba(14,25,36,0.95); backdrop-filter: blur(8px); border-bottom: 1px solid var(--border); display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; }
                .brand { display: flex; align-items: center; gap: 10px; }
                .dot { width: 10px; height: 10px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 10px var(--accent); }
                h1 { margin: 0; font-size: 17px; font-weight: 700; color: var(--text); }
                .sub { font-size: 12px; color: var(--muted); }
                .actions { display: flex; gap: 8px; flex-wrap: wrap; }
                a.btn, button { background: var(--accent); color: #05161A; border: 0; border-radius: 8px; padding: 8px 13px; font-weight: 700; font-size: 12.5px; text-decoration: none; cursor: pointer; transition: opacity .15s; }
                a.btn:hover, button:hover { opacity: 0.9; }
                a.btn.sec, button.sec { background: var(--card2); color: var(--text); border: 1px solid var(--border); }
                button.sec.active { border-color: var(--accent); color: var(--accent); }
                main { padding: 18px 20px 36px; max-width: 1180px; margin: 0 auto; display: grid; gap: 16px; }
                .stats { display: flex; flex-wrap: wrap; gap: 10px; }
                .pill { background: var(--card); border: 1px solid var(--border); border-radius: 999px; padding: 6px 14px; font-size: 12.5px; font-weight: 600; display: flex; align-items: center; gap: 6px; }
                .pill b { font-size: 14px; }
                .pill.info b { color: var(--info); }
                .pill.warn b { color: var(--warn); }
                .pill.err b { color: var(--err); }
                section { background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 16px; }
                .sec-head { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; justify-content: space-between; margin-bottom: 12px; }
                h2 { margin: 0; font-size: 13px; color: var(--accent); text-transform: uppercase; letter-spacing: 0.06em; }
                .cfg-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 10px; }
                .cfg-item { background: var(--card2); border: 1px solid var(--border); border-radius: 10px; padding: 9px 11px; }
                .cfg-k { font-size: 11px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; margin-bottom: 3px; }
                .cfg-v { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; word-break: break-word; color: var(--text); }
                .toolbar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 12px; }
                .chips { display: flex; gap: 6px; flex-wrap: wrap; }
                .chip { background: var(--card2); color: var(--muted); border: 1px solid var(--border); border-radius: 8px; padding: 6px 11px; font-size: 12px; font-weight: 600; cursor: pointer; }
                .chip.on { background: rgba(46,196,182,0.16); color: var(--accent); border-color: var(--accent); }
                .search { flex: 1; min-width: 210px; background: var(--card2); color: var(--text); border: 1px solid var(--border); border-radius: 8px; padding: 8px 12px; font-size: 13px; outline: none; }
                .search:focus { border-color: var(--accent); }
                .log-list { display: grid; gap: 8px; }
                .log-card { background: var(--card2); border: 1px solid var(--border); border-left-width: 4px; border-radius: 10px; padding: 10px 12px; }
                .log-card.INFO { border-left-color: var(--info); }
                .log-card.WARN { border-left-color: var(--warn); }
                .log-card.ERROR { border-left-color: var(--err); background: rgba(248,113,113,0.06); }
                .log-meta { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 5px; font-size: 12px; }
                .badge { padding: 2px 7px; border-radius: 5px; font-weight: 700; font-size: 11px; }
                .badge.INFO { background: rgba(56,189,248,0.16); color: var(--info); }
                .badge.WARN { background: rgba(251,191,36,0.18); color: var(--warn); }
                .badge.ERROR { background: rgba(248,113,113,0.2); color: var(--err); }
                .tag { font-weight: 700; color: var(--text); }
                .ts { color: var(--muted); font-family: ui-monospace, monospace; margin-left: auto; }
                .msg { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; line-height: 1.48; white-space: pre-wrap; word-break: break-word; color: #DCE8F0; }
                .stack { margin-top: 7px; padding-top: 7px; border-top: 1px dashed var(--border); font-family: ui-monospace, monospace; font-size: 11.5px; color: #FCA5A5; white-space: pre-wrap; word-break: break-word; }
                .empty { text-align: center; padding: 28px; color: var(--muted); font-size: 13px; }
              </style>
            </head>
            <body>
              <header>
                <div class="brand">
                  <span class="dot"></span>
                  <div>
                    <h1>CleanTxa v${escapeHtml(BuildConfig.VERSION_NAME)} · Web Logs</h1>
                    <div class="sub">Local HTTP Server · Port $PORT · ${entries.size} entries</div>
                  </div>
                </div>
                <div class="actions">
                  <button id="copyBtn" onclick="copyAll()">Copy Logs + Config</button>
                  <a class="btn sec" href="/logs.txt" target="_blank">Raw .txt</a>
                  <a class="btn sec" href="/download">Download .txt</a>
                  <button id="autoBtn" class="sec" onclick="toggleAuto()">Auto-refresh: OFF</button>
                  <button class="sec" onclick="location.reload()">Refresh</button>
                </div>
              </header>
              <main>
                <div class="stats">
                  <div class="pill">Total: <b>${entries.size}</b></div>
                  <div class="pill info">INFO: <b>$infoCount</b></div>
                  <div class="pill warn">WARN: <b>$warnCount</b></div>
                  <div class="pill err">ERROR: <b>$errorCount</b></div>
                </div>
                <section>
                  <div class="sec-head">
                    <h2>Device &amp; App Configuration</h2>
                  </div>
                  <div class="cfg-grid">$configCards</div>
                </section>
                <section>
                  <div class="sec-head">
                    <h2>Logs (<span id="shownCount">${entries.size}</span>)</h2>
                  </div>
                  <div class="toolbar">
                    <div class="chips">
                      <button class="chip on" data-lv="ALL" onclick="setLevel('ALL', this)">All</button>
                      <button class="chip" data-lv="INFO" onclick="setLevel('INFO', this)">INFO</button>
                      <button class="chip" data-lv="WARN" onclick="setLevel('WARN', this)">WARN</button>
                      <button class="chip" data-lv="ERROR" onclick="setLevel('ERROR', this)">ERROR</button>
                    </div>
                    <input id="search" class="search" type="search" placeholder="Search tag, message, stack..." oninput="renderLogs()" />
                  </div>
                  <div id="logList" class="log-list"></div>
                </section>
              </main>
              <script>
                const FULL_REPORT = `$rawEscapedForJs`;
                const LOGS = $safeLogsJson;
                let activeLevel = 'ALL';
                let autoTimer = null;

                function setLevel(lv, btn) {
                  activeLevel = lv;
                  document.querySelectorAll('.chip').forEach(c => c.classList.remove('on'));
                  btn.classList.add('on');
                  renderLogs();
                }

                function renderLogs() {
                  const q = (document.getElementById('search').value || '').toLowerCase();
                  const list = document.getElementById('logList');
                  const filtered = LOGS.filter(item => {
                    if (activeLevel !== 'ALL' && item.level !== activeLevel) return false;
                    if (!q) return true;
                    return (item.tag + ' ' + item.message + ' ' + item.stack).toLowerCase().includes(q);
                  });
                  document.getElementById('shownCount').textContent = filtered.length;
                  list.innerHTML = '';
                  if (filtered.length === 0) {
                    const empty = document.createElement('div');
                    empty.className = 'empty';
                    empty.textContent = 'No matching log entries.';
                    list.appendChild(empty);
                    return;
                  }
                  for (const item of filtered) {
                    const card = document.createElement('div');
                    card.className = 'log-card ' + item.level;
                    const meta = document.createElement('div');
                    meta.className = 'log-meta';
                    const badge = document.createElement('span');
                    badge.className = 'badge ' + item.level;
                    badge.textContent = item.level;
                    const tag = document.createElement('span');
                    tag.className = 'tag';
                    tag.textContent = item.tag;
                    const ts = document.createElement('span');
                    ts.className = 'ts';
                    ts.textContent = item.time;
                    meta.append(badge, tag, ts);
                    const msg = document.createElement('div');
                    msg.className = 'msg';
                    msg.textContent = item.message;
                    card.append(meta, msg);
                    if (item.stack) {
                      const st = document.createElement('div');
                      st.className = 'stack';
                      st.textContent = item.stack;
                      card.appendChild(st);
                    }
                    list.appendChild(card);
                  }
                }

                async function copyAll() {
                  const btn = document.getElementById('copyBtn');
                  try {
                    await navigator.clipboard.writeText(FULL_REPORT);
                    btn.textContent = 'Copied!';
                    setTimeout(() => btn.textContent = 'Copy Logs + Config', 1800);
                  } catch (e) {
                    window.open('/logs.txt', '_blank');
                  }
                }

                function toggleAuto() {
                  const btn = document.getElementById('autoBtn');
                  if (autoTimer) {
                    clearInterval(autoTimer);
                    autoTimer = null;
                    sessionStorage.removeItem('txa_auto');
                    btn.textContent = 'Auto-refresh: OFF';
                    btn.classList.remove('active');
                  } else {
                    sessionStorage.setItem('txa_auto', '1');
                    btn.textContent = 'Auto-refresh: ON (5s)';
                    btn.classList.add('active');
                    autoTimer = setInterval(() => location.reload(), 5000);
                  }
                }

                if (sessionStorage.getItem('txa_auto') === '1') {
                  toggleAuto();
                }
                renderLogs();
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

