package com.whiskeymike.wmpoketrap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.whiskeymike.wmpoketrap.MainActivity
import com.whiskeymike.wmpoketrap.R
import com.whiskeymike.wmpoketrap.bot.CalibrationStep
import com.whiskeymike.wmpoketrap.bot.CalibrationSteps
import com.whiskeymike.wmpoketrap.bot.ScreenPoint
import com.whiskeymike.wmpoketrap.bot.ScreenRect
import com.whiskeymike.wmpoketrap.bot.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.max
import kotlin.math.min

/**
 * Fullscreen overlay wizard that calibrates while the game stays visible underneath.
 * No app-tab switching mid-setup.
 */
class CalibrationWizardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: SettingsRepository
    private var windowManager: WindowManager? = null
    private var root: FrameLayout? = null
    private var hudPanel: LinearLayout? = null
    private var canvasView: CalibrateCanvas? = null
    private var titleView: TextView? = null
    private var hintView: TextView? = null
    private var stepIndex = 0
    private var steps: List<CalibrationStep> = CalibrationSteps.all
    private var rootParams: WindowManager.LayoutParams? = null
    private var navigationButton: Button? = null
    private var wizardAttached = false
    private var hudOffsetX = 24f
    private var hudOffsetY = 80f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(applicationContext)
        val wnOnly = pendingWnOnly
        pendingWnOnly = false
        steps = if (wnOnly) {
            CalibrationSteps.wn
        } else {
            runBlocking { CalibrationSteps.required(repo.current()) }
        }
        startForeground(43, buildNotification(if (wnOnly) "WN calibration wizard" else "Calibration wizard active"))
        showWizard()
        Toast.makeText(
            this,
            "Drag the purple bar to move the prompt off the area",
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun showWizard() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()
        val screenW = resources.displayMetrics.widthPixels

        val canvas = CalibrateCanvas(this) { x1, y1, x2, y2 ->
            onGesture(x1, y1, x2, y2)
        }
        canvasView = canvas

        val hud = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF20B1020.toInt())
            setPadding(pad, pad, pad, pad)
            elevation = 16f
        }
        hudPanel = hud

        // Drag handle — only this strip moves the panel (buttons stay tappable).
        val dragBar = TextView(this).apply {
            text = "⠿  DRAG ME  ·  move this panel out of the way"
            setTextColor(0xFFA78BFA.toInt())
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
            setBackgroundColor(0xFF1E1B4B.toInt())
            gravity = android.view.Gravity.CENTER
        }
        attachHudDrag(dragBar, hud)

        titleView = TextView(this).apply {
            setTextColor(0xFFA78BFA.toInt())
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, pad / 2, 0, 0)
        }
        hintView = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            setPadding(0, (6 * density).toInt(), 0, (8 * density).toInt())
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(
            smallBtn("SKIP") { advance() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, pad / 3, 0)
            },
        )
        row.addView(
            smallBtn("NAVIGATE GAME") { enterGameNavigationMode() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(pad / 3, 0, pad / 3, 0)
            },
        )
        row.addView(
            smallBtn("CANCEL") { finishWizard(cancelled = true) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(pad / 3, 0, 0, 0)
            },
        )

        hud.addView(dragBar)
        hud.addView(titleView)
        hud.addView(hintView)
        hud.addView(row)

        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(0x33000000.toInt())
            addView(
                canvas,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            // Floating panel — ~92% width so edges stay grab/drag friendly.
            val hudW = (screenW * 0.92f).toInt()
            addView(
                hud,
                FrameLayout.LayoutParams(hudW, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = hudOffsetX.toInt()
                    topMargin = hudOffsetY.toInt()
                },
            )
        }
        root = rootLayout
        refreshHud()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        rootParams = params
        windowManager?.addView(rootLayout, params)
        wizardAttached = true
    }

    /** Drag the prompt panel by its purple handle so it doesn't cover the target area. */
    private fun attachHudDrag(handle: View, panel: View) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = panel.x
                    startY = panel.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val rootView = root ?: return@setOnTouchListener true
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    var nx = startX + dx
                    var ny = startY + dy
                    // Keep mostly on-screen so Cancel stays reachable.
                    val maxX = (rootView.width - panel.width).toFloat().coerceAtLeast(0f)
                    val maxY = (rootView.height - panel.height).toFloat().coerceAtLeast(0f)
                    nx = nx.coerceIn(0f, maxX)
                    ny = ny.coerceIn(0f, maxY)
                    panel.x = nx
                    panel.y = ny
                    hudOffsetX = nx
                    hudOffsetY = ny
                    true
                }
                else -> true
            }
        }
    }

    /**
     * Remove the fullscreen wizard window entirely. Android can reject touches through a
     * translucent overlay even when FLAG_NOT_TOUCHABLE is set, so changing flags is not enough.
     * A small separate return button remains; everywhere else is the real game.
     */
    private fun enterGameNavigationMode() {
        val wm = windowManager ?: return
        val rootView = root ?: return
        if (wizardAttached) {
            runCatching { wm.removeView(rootView) }
            wizardAttached = false
        }

        val returnButton = Button(this).apply {
            text = "RETURN TO\nWIZARD"
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF6D28D9.toInt())
            setOnClickListener { exitGameNavigationMode() }
        }
        navigationButton = returnButton
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 100
        }
        wm.addView(returnButton, params)
        Toast.makeText(
            this,
            "Game unlocked — navigate normally, then tap RETURN TO WIZARD",
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun exitGameNavigationMode() {
        val wm = windowManager ?: return
        navigationButton?.let { runCatching { wm.removeView(it) } }
        navigationButton = null
        val rootView = root ?: return
        val params = rootParams ?: return
        if (!wizardAttached) {
            wm.addView(rootView, params)
            wizardAttached = true
        }
        refreshHud()
    }

    private fun smallBtn(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF334155.toInt())
            setOnClickListener { onClick() }
        }

    private fun wizardSteps(): List<CalibrationStep> = steps

    private fun currentStep() = steps.getOrNull(stepIndex)

    private fun refreshHud() {
        val step = steps.getOrNull(stepIndex) ?: return
        val total = steps.size
        val kind = if (step.isRegion) "DRAG A BOX" else "TAP"
        val opt = if (step.optional) " · optional" else ""
        titleView?.text = "Step ${stepIndex + 1}/$total — ${step.title} ($kind$opt)"
        hintView?.text = step.hint +
            "\nUse NAVIGATE GAME to open Fight/Items, then return and mark the button."
        canvasView?.setMode(step.isRegion)
    }

    private fun onGesture(x1: Int, y1: Int, x2: Int, y2: Int) {
        val step = currentStep() ?: return
        scope.launch {
            val cur = repo.current()
            val updated = if (step.isRegion) {
                val w = kotlin.math.abs(x2 - x1)
                val h = kotlin.math.abs(y2 - y1)
                if (w < 12 || h < 12) {
                    Toast.makeText(
                        this@CalibrationWizardService,
                        "Box too small — drag a larger area",
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@launch
                }
                val rect = ScreenRect(
                    left = min(x1, x2),
                    top = min(y1, y2),
                    right = max(x1, x2),
                    bottom = max(y1, y2),
                )
                CalibrationSteps.applyRegion(cur, step.key, rect)
            } else {
                CalibrationSteps.applyPoint(cur, step.key, ScreenPoint(x2, y2))
            }
            repo.save(updated)
            vibrateClick()
            advance()
        }
    }

    private fun advance() {
        stepIndex++
        if (stepIndex >= steps.size) {
            finishWizard(cancelled = false)
        } else {
            refreshHud()
        }
    }

    private fun finishWizard(cancelled: Boolean) {
        Toast.makeText(
            this,
            if (cancelled) "Calibration cancelled" else "Calibration complete",
            Toast.LENGTH_SHORT,
        ).show()
        stopSelf()
    }

    private fun vibrateClick() {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
                val vm = getSystemService(VibratorManager::class.java)
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "wm_poketrap_calibrate"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "WM PokeTrap Calibrate", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("WM PokeTrap")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        navigationButton?.let { runCatching { windowManager?.removeView(it) } }
        navigationButton = null
        if (wizardAttached) {
            root?.let { runCatching { windowManager?.removeView(it) } }
        }
        wizardAttached = false
        root = null
        hudPanel = null
        canvasView = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_WN_ONLY = "wn_only"
        @Volatile private var pendingWnOnly: Boolean = false

        fun start(context: Context, wnOnly: Boolean = false) {
            pendingWnOnly = wnOnly
            val intent = Intent(context, CalibrationWizardService::class.java).apply {
                putExtra(EXTRA_WN_ONLY, wnOnly)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CalibrationWizardService::class.java))
        }
    }
}

private class CalibrateCanvas(
    context: Context,
    private val onDone: (x1: Int, y1: Int, x2: Int, y2: Int) -> Unit,
) : View(context) {
    private var regionMode = true
    private var startX = 0f
    private var startY = 0f
    private var curX = 0f
    private var curY = 0f
    private var startRawX = 0
    private var startRawY = 0
    private var dragging = false

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#A78BFA")
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#33A78BFA")
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#22C55E")
    }

    fun setMode(isRegion: Boolean) {
        regionMode = isRegion
        dragging = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!dragging) return
        if (regionMode) {
            val rect = RectF(
                min(startX, curX),
                min(startY, curY),
                max(startX, curX),
                max(startY, curY),
            )
            canvas.drawRect(rect, fillPaint)
            canvas.drawRect(rect, boxPaint)
        } else {
            canvas.drawLine(curX - 28, curY, curX + 28, curY, crossPaint)
            canvas.drawLine(curX, curY - 28, curX, curY + 28, crossPaint)
            canvas.drawCircle(curX, curY, 18f, crossPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Draw with view-local coords; save with raw screen coords for the bot.
                startX = event.x
                startY = event.y
                curX = startX
                curY = startY
                startRawX = event.rawX.toInt()
                startRawY = event.rawY.toInt()
                dragging = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                curX = event.x
                curY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) return true
                dragging = false
                curX = event.x
                curY = event.y
                invalidate()
                onDone(startRawX, startRawY, event.rawX.toInt(), event.rawY.toInt())
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}