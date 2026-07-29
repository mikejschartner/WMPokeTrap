package com.whiskeymike.wmpoketrap.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.whiskeymike.wmpoketrap.bot.ScreenPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class TrapAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    suspend fun tap(point: ScreenPoint, durationMs: Long = 50L): Boolean {
        if (!point.valid()) return false
        return tap(point.x.toFloat(), point.y.toFloat(), durationMs)
    }

    suspend fun tap(x: Float, y: Float, durationMs: Long = 50L): Boolean =
        suspendCancellableCoroutine { cont ->
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(30))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val ok = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
            if (!ok && cont.isActive) cont.resume(false)
        }

    suspend fun swipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long = 220L,
    ): Boolean = suspendCancellableCoroutine { cont ->
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(25))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume(false)
            }
        }, null)
        if (!ok && cont.isActive) cont.resume(false)
    }

    /**
     * Press-and-hold at one point (no drag). Used for joystick-style walking so
     * an encounter can't pan the battle UI from a swipe gesture.
     */
    suspend fun hold(x: Float, y: Float, durationMs: Long): Boolean =
        tap(x, y, durationMs.coerceAtLeast(40L))

    /**
     * Dispatching a new gesture cancels any in-flight swipe/tap.
     * Uses a 1ms stroke in the corner so it shouldn't hit battle UI.
     */
    fun interruptGestures() {
        val path = Path().apply { moveTo(2f, 2f) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    suspend fun screenshot(): Bitmap? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            executor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val bmp = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace,
                        )?.copy(Bitmap.Config.ARGB_8888, false)
                        screenshot.hardwareBuffer.close()
                        if (cont.isActive) cont.resume(bmp)
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
            },
        )
    }

    companion object {
        @Volatile var instance: TrapAccessibilityService? = null
        private val executor = Executors.newSingleThreadExecutor()
        fun isEnabled(): Boolean = instance != null
    }
}
