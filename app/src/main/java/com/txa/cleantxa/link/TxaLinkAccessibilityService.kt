package com.txa.cleantxa.link

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * Dịch vụ trợ năng chạy ngầm trên TV: thực hiện điều hướng D-Pad, bấm OK, Home, Back, Đa nhiệm, Ngủ và gõ tiếng Việt
 * có dấu trực tiếp từ điện thoại đã tin cậy mà KHÔNG cần bật ADB.
 */
class TxaLinkAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun performRemoteKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
        KeyEvent.KEYCODE_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
        KeyEvent.KEYCODE_APP_SWITCH -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        KeyEvent.KEYCODE_MENU -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_SLEEP -> {
            if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            else performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> clickFocusedNode()
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT -> moveFocusDirection(keyCode)
        KeyEvent.KEYCODE_DEL -> deleteLastChar()
        else -> false
    }

    private fun clickFocusedNode(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: findFocusedRecursive(root)
            ?: return false
        var cur: AccessibilityNodeInfo? = focused
        while (cur != null) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur.parent
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun moveFocusDirection(keyCode: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = ArrayList<AccessibilityNodeInfo>()
        collectFocusableNodes(root, candidates)
        if (candidates.isEmpty()) return false

        val current = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFocusedRecursive(root)
        if (current == null) {
            return candidates.first().performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }

        val curRect = Rect().also { current.getBoundsInScreen(it) }
        val cx = curRect.centerX()
        val cy = curRect.centerY()

        var best: AccessibilityNodeInfo? = null
        var bestScore = Long.MAX_VALUE
        for (node in candidates) {
            if (node == current) continue
            val r = Rect().also { node.getBoundsInScreen(it) }
            if (r.isEmpty) continue
            val nx = r.centerX()
            val ny = r.centerY()
            val dx = nx - cx
            val dy = ny - cy
            val primary: Int
            val cross: Int
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { if (dy >= -4) continue; primary = -dy; cross = abs(dx) }
                KeyEvent.KEYCODE_DPAD_DOWN -> { if (dy <= 4) continue; primary = dy; cross = abs(dx) }
                KeyEvent.KEYCODE_DPAD_LEFT -> { if (dx >= -4) continue; primary = -dx; cross = abs(dy) }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { if (dx <= 4) continue; primary = dx; cross = abs(dy) }
                else -> continue
            }
            val score = primary.toLong() * 10L + cross.toLong() * 28L
            if (score < bestScore) {
                bestScore = score
                best = node
            }
        }

        if (best != null && best.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
            return true
        }

        // Ở mép danh sách cuộn: cuộn khung cha rồi thử lại.
        var scrollable: AccessibilityNodeInfo? = current.parent
        while (scrollable != null && !scrollable.isScrollable) scrollable = scrollable.parent
        if (scrollable != null) {
            val action = if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            return scrollable.performAction(action)
        }
        return false
    }

    private fun collectFocusableNodes(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null || !node.isVisibleToUser) return
        if (node.isFocusable && node.isEnabled) out += node
        for (i in 0 until node.childCount) {
            collectFocusableNodes(node.getChild(i), out)
        }
    }

    private fun findFocusedRecursive(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused) return node
        for (i in 0 until node.childCount) {
            findFocusedRecursive(node.getChild(i))?.let { return it }
        }
        return null
    }

    fun injectText(text: String, append: Boolean = true): Boolean {
        val field = editableFocus() ?: return false
        val existing = if (!append || field.isShowingHintText) "" else field.text?.toString().orEmpty()
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, existing + text)
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun deleteLastChar(): Boolean {
        val field = editableFocus() ?: return false
        if (field.isShowingHintText) return false
        val cur = field.text?.toString().orEmpty()
        if (cur.isEmpty()) return true
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, cur.dropLast(1))
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun editableFocus(): AccessibilityNodeInfo? {
        val focusInput = AccessibilityNodeInfo.FOCUS_INPUT
        findFocus(focusInput)?.takeIf { it.isEditable }?.let { return it }
        rootInActiveWindow?.findFocus(focusInput)?.takeIf { it.isEditable }?.let { return it }
        for (w in windows) {
            w.root?.findFocus(focusInput)?.takeIf { it.isEditable }?.let { return it }
        }
        return null
    }

    /** Đọc văn bản đang hiển thị trong ô nhập hoặc phần tử đang được trỏ trên màn hình TV. */
    fun readFocusedText(): String {
        editableFocus()?.let { ed ->
            if (!ed.isShowingHintText) {
                val txt = ed.text?.toString().orEmpty()
                if (txt.isNotBlank()) return txt
            }
        }
        val root = rootInActiveWindow ?: return ""
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: findFocusedRecursive(root)
        return node?.text?.toString()?.takeIf { it.isNotBlank() }
            ?: node?.contentDescription?.toString().orEmpty()
    }

    /** Chụp màn hình TV trực tiếp qua quyền Trợ năng (Android 11+, hoàn toàn KHÔNG cần ADB). */
    fun captureScreenJpeg(timeoutMs: Long = 5_000L): ByteArray? {
        if (Build.VERSION.SDK_INT < 30) return null
        val latch = java.util.concurrent.CountDownLatch(1)
        var outBytes: ByteArray? = null
        runCatching {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val hw = screenshot.hardwareBuffer
                            val bmp = android.graphics.Bitmap.wrapHardwareBuffer(hw, screenshot.colorSpace)
                            hw.close()
                            if (bmp != null) {
                                val soft = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                bmp.recycle()
                                val bos = java.io.ByteArrayOutputStream()
                                soft.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, bos)
                                soft.recycle()
                                outBytes = bos.toByteArray()
                            }
                        } catch (_: Throwable) {
                        } finally {
                            latch.countDown()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        latch.countDown()
                    }
                },
            )
            latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
        return outBytes
    }

    companion object {
        @Volatile
        var instance: TxaLinkAccessibilityService? = null
            private set

        fun isEnabled(ctx: Context): Boolean {
            if (instance != null) return true
            val flat = ComponentName(ctx, TxaLinkAccessibilityService::class.java).flattenToString()
            val shortFlat = ComponentName(ctx, TxaLinkAccessibilityService::class.java).flattenToShortString()
            val enabled = runCatching {
                Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            }.getOrDefault("")
            return enabled.split(':').any { it.equals(flat, ignoreCase = true) || it.equals(shortFlat, ignoreCase = true) }
        }
    }
}
