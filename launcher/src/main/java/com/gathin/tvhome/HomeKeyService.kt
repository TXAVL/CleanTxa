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
                ConfigStore.ACTION_LEARN_KEY -> learnUntilMs = System.currentTimeMillis() + 20_000L
                ConfigStore.ACTION_TYPE_TEXT -> {
                    val text = intent.getStringExtra("text") ?: return
                    injectText(text)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        cfg = ConfigStore.load(this)
        val filter = IntentFilter().apply {
            addAction(ConfigStore.ACTION_RELOAD_CONFIG)
            addAction(ConfigStore.ACTION_LEARN_KEY)
            addAction(ConfigStore.ACTION_TYPE_TEXT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(volRunnable)
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!cfg.stockHomeRedirect || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (pkg == "com.google.android.tvlauncher" ||
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

        val mappedPkg = cfg.appKeys[code] ?: cfg.appKeys[scan]
        if (mappedPkg != null) {
            launchApp(mappedPkg)
            return true
        }

        if (code in cfg.settingsKeycodes || scan in cfg.settingsKeycodes) {
            runCatching {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return true
        }

        return false
    }

    private fun launchHome() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        }
    }

    private fun launchApp(pkg: String) {
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
            ?: return
        runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun injectText(text: String) {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}

