package com.whiskeymike.wmpoketrap.bot

import android.content.Context
import android.graphics.Bitmap
import com.whiskeymike.wmpoketrap.service.OverlayService
import com.whiskeymike.wmpoketrap.service.TrapAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Whatnot giveaway hopper — separate from Farm.
 *
 * Stay on the live: tap Giveaway + Follow, then watch for a winner ("won"),
 * and re-enter. No swipe (leaving drops the entry).
 */
class WnEngine(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settingsRepo = SettingsRepository(appContext)
    private val ocr = OcrEngine()

    private val _stats = MutableStateFlow(BotStats(mode = "wn"))
    val stats: StateFlow<BotStats> = _stats.asStateFlow()

    private var job: Job? = null
    @Volatile private var stopRequested = false
    @Volatile private var pauseRequested = false

    fun start() {
        if (job?.isActive == true) return
        val svc = TrapAccessibilityService.instance
        if (svc == null) {
            publish { it.copy(status = "Enable Accessibility for WM PokeTrap first", mode = "wn") }
            return
        }
        BotEngine.get(appContext).stop()
        stopRequested = false
        pauseRequested = false
        OverlayService.prepareForGestures()
        job = scope.launch { runLoop(svc) }
    }

    fun stop() {
        stopRequested = true
        pauseRequested = false
        OverlayService.restoreStickForEditing()
        job?.cancel()
        job = null
        publish { it.copy(running = false, status = "WN stopped", mode = "wn") }
    }

    fun togglePause() {
        pauseRequested = !pauseRequested
        publish {
            it.copy(status = if (pauseRequested) "WN paused" else "WN resumed", mode = "wn")
        }
    }

    private suspend fun runLoop(svc: TrapAccessibilityService) {
        var s = settingsRepo.current()
        if (!s.wnGiveawayRegion.valid()) {
            publish {
                it.copy(
                    running = false,
                    status = "Calibrate WN Giveaway box",
                    mode = "wn",
                )
            }
            return
        }

        var entered = 0
        publish {
            it.copy(
                running = true,
                wnEntered = 0,
                status = "WN starting — open Whatnot live",
                mode = "wn",
                detected = "enter → watch for won → re-enter",
            )
        }
        delay(800)

        while (!stopRequested) {
            waitIfPaused()
            s = settingsRepo.current()
            if (!s.wnGiveawayRegion.valid()) {
                publish { it.copy(status = "WN Giveaway box missing", mode = "wn") }
                delay(1000)
                continue
            }

            // Enter (or re-enter) this giveaway.
            entered++
            publish {
                it.copy(
                    status = "WN settling before enter…",
                    wnEntered = entered,
                    mode = "wn",
                )
            }
            delay(SETTLE_MS)
            if (stopRequested) break

            publish { it.copy(status = "WN tap Giveaway (#$entered)", mode = "wn") }
            OverlayService.prepareForGestures()
            svc.tap(s.wnGiveawayRegion.centerX(), s.wnGiveawayRegion.centerY())
            delay(SETTLE_MS)
            if (stopRequested) break

            tapFollowAlways(svc, s)
            if (stopRequested) break

            // Stay on this live — wait for a winner, then loop to re-enter.
            publish {
                it.copy(
                    status = "WN watching for winner… (#$entered)",
                    mode = "wn",
                )
            }
            waitForWinner(svc, s)
            if (stopRequested) break

            publish { it.copy(status = "WN winner seen — re-enter shortly", mode = "wn") }
            delay(POST_WIN_MS)
        }

        publish { it.copy(running = false, status = "WN stopped", mode = "wn", wnEntered = entered) }
    }

    private suspend fun waitForWinner(svc: TrapAccessibilityService, s: TrapSettings) {
        // Ignore "won" that might still be on screen from a prior giveaway for a moment.
        delay(1500)
        while (!stopRequested) {
            waitIfPaused()
            if (stopRequested) return
            val bmp = svc.screenshot()
            if (bmp == null) {
                delay(700)
                continue
            }
            val blob = readWinnerWatchText(bmp, s)
            val norm = normalize(blob)
            publish {
                it.copy(
                    detected = blob.take(90).ifBlank { "(watching…)" },
                    status = "WN watching for winner…",
                    mode = "wn",
                )
            }
            if (looksLikeWinner(norm)) {
                publish {
                    it.copy(
                        status = "WN winner detected",
                        detected = blob.take(90),
                        mode = "wn",
                    )
                }
                // Wait until the won line fades a bit so we don't double-fire same event.
                delay(1200)
                return
            }
            delay(POLL_MS)
        }
    }

    private suspend fun readWinnerWatchText(bmp: Bitmap, s: TrapSettings): String {
        val parts = mutableListOf<String>()
        // Chat / winner toasts are usually left-center — prefer swipe-point as an anchor
        // for a watch box if calibrated; otherwise use a left-band on the screenshot.
        if (s.wnSwipePoint.valid()) {
            val cx = s.wnSwipePoint.x
            val cy = s.wnSwipePoint.y
            val watch = ScreenRect(
                left = (cx - 280).coerceAtLeast(0),
                top = (cy - 420).coerceAtLeast(0),
                right = (cx + 280).coerceAtMost(bmp.width),
                bottom = (cy + 80).coerceAtMost(bmp.height),
            )
            if (watch.valid()) parts += ocr.readTextFast(bmp, watch, maxWidth = 320)
        } else {
            val chat = ScreenRect(
                left = (bmp.width * 0.02f).toInt(),
                top = (bmp.height * 0.35f).toInt(),
                right = (bmp.width * 0.62f).toInt(),
                bottom = (bmp.height * 0.78f).toInt(),
            )
            if (chat.valid()) parts += ocr.readTextFast(bmp, chat, maxWidth = 320)
        }
        if (s.wnGiveawayRegion.valid()) {
            parts += ocr.readTextFast(bmp, s.wnGiveawayRegion, maxWidth = 280)
        }
        if (s.wnFollowRegion.valid()) {
            parts += ocr.readTextFast(bmp, s.wnFollowRegion, maxWidth = 280)
        }
        return parts.joinToString(" ")
    }

    private suspend fun tapFollowAlways(svc: TrapAccessibilityService, s: TrapSettings) {
        when {
            s.wnFollowPoint.valid() -> {
                publish { it.copy(status = "WN tap Follow", mode = "wn") }
                OverlayService.prepareForGestures()
                svc.tap(s.wnFollowPoint)
                delay(700)
            }
            s.wnFollowRegion.valid() -> {
                publish { it.copy(status = "WN tap Follow region", mode = "wn") }
                OverlayService.prepareForGestures()
                svc.tap(s.wnFollowRegion.centerX(), s.wnFollowRegion.centerY())
                delay(700)
            }
            else -> delay(200)
        }
    }

    private suspend fun waitIfPaused() {
        while (pauseRequested && !stopRequested) {
            delay(200)
        }
    }

    private fun publish(block: (BotStats) -> BotStats) {
        _stats.value = block(_stats.value.copy(mode = "wn"))
    }

    companion object {
        private const val SETTLE_MS = 1500L
        private const val POST_WIN_MS = 2000L
        private const val POLL_MS = 650L

        @Volatile private var singleton: WnEngine? = null
        fun get(context: Context): WnEngine {
            return singleton ?: synchronized(this) {
                singleton ?: WnEngine(context.applicationContext).also { singleton = it }
            }
        }

        fun normalize(raw: String): String =
            raw.lowercase()
                .replace('é', 'e')
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        fun looksLikeWinner(norm: String): Boolean {
            if (norm.isBlank()) return false
            // Chat line like "javithehutt623 won" / "someone won!"
            if (Regex("\\bwon\\b").containsMatchIn(norm)) return true
            if ("winner" in norm) return true
            return false
        }
    }
}
