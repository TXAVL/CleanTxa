package com.gathin.tvhome

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class HomeKeyService : AccessibilityService() {
    private var cfg = LauncherConfig()
    private val handler = Handler(Looper.getMainLooper())
    private var learnUntilMs = 0L
    private var learnId: String? = null
    private var learnedId: String? = null
    private var volDir = 0

    private val volRunnable = object : Runnable {
        override fun run() {
            if (volDir != 0) {
                val am = getSystemService(AudioManager::class.java)
                am?.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (volDir > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
                handler.postDelayed(this, 140)
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ConfigStore.ACTION_RELOAD_CONFIG -> cfg = ConfigStore.load(this@HomeKeyService)
                ConfigStore.ACTION_LEARN_KEY, "com.gathin.tvhome.LEARN_KEY" -> {
                    val id = intent.getStringExtra("id")
                    if (id == null || id != learnedId) {
                        learnId = id
                        learnUntilMs = System.currentTimeMillis() + 20_000L
                    }
                }
                ConfigStore.ACTION_TYPE_TEXT, "com.gathin.tvhome.TYPE_TEXT" -> {
                    val text = intent.getStringExtra("b64")?.let {
                        runCatching { String(android.util.Base64.decode(it, android.util.Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
                    } ?: intent.getStringExtra("text") ?: return
                    if (injectText(text)) resultCode = 1
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = this
        cfg = ConfigStore.load(this)
        val filter = IntentFilter().apply {
            addAction(ConfigStore.ACTION_RELOAD_CONFIG)
            addAction(ConfigStore.ACTION_LEARN_KEY)
            addAction("com.gathin.tvhome.LEARN_KEY")
            addAction(ConfigStore.ACTION_TYPE_TEXT)
            addAction("com.gathin.tvhome.TYPE_TEXT")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        if (running === this) running = null
        handler.removeCallbacks(volRunnable)
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!cfg.stockHomeRedirect || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if ((cfg.stockHomePackage.isNotBlank() && pkg == cfg.stockHomePackage) ||
            pkg == "com.google.android.tvlauncher" ||
            pkg == "com.google.android.apps.tv.launcherx" ||
            pkg == "com.google.android.leanbacklauncher" ||
            pkg == "com.mitv.tvhome"
        ) {
            launchHome()
        }
    }

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val scan = event.scanCode

        if (System.currentTimeMillis() < learnUntilMs && event.action == KeyEvent.ACTION_DOWN) {
            learnUntilMs = 0L
            val idPart = learnId?.let { " id=$it" }.orEmpty()
            learnedId = learnId
            android.util.Log.i("CleanTxaKey", "key=$code scan=$scan$idPart")
            sendBroadcast(
                Intent(ConfigStore.ACTION_KEY_LEARNED)
                    .setPackage(packageName)
                    .putExtra("keycode", code)
                    .putExtra("scancode", scan)
            )
            return true
        }

        if (cfg.volumeKeyRepeat && (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                volDir = if (code == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1
                handler.removeCallbacks(volRunnable)
                handler.postDelayed(volRunnable, 350)
            } else if (event.action == KeyEvent.ACTION_UP) {
                volDir = 0
                handler.removeCallbacks(volRunnable)
            }
            return false
        }

        if (event.action != KeyEvent.ACTION_DOWN) return false

        if (code == KeyEvent.KEYCODE_HOME && cfg.stockHomeRedirect) {
            launchHome()
            return true
        }

        if (!isNavigationKey(code)) {
            // Mở đè Home gốc trên Google TV (4.1.5): vẫn xử lý nút app và nút Cài đặt từ bảng phím remote.
            val mappedTarget = cfg.appKeys[code] ?: cfg.appScancodes[scan]
            if (mappedTarget != null) {
                launchTarget(mappedTarget)
                return true
            }

            if (code in cfg.settingsKeycodes || scan in cfg.settingsKeycodes) {
                openSystemSettings()
                return true
            }
        }

        return false
    }

    private fun isNavigationKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BACK -> true
        else -> false
    }

    private fun launchHome(openLibrary: Boolean = false) {
        runCatching {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            if (openLibrary) intent.action = ConfigStore.ACTION_OPEN_LIBRARY
            startActivity(intent)
        }
    }

    private fun openSystemSettings() {
        val primary = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(primary) }.isFailure) {
            // Fallback cho TCL khi Settings.ACTION_SETTINGS bị chặn.
            runCatching {
                packageManager.getLeanbackLaunchIntentForPackage("com.android.tv.settings")
                    ?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }

    private fun launchTarget(target: String) {
        when (target) {
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "library" -> launchHome(openLibrary = true)
            "sleep" -> if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            else -> {
                val i = packageManager.getLeanbackLaunchIntentForPackage(target)
                    ?: packageManager.getLaunchIntentForPackage(target)
                    ?: return
                runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }

    private fun injectText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    companion object {
        @Volatile
        private var running: HomeKeyService? = null

        fun recentsNow(): Boolean = running?.performGlobalAction(GLOBAL_ACTION_RECENTS) == true

        fun sleepNow(): Boolean =
            Build.VERSION.SDK_INT >= 28 && running?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
    }
}

