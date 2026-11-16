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
 *
 * Đường dẫn: `/` trang web, `/logs.json` dữ liệu cho tự làm mới, `/logs.txt` bản thô, `/download` tải về.
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

                val out = s.getOutputStream()
                when (path) {
                    "/logs.txt" -> writeResponse(
                        out = out,
                        contentType = "text/plain; charset=utf-8",
                        body = TxaLogger.fullReport(ctx, extra).toByteArray(Charsets.UTF_8),
                    )
                    "/download" -> writeResponse(
                        out = out,
                        contentType = "text/plain; charset=utf-8",
                        body = TxaLogger.fullReport(ctx, extra).toByteArray(Charsets.UTF_8),
                        extraHeaders = listOf("Content-Disposition: attachment; filename=\"cleantxa-logs.txt\""),
                    )
                    "/logs.json" -> writeResponse(
                        out = out,
                        contentType = "application/json; charset=utf-8",
                        body = entriesJson().toString().toByteArray(Charsets.UTF_8),
                    )
                    else -> writeResponse(
                        out = out,
                        contentType = "text/html; charset=utf-8",
                        body = buildHtmlPage(ctx, extra).toByteArray(Charsets.UTF_8),
                    )
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

    /** Toàn bộ log hiện có, mới nhất trước; `n` là số thứ tự (1 = cũ nhất) để trang web nhớ dòng nào đang mở rộng. */
    private fun entriesJson(): JSONObject {
        val entries = TxaLogger.entries.toList().asReversed()
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val arr = JSONArray()
        entries.forEachIndexed { i, e ->
            arr.put(
                JSONObject().apply {
                    put("n", entries.size - i)
                    put("time", fmt.format(Date(e.time)))
                    put("level", e.level.name)
                    put("tag", e.tag)
                    put("message", e.message)
                    put("stack", e.stackTrace ?: "")
                }
            )
        }
        return JSONObject().put("entries", arr)
    }

    private fun buildHtmlPage(ctx: Context, extra: String?): String {
        val pairs = TxaLogger.deviceConfigPairs(ctx, extra)
        val entries = TxaLogger.entries.toList()

        val configCards = pairs.joinToString("\n") { (k, v) ->
            """<div class="cfg-item"><div class="cfg-k">${escapeHtml(k)}</div><div class="cfg-v">${escapeHtml(v)}</div></div>"""
        }
        val safeLogsJson = entriesJson().toString().replace("</", "<\\/")

        return """
            <!doctype html>
            <html lang="vi">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width,initial-scale=1" />
              <meta name="color-scheme" content="dark light" />
              <title>CleanTxa v${escapeHtml(BuildConfig.VERSION_NAME)} · Web Logs (Port $PORT)</title>
              <style>
                :root {
                  --bg: #071017; --card: #0E1924; --card2: #132230; --border: #1E3245;
                  --text: #E9F2F7; --muted: #8AA3B5; --accent: #2EC4B6; --on-accent: #05161A;
                  --info: #38BDF8; --warn: #FBBF24; --err: #F87171; --msg: #DCE8F0; --stack: #FCA5A5;
                }
                :root[data-theme="light"] {
                  --bg: #F3F7F9; --card: #FFFFFF; --card2: #EEF3F6; --border: #D3DEE5;
                  --text: #11202B; --muted: #55697A; --accent: #00796B; --on-accent: #FFFFFF;
                  --info: #0369A1; --warn: #A16207; --err: #B91C1C; --msg: #1B2C38; --stack: #9F1239;
                }
                * { box-sizing: border-box; }
                body { margin: 0; font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif; background: var(--bg); color: var(--text); }
                header { position: sticky; top: 0; z-index: 10; padding: 12px 16px; background: var(--card); border-bottom: 1px solid var(--border); display: flex; flex-wrap: wrap; gap: 10px; align-items: center; justify-content: space-between; }
                .brand { display: flex; align-items: center; gap: 10px; min-width: 0; }
                .dot { width: 10px; height: 10px; border-radius: 50%; background: var(--muted); flex: none; }
                .dot.live { background: var(--accent); box-shadow: 0 0 8px var(--accent); }
                .dot.off { background: var(--err); }
                h1 { margin: 0; font-size: 16px; font-weight: 700; }
                .sub { font-size: 12px; color: var(--muted); }
                .actions { display: flex; gap: 6px; flex-wrap: wrap; }
                a.btn, button { font: inherit; background: var(--accent); color: var(--on-accent); border: 0; border-radius: 8px; padding: 8px 12px; font-weight: 700; font-size: 12.5px; text-decoration: none; cursor: pointer; min-height: 36px; }
                a.btn:hover, button:hover { opacity: 0.9; }
                a.btn.sec, button.sec { background: var(--card2); color: var(--text); border: 1px solid var(--border); }
                button.sec.active { border-color: var(--accent); color: var(--accent); }
                main { padding: 16px; max-width: 1180px; margin: 0 auto; display: grid; gap: 14px; }
                .stats { display: flex; flex-wrap: wrap; gap: 8px; }
                .pill { font: inherit; background: var(--card); color: var(--text); border: 1px solid var(--border); border-radius: 999px; padding: 6px 14px; font-size: 12.5px; font-weight: 600; display: flex; align-items: center; gap: 6px; min-height: 34px; }
                .pill.on { border-color: var(--accent); }
                .pill b { font-size: 14px; }
                .pill.info b { color: var(--info); }
                .pill.warn b { color: var(--warn); }
                .pill.err b { color: var(--err); }
                section, details.card { background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 14px; }
                summary { cursor: pointer; list-style: none; }
                summary::-webkit-details-marker { display: none; }
                h2 { margin: 0; font-size: 13px; color: var(--accent); text-transform: uppercase; letter-spacing: 0.06em; }
                .cfg-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 8px; margin-top: 12px; }
                .cfg-item { background: var(--card2); border: 1px solid var(--border); border-radius: 10px; padding: 8px 10px; }
                .cfg-k { font-size: 11px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; margin-bottom: 3px; }
                .cfg-v { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; word-break: break-word; }
                .toolbar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin: 12px 0; }
                .chips { display: flex; gap: 6px; flex-wrap: wrap; }
                .chip { background: var(--card2); color: var(--muted); border: 1px solid var(--border); font-weight: 600; font-size: 12px; }
                .chip.on { background: transparent; color: var(--accent); border-color: var(--accent); }
                .search, select { font: inherit; background: var(--card2); color: var(--text); border: 1px solid var(--border); border-radius: 8px; padding: 8px 12px; font-size: 13px; outline: none; min-height: 36px; }
                .search { flex: 1; min-width: 180px; }
                .search:focus, select:focus { border-color: var(--accent); }
                .log-list { display: grid; gap: 8px; }
                .log-card { background: var(--card2); border: 1px solid var(--border); border-left-width: 4px; border-radius: 10px; padding: 10px 12px; }
                .log-card.INFO { border-left-color: var(--info); }
                .log-card.WARN { border-left-color: var(--warn); }
                .log-card.ERROR { border-left-color: var(--err); }
                .log-meta { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 5px; font-size: 12px; }
                .badge { padding: 2px 7px; border-radius: 5px; font-weight: 700; font-size: 11px; border: 1px solid currentColor; }
                .badge.INFO { color: var(--info); }
                .badge.WARN { color: var(--warn); }
                .badge.ERROR { color: var(--err); }
                .tag { font-weight: 700; word-break: break-all; }
                .ts { color: var(--muted); font-family: ui-monospace, monospace; margin-left: auto; }
                .cp { background: transparent; color: var(--muted); border: 1px solid var(--border); padding: 2px 8px; min-height: 26px; font-size: 11.5px; font-weight: 600; }
                .msg { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; line-height: 1.48; white-space: pre-wrap; word-break: break-word; color: var(--msg); }
                .msg.clamp { display: -webkit-box; -webkit-line-clamp: 6; -webkit-box-orient: vertical; overflow: hidden; }
                .more { background: transparent; color: var(--accent); padding: 4px 0 0; min-height: 0; font-size: 12px; }
                .stack { margin-top: 7px; padding-top: 7px; border-top: 1px dashed var(--border); font-family: ui-monospace, monospace; font-size: 11.5px; color: var(--stack); white-space: pre-wrap; word-break: break-word; }
                .empty { text-align: center; padding: 28px; color: var(--muted); font-size: 13px; }
                @media (max-width: 560px) {
                  header { padding: 10px 12px; }
                  main { padding: 12px; }
                  .actions { width: 100%; }
                  .actions > * { flex: 1 1 auto; text-align: center; }
                  .ts { margin-left: 0; }
                }
              </style>
            </head>
            <body>
              <header>
                <div class="brand">
                  <span id="dot" class="dot"></span>
                  <div>
                    <h1>CleanTxa v${escapeHtml(BuildConfig.VERSION_NAME)} · Web Logs</h1>
                    <div class="sub">Port $PORT · <span id="status">…</span></div>
                  </div>
                </div>
                <div class="actions">
                  <button id="copyBtn" onclick="copyAll()">Copy Logs + Config</button>
                  <a class="btn sec" href="/logs.txt" target="_blank">Raw .txt</a>
                  <a class="btn sec" href="/download">Download</a>
                  <button id="liveBtn" class="sec" onclick="toggleLive()">Live</button>
                  <button id="themeBtn" class="sec" onclick="toggleTheme()">Theme</button>
                </div>
              </header>
              <main>
                <div class="stats">
                  <button class="pill on" data-lv="ALL" onclick="setLevel('ALL')">Total <b id="cAll">${entries.size}</b></button>
                  <button class="pill info" data-lv="INFO" onclick="setLevel('INFO')">INFO <b id="cInfo">0</b></button>
                  <button class="pill warn" data-lv="WARN" onclick="setLevel('WARN')">WARN <b id="cWarn">0</b></button>
                  <button class="pill err" data-lv="ERROR" onclick="setLevel('ERROR')">ERROR <b id="cErr">0</b></button>
                </div>
                <details id="cfg" class="card" open>
                  <summary><h2>Device &amp; App Configuration</h2></summary>
                  <div class="cfg-grid">$configCards</div>
                </details>
                <section>
                  <h2>Logs (<span id="shownCount">0</span>)</h2>
                  <div class="toolbar">
                    <input id="search" class="search" type="search" placeholder="Search tag, message, stack..." oninput="render()" />
                    <select id="tagSel" onchange="setTag(this.value)"><option value="ALL">All tags</option></select>
                    <button id="sortBtn" class="sec" onclick="toggleSort()">Newest first</button>
                  </div>
                  <div id="logList" class="log-list"></div>
                </section>
              </main>
              <script>
                var LOGS = ${safeLogsJson}.entries;
                var level = 'ALL', tag = 'ALL', newestFirst = true, timer = null, failed = 0;
                var expanded = {};

                function store(k, v) { try { if (v === null) localStorage.removeItem(k); else localStorage.setItem(k, v); } catch (e) {} }
                function load(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }

                function applyTheme(t) {
                  document.documentElement.setAttribute('data-theme', t);
                  document.getElementById('themeBtn').textContent = t === 'light' ? 'Dark' : 'Light';
                }
                function toggleTheme() {
                  var t = document.documentElement.getAttribute('data-theme') === 'light' ? 'dark' : 'light';
                  store('txa_theme', t); applyTheme(t);
                }
                (function () {
                  var t = load('txa_theme');
                  if (!t) t = window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
                  applyTheme(t);
                  if (window.innerWidth < 700) document.getElementById('cfg').removeAttribute('open');
                })();

                function setLevel(lv) {
                  level = lv;
                  document.querySelectorAll('.pill').forEach(function (p) { p.classList.toggle('on', p.getAttribute('data-lv') === lv); });
                  render();
                }
                function setTag(v) { tag = v; render(); }
                function toggleSort() {
                  newestFirst = !newestFirst;
                  document.getElementById('sortBtn').textContent = newestFirst ? 'Newest first' : 'Oldest first';
                  render();
                }

                function updateCounts() {
                  var c = { INFO: 0, WARN: 0, ERROR: 0 };
                  LOGS.forEach(function (e) { if (c[e.level] !== undefined) c[e.level]++; });
                  document.getElementById('cAll').textContent = LOGS.length;
                  document.getElementById('cInfo').textContent = c.INFO;
                  document.getElementById('cWarn').textContent = c.WARN;
                  document.getElementById('cErr').textContent = c.ERROR;
                }
                function updateTags() {
                  var sel = document.getElementById('tagSel');
                  var seen = {};
                  LOGS.forEach(function (e) { seen[e.tag] = true; });
                  var tags = Object.keys(seen).sort();
                  if (tag !== 'ALL' && !seen[tag]) tag = 'ALL';
                  sel.innerHTML = '';
                  var all = document.createElement('option');
                  all.value = 'ALL'; all.textContent = 'All tags';
                  sel.appendChild(all);
                  tags.forEach(function (t) {
                    var o = document.createElement('option');
                    o.value = t; o.textContent = t;
                    sel.appendChild(o);
                  });
                  sel.value = tag;
                }

                function entryText(e) {
                  return '[' + e.time + '] ' + e.level + '/' + e.tag + ': ' + e.message + (e.stack ? '\n' + e.stack : '');
                }

                function copyText(text) {
                  if (navigator.clipboard && window.isSecureContext) return navigator.clipboard.writeText(text);
                  return new Promise(function (resolve, reject) {
                    var ta = document.createElement('textarea');
                    ta.value = text;
                    ta.style.position = 'fixed'; ta.style.opacity = '0';
                    document.body.appendChild(ta);
                    ta.focus(); ta.select();
                    var ok = false;
                    try { ok = document.execCommand('copy'); } catch (e) {}
                    document.body.removeChild(ta);
                    if (ok) resolve(); else reject(new Error('copy failed'));
                  });
                }
                function flash(btn, text) {
                  var old = btn.getAttribute('data-label') || btn.textContent;
                  btn.setAttribute('data-label', old);
                  btn.textContent = text;
                  setTimeout(function () { btn.textContent = old; }, 1500);
                }
                function copyAll() {
                  var btn = document.getElementById('copyBtn');
                  fetch('/logs.txt', { cache: 'no-store' })
                    .then(function (r) { return r.text(); })
                    .then(copyText)
                    .then(function () { flash(btn, 'Copied!'); })
                    .catch(function () { window.open('/logs.txt', '_blank'); });
                }

                function render() {
                  var q = (document.getElementById('search').value || '').toLowerCase();
                  var list = document.getElementById('logList');
                  var rows = LOGS.filter(function (e) {
                    if (level !== 'ALL' && e.level !== level) return false;
                    if (tag !== 'ALL' && e.tag !== tag) return false;
                    if (!q) return true;
                    return (e.tag + ' ' + e.message + ' ' + e.stack).toLowerCase().indexOf(q) >= 0;
                  });
                  if (!newestFirst) rows = rows.slice().reverse();
                  document.getElementById('shownCount').textContent = rows.length;
                  list.innerHTML = '';
                  if (rows.length === 0) {
                    var empty = document.createElement('div');
                    empty.className = 'empty';
                    empty.textContent = 'No matching log entries.';
                    list.appendChild(empty);
                    return;
                  }
                  rows.forEach(function (e) {
                    var card = document.createElement('div');
                    card.className = 'log-card ' + e.level;
                    var meta = document.createElement('div');
                    meta.className = 'log-meta';
                    var badge = document.createElement('span');
                    badge.className = 'badge ' + e.level;
                    badge.textContent = e.level;
                    var tg = document.createElement('span');
                    tg.className = 'tag';
                    tg.textContent = e.tag;
                    var ts = document.createElement('span');
                    ts.className = 'ts';
                    ts.textContent = e.time;
                    var cp = document.createElement('button');
                    cp.className = 'cp';
                    cp.textContent = 'Copy';
                    cp.onclick = function () {
                      copyText(entryText(e)).then(function () { flash(cp, 'Copied'); }, function () { flash(cp, 'Failed'); });
                    };
                    meta.appendChild(badge); meta.appendChild(tg); meta.appendChild(ts); meta.appendChild(cp);
                    var msg = document.createElement('div');
                    msg.className = 'msg';
                    msg.textContent = e.message;
                    card.appendChild(meta); card.appendChild(msg);
                    var long = e.message.split('\n').length > 6 || e.message.length > 480;
                    if (long && !expanded[e.n]) {
                      msg.className = 'msg clamp';
                      var more = document.createElement('button');
                      more.className = 'more';
                      more.textContent = 'Show more';
                      more.onclick = function () { expanded[e.n] = true; render(); };
                      card.appendChild(more);
                    }
                    if (e.stack) {
                      var st = document.createElement('div');
                      st.className = 'stack';
                      st.textContent = e.stack;
                      card.appendChild(st);
                    }
                    list.appendChild(card);
                  });
                }

                function setStatus(text, cls) {
                  document.getElementById('status').textContent = text;
                  document.getElementById('dot').className = 'dot' + (cls ? ' ' + cls : '');
                }
                function stamp() {
                  var d = new Date();
                  function p(n) { return (n < 10 ? '0' : '') + n; }
                  setStatus(LOGS.length + ' entries · updated ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds()), timer ? 'live' : '');
                }
                function refresh() {
                  if (document.hidden) return;
                  fetch('/logs.json', { cache: 'no-store' })
                    .then(function (r) { return r.json(); })
                    .then(function (d) {
                      failed = 0;
                      LOGS = d.entries;
                      updateCounts(); updateTags(); render(); stamp();
                    })
                    .catch(function () {
                      failed++;
                      if (failed >= 2) setStatus('connection lost - is CleanTxa still open on the device?', 'off');
                    });
                }
                function toggleLive() {
                  var btn = document.getElementById('liveBtn');
                  if (timer) {
                    clearInterval(timer); timer = null;
                    store('txa_live', '0');
                    btn.textContent = 'Live: OFF'; btn.classList.remove('active');
                  } else {
                    store('txa_live', '1');
                    btn.textContent = 'Live: ON'; btn.classList.add('active');
                    timer = setInterval(refresh, 3000);
                    refresh();
                  }
                  stamp();
                }

                updateCounts(); updateTags(); render();
                if (load('txa_live') === '0') { document.getElementById('liveBtn').textContent = 'Live: OFF'; stamp(); }
                else toggleLive();
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
