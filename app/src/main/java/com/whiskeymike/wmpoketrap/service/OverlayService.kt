package com.whiskeymike.wmpoketrap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.whiskeymike.wmpoketrap.MainActivity
import com.whiskeymike.wmpoketrap.R
import com.whiskeymike.wmpoketrap.bot.BotEngine
import com.whiskeymike.wmpoketrap.bot.ScreenPoint
import com.whiskeymike.wmpoketrap.bot.SettingsRepository
import com.whiskeymike.wmpoketrap.bot.TrapSettings
import com.whiskeymike.wmpoketrap.bot.WnEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max

class OverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settingsRepo by lazy { SettingsRepository(this) }
    private var windowManager: WindowManager? = null
    private var panel: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var statusView: TextView? = null
    private var stickView: TextView? = null
    private var stickParams: WindowManager.LayoutParams? = null
    private var stickSizePx: Int = 0
    private var collectJob: Job? = null
    private var botRunning = false

    @Volatile private var stickCx: Int = 0
    @Volatile private var stickCy: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForeground(42, buildNotification("WMMods ready"))
        showOverlay()
        collectJob = scope.launch {
            val farm = BotEngine.get(this@OverlayService).stats
            val wn = WnEngine.get(this@OverlayService).stats
            launch {
                farm.collectLatest { stats ->
                    if (WnEngine.get(this@OverlayService).stats.value.running) return@collectLatest
                    statusView?.text = if (stats.running) {
                        "● FARM\n${stats.status}"
                    } else {
                        "○ IDLE\n${stats.status}"
                    }
                    setStickPassThrough(stats.running)
                }
            }
            launch {
                wn.collectLatest { stats ->
                    if (!stats.running && !BotEngine.get(this@OverlayService).stats.value.running) {
                        statusView?.text = "○ IDLE\n${stats.status}"
                        setStickPassThrough(false)
                        return@collectLatest
                    }
                    if (!stats.running) return@collectLatest
                    statusView?.text = "● WN  ${stats.wnEntered} in\n${stats.status}"
                    setStickPassThrough(true)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_OVERLAY -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    /** Live center of the draggable walk stick (screen pixels). */
    fun walkStickCenter(): ScreenPoint? {
        if (stickCx > 0 && stickCy > 0) return ScreenPoint(stickCx, stickCy)
        return null
    }

    /** Left/right tilt distance from stick center. */
    fun walkStickHoldOffset(): Float {
        val r = stickSizePx / 2f
        return (r * 0.85f).coerceIn(48f, 180f)
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val pad = (10 * density).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE0F1F14.toInt())
            setPadding(pad, pad, pad, pad)
            elevation = 12f
        }

        val dragBar = TextView(this).apply {
            text = "⠿  DRAG"
            setTextColor(0xFFA78BFA.toInt())
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(pad / 2, pad / 3, pad / 2, pad / 3)
            setBackgroundColor(0xFF1E1B4B.toInt())
            gravity = android.view.Gravity.CENTER
        }

        statusView = TextView(this).apply {
            setTextColor(0xFF22C55E.toInt())
            textSize = 13f
            text = "○ IDLE\nReady"
            setPadding(0, pad / 2, 0, 0)
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        fun actionBtn(label: String, color: Int, onClick: () -> Unit): Button {
            return Button(this@OverlayService).apply {
                text = label
                textSize = 11f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(color)
                setOnClickListener { onClick() }
            }
        }

        val engine = BotEngine.get(this)
        val wn = WnEngine.get(this)
        row.addView(actionBtn("START", 0xFF6D28D9.toInt()) { engine.start() }, lp())
        row.addView(actionBtn("PAUSE", 0xFF334155.toInt()) {
            if (wn.stats.value.running) wn.togglePause() else engine.togglePause()
        }, lp())
        row.addView(actionBtn("STOP", 0xFFDC2626.toInt()) {
            engine.stop()
            wn.stop()
        }, lp())
        row.addView(
            actionBtn("CAL", 0xFF0EA5E9.toInt()) {
                CalibrationWizardService.start(this@OverlayService)
            },
            lp(),
        )

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(
            actionBtn("WN", 0xFF059669.toInt()) { wn.start() },
            lp(),
        )
        row2.addView(
            actionBtn("WN CAL", 0xFF0D9488.toInt()) {
                CalibrationWizardService.start(this@OverlayService, wnOnly = true)
            },
            lp(),
        )
        row2.addView(
            actionBtn("EXIT", 0xFF7F1D1D.toInt()) {
                engine.stop()
                wn.stop()
                stopSelf()
            },
            lp(),
        )

        layout.addView(dragBar)
        layout.addView(statusView)
        layout.addView(row)
        layout.addView(row2)
        panel = layout

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 120
        }
        panelParams = params
        attachPanelDrag(dragBar, layout, params)
        // Place at saved spot, then add + restore stick.
        scope.launch {
            val saved = runCatching { settingsRepo.current() }.getOrNull()
            val dm = resources.displayMetrics
            if (saved != null && saved.overlayPanelX >= 0 && saved.overlayPanelY >= 0) {
                params.x = saved.overlayPanelX
                params.y = saved.overlayPanelY
            }
            windowManager?.addView(layout, params)
            // Clamp after layout knows its size.
            layout.post {
                val maxX = max(0, dm.widthPixels - layout.width)
                val maxY = max(0, dm.heightPixels - layout.height)
                params.x = params.x.coerceIn(0, maxX)
                params.y = params.y.coerceIn(0, maxY)
                runCatching { windowManager?.updateViewLayout(layout, params) }
            }
            showWalkStick(saved)
        }
    }

    private suspend fun showWalkStick(savedSettings: TrapSettings? = null) {
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density
        stickSizePx = (88 * density).toInt()
        val saved = savedSettings ?: runCatching { settingsRepo.current() }.getOrNull()
        val dm = resources.displayMetrics
        val startX = if (saved != null && saved.swipeCenter.valid()) {
            (saved.swipeCenter.x - stickSizePx / 2).coerceIn(0, max(0, dm.widthPixels - stickSizePx))
        } else {
            (dm.widthPixels / 2 - stickSizePx / 2).coerceAtLeast(0)
        }
        val startY = if (saved != null && saved.swipeCenter.valid()) {
            (saved.swipeCenter.y - stickSizePx / 2).coerceIn(0, max(0, dm.heightPixels - stickSizePx))
        } else {
            (dm.heightPixels * 0.72f - stickSizePx / 2).toInt().coerceAtLeast(0)
        }

        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x552E1065.toInt())
            setStroke((3 * density).toInt(), 0xCCA78BFA.toInt())
        }
        val stick = TextView(this).apply {
            text = "◎\nSTICK"
            setTextColor(0xEEDDD6FE.toInt())
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            background = ring
            elevation = 8f
        }
        stickView = stick

        val sp = WindowManager.LayoutParams(
            stickSizePx,
            stickSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }
        stickParams = sp
        refreshStickCenter()
        attachStickDrag(stick, sp)
        wm.addView(stick, sp)
    }

    private fun refreshStickCenter() {
        val sp = stickParams ?: return
        stickCx = sp.x + stickSizePx / 2
        stickCy = sp.y + stickSizePx / 2
    }

    /** Call before injecting walk gestures so the stick isn't under the finger. */
    fun prepareForGestures() {
        refreshStickCenter()
        setStickPassThrough(true)
    }

    fun restoreStickForEditing() {
        setStickPassThrough(false)
    }

    private fun setStickPassThrough(running: Boolean) {
        botRunning = running
        val stick = stickView ?: return
        val sp = stickParams ?: return
        val wm = windowManager ?: return
        // Accessibility gestures often still hit overlays — detach the stick while
        // running so left/right holds go into the game, not our circle.
        if (running) {
            refreshStickCenter()
            if (stick.parent != null) {
                runCatching { wm.removeView(stick) }
            }
        } else {
            stick.alpha = 0.85f
            stick.text = "◎\nSTICK"
            if (stick.parent == null) {
                runCatching { wm.addView(stick, sp) }
            }
        }
    }

    private fun attachStickDrag(view: View, params: WindowManager.LayoutParams) {
        var lastX = 0
        var lastY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            if (botRunning) return@setOnTouchListener false
            val wm = windowManager ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX.toInt()
                    lastY = event.rawY.toInt()
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val x = event.rawX.toInt()
                    val y = event.rawY.toInt()
                    val dx = x - lastX
                    val dy = y - lastY
                    if (dx != 0 || dy != 0) moved = true
                    params.x += dx
                    params.y += dy
                    lastX = x
                    lastY = y
                    val dm = resources.displayMetrics
                    val maxX = max(0, dm.widthPixels - stickSizePx)
                    val maxY = max(0, dm.heightPixels - stickSizePx)
                    params.x = params.x.coerceIn(0, maxX)
                    params.y = params.y.coerceIn(0, maxY)
                    refreshStickCenter()
                    runCatching { wm.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    refreshStickCenter()
                    if (moved) persistStickCenter()
                    true
                }
                else -> true
            }
        }
    }

    private fun persistStickCenter() {
        val cx = stickCx
        val cy = stickCy
        if (cx <= 0 || cy <= 0) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val cur = settingsRepo.current()
                settingsRepo.save(cur.copy(swipeCenter = ScreenPoint(cx, cy)))
            }
        }
        statusView?.text = "○ IDLE\nStick saved ($cx, $cy)"
    }

    private fun persistPanelPos() {
        val sp = panelParams ?: return
        val x = sp.x
        val y = sp.y
        scope.launch(Dispatchers.IO) {
            runCatching {
                val cur = settingsRepo.current()
                settingsRepo.save(cur.copy(overlayPanelX = x, overlayPanelY = y))
            }
        }
        statusView?.text = "○ IDLE\nPanel saved ($x, $y)"
    }

    private fun attachPanelDrag(
        handle: View,
        panelView: View,
        params: WindowManager.LayoutParams,
    ) {
        var lastX = 0
        var lastY = 0
        var moved = false
        handle.setOnTouchListener { _, event ->
            val wm = windowManager ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX.toInt()
                    lastY = event.rawY.toInt()
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val x = event.rawX.toInt()
                    val y = event.rawY.toInt()
                    val dx = x - lastX
                    val dy = y - lastY
                    if (dx != 0 || dy != 0) moved = true
                    params.x += dx
                    params.y += dy
                    lastX = x
                    lastY = y
                    val dm = resources.displayMetrics
                    val maxX = max(0, dm.widthPixels - panelView.width)
                    val maxY = max(0, dm.heightPixels - panelView.height)
                    params.x = params.x.coerceIn(0, maxX)
                    params.y = params.y.coerceIn(0, maxY)
                    runCatching { wm.updateViewLayout(panelView, params) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (moved) persistPanelPos()
                    true
                }
                else -> true
            }
        }
    }

    private fun lp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { setMargins(4, 8, 4, 0) }

    private fun buildNotification(text: String): Notification {
        val channelId = "wm_poketrap_overlay"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "WMMods Overlay", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("WMMods")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        stickView?.let { runCatching { windowManager?.removeView(it) } }
        stickView = null
        stickParams = null
        panel?.let { runCatching { windowManager?.removeView(it) } }
        panel = null
        panelParams = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP_OVERLAY = "com.whiskeymike.wmpoketrap.STOP_OVERLAY"
        @Volatile var instance: OverlayService? = null

        fun walkStickCenterOrNull(): ScreenPoint? = instance?.walkStickCenter()
        fun walkStickHoldOffsetOrDefault(fallback: Float): Float =
            instance?.walkStickHoldOffset() ?: fallback

        fun prepareForGestures() {
            instance?.prepareForGestures()
        }

        fun restoreStickForEditing() {
            instance?.restoreStickForEditing()
        }
    }
}
