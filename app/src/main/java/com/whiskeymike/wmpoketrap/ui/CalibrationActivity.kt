package com.whiskeymike.wmpoketrap.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.whiskeymike.wmpoketrap.bot.CalibrationSteps
import com.whiskeymike.wmpoketrap.bot.ScreenPoint
import com.whiskeymike.wmpoketrap.bot.ScreenRect
import com.whiskeymike.wmpoketrap.bot.SettingsRepository
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Single-item calibrator (from Calibration tab SELECT buttons).
 * Prefer CalibrationWizardService for full setup over the game.
 */
class CalibrationActivity : ComponentActivity() {
    private var startX = 0f
    private var startY = 0f
    private var dragging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra("key") ?: run {
            finish(); return
        }
        val step = CalibrationSteps.all.find { it.key == key }
            ?: CalibrationSteps.wn.find { it.key == key }
        val isRegion = step?.isRegion
            ?: (key.endsWith("_region") || key.endsWith("Region"))

        val hint = TextView(this).apply {
            text = if (isRegion) {
                "Drag a box around: ${step?.title ?: key}\n(Back to cancel)"
            } else {
                "Tap the center of: ${step?.title ?: key}\n(Back to cancel)"
            }
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setPadding(40, 80, 40, 40)
            setBackgroundColor(0x88000000.toInt())
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(0x66000000.toInt())
            addView(hint)
            setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = ev.rawX
                        startY = ev.rawY
                        dragging = true
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragging) return@setOnTouchListener true
                        dragging = false
                        val x1 = startX.toInt()
                        val y1 = startY.toInt()
                        val x2 = ev.rawX.toInt()
                        val y2 = ev.rawY.toInt()
                        lifecycleScope.launch {
                            val repo = SettingsRepository(applicationContext)
                            val cur = repo.current()
                            val updated = if (isRegion) {
                                val rect = ScreenRect(
                                    left = min(x1, x2),
                                    top = min(y1, y2),
                                    right = max(x1, x2),
                                    bottom = max(y1, y2),
                                )
                                CalibrationSteps.applyRegion(cur, key, rect)
                            } else {
                                CalibrationSteps.applyPoint(cur, key, ScreenPoint(x2, y2))
                            }
                            repo.save(updated)
                            setResult(Activity.RESULT_OK, Intent().putExtra("key", key))
                            finish()
                        }
                        true
                    }
                    else -> true
                }
            }
        }
        setContentView(root)
    }
}
