package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.protocol.InputEvent
import com.example.protocol.ProtocolConstants
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class UbaidAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning.set(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op for events, service acts as remote input receiver
    }

    override fun onInterrupt() {
        // Interrupted by system
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        isRunning.set(false)
    }

    suspend fun executeInputEvent(event: InputEvent): Pair<Boolean, String> {
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(metrics)

        val screenW = if (metrics.widthPixels > 0) metrics.widthPixels else 1080
        val screenH = if (metrics.heightPixels > 0) metrics.heightPixels else 2400

        return when (event.action) {
            ProtocolConstants.ACTION_TAP -> {
                val px = event.xNormalized * screenW
                val py = event.yNormalized * screenH
                dispatchTapGesture(px, py, event.durationMs.coerceAtLeast(40L))
            }
            ProtocolConstants.ACTION_DOUBLE_TAP -> {
                val px = event.xNormalized * screenW
                val py = event.yNormalized * screenH
                val first = dispatchTapGesture(px, py, 40L)
                if (first.first) {
                    kotlinx.coroutines.delay(100)
                    dispatchTapGesture(px, py, 40L)
                } else {
                    first
                }
            }
            ProtocolConstants.ACTION_LONG_PRESS -> {
                val px = event.xNormalized * screenW
                val py = event.yNormalized * screenH
                dispatchTapGesture(px, py, 650L)
            }
            ProtocolConstants.ACTION_SWIPE, ProtocolConstants.ACTION_SCROLL -> {
                dispatchSwipeGesture(event, screenW, screenH)
            }
            ProtocolConstants.ACTION_GLOBAL_BACK -> {
                val success = performGlobalAction(GLOBAL_ACTION_BACK)
                Pair(success, if (success) "SUCCESS" else "BACK_REJECTED")
            }
            ProtocolConstants.ACTION_GLOBAL_HOME -> {
                val success = performGlobalAction(GLOBAL_ACTION_HOME)
                Pair(success, if (success) "SUCCESS" else "HOME_REJECTED")
            }
            ProtocolConstants.ACTION_GLOBAL_RECENTS -> {
                val success = performGlobalAction(GLOBAL_ACTION_RECENTS)
                Pair(success, if (success) "SUCCESS" else "RECENTS_REJECTED")
            }
            ProtocolConstants.ACTION_GLOBAL_NOTIFICATIONS -> {
                val success = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                Pair(success, if (success) "SUCCESS" else "NOTIFICATIONS_REJECTED")
            }
            ProtocolConstants.ACTION_GLOBAL_QUICK_SETTINGS -> {
                val success = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                Pair(success, if (success) "SUCCESS" else "QUICK_SETTINGS_REJECTED")
            }
            ProtocolConstants.ACTION_GLOBAL_LOCK_SCREEN -> {
                val success = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                Pair(success, if (success) "SUCCESS" else "LOCK_SCREEN_REJECTED")
            }
            ProtocolConstants.ACTION_GLOBAL_TAKE_SCREENSHOT -> {
                val success = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                Pair(success, if (success) "SUCCESS" else "SCREENSHOT_REJECTED")
            }
            else -> Pair(false, "UNSUPPORTED_INPUT_ACTION")
        }
    }

    private suspend fun dispatchTapGesture(x: Float, y: Float, durationMs: Long): Pair<Boolean, String> {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return suspendCancellableCoroutine { continuation ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    continuation.resume(Pair(true, "SUCCESS"))
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    continuation.resume(Pair(false, "GESTURE_CANCELLED"))
                }
            }
            val dispatched = dispatchGesture(gesture, callback, null)
            if (!dispatched) {
                continuation.resume(Pair(false, "DISPATCH_REJECTED_BY_SYSTEM"))
            }
        }
    }

    private suspend fun dispatchSwipeGesture(
        event: InputEvent,
        screenW: Int,
        screenH: Int
    ): Pair<Boolean, String> {
        val path = Path()
        if (event.pathPoints.size >= 2) {
            val start = event.pathPoints.first()
            path.moveTo(start.x * screenW, start.y * screenH)
            for (i in 1 until event.pathPoints.size) {
                val pt = event.pathPoints[i]
                path.lineTo(pt.x * screenW, pt.y * screenH)
            }
        } else {
            val startX = event.xNormalized * screenW
            val startY = event.yNormalized * screenH
            val endY = (startY - 300f).coerceAtLeast(0f)
            path.moveTo(startX, startY)
            path.lineTo(startX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, event.durationMs.coerceAtLeast(150L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return suspendCancellableCoroutine { continuation ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    continuation.resume(Pair(true, "SUCCESS"))
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    continuation.resume(Pair(false, "SWIPE_CANCELLED"))
                }
            }
            val dispatched = dispatchGesture(gesture, callback, null)
            if (!dispatched) {
                continuation.resume(Pair(false, "SWIPE_DISPATCH_REJECTED"))
            }
        }
    }

    fun injectText(text: String, append: Boolean): Pair<Boolean, String> {
        val rootNode = rootInActiveWindow ?: return Pair(false, "NO_ACTIVE_WINDOW")
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: rootNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = Bundle().apply {
                val currentText = focusedNode.text?.toString() ?: ""
                val target = if (append) currentText + text else text
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, target)
            }
            val performed = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            return Pair(performed, if (performed) "SUCCESS" else "ACTION_SET_TEXT_FAILED")
        }

        return Pair(false, "NO_EDITABLE_FOCUSED_INPUT")
    }

    companion object {
        @Volatile
        private var instance: UbaidAccessibilityService? = null
        private val isRunning = AtomicBoolean(false)

        fun getInstance(): UbaidAccessibilityService? = instance

        fun isServiceRunning(): Boolean = isRunning.get() && instance != null
    }
}
