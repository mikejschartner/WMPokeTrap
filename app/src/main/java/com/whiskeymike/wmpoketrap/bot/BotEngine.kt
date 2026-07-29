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
import kotlin.math.roundToLong
import kotlin.random.Random

class BotEngine(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settingsRepo = SettingsRepository(appContext)
    private val ocr = OcrEngine()
    private val matcher = PokemonMatcher(appContext)

    private val _stats = MutableStateFlow(BotStats())
    val stats: StateFlow<BotStats> = _stats.asStateFlow()

    private var job: Job? = null
    @Volatile private var stopRequested = false
    @Volatile private var pauseRequested = false
    /** Once Fight is seen, never swipe again until battle is confirmed over. */
    @Volatile private var battleLocked = false
    /** Last overworld battle-region fingerprint — big jump = encounter splash. */
    private var lastWalkFingerprint: IntArray? = null
    /** Stable overworld fingerprint to decide when a false-alarm splash clears. */
    private var stableWalkFingerprint: IntArray? = null

    fun start() {
        if (job?.isActive == true) return
        val svc = TrapAccessibilityService.instance
        if (svc == null) {
            publish { it.copy(status = "Enable Accessibility for WM PokeTrap first") }
            return
        }
        // Farm and WN never run together.
        WnEngine.get(appContext).stop()
        stopRequested = false
        pauseRequested = false
        battleLocked = false
        lastWalkFingerprint = null
        stableWalkFingerprint = null
        OverlayService.prepareForGestures()
        job = scope.launch { runLoop(svc) }
    }

    fun stop() {
        stopRequested = true
        pauseRequested = false
        battleLocked = false
        lastWalkFingerprint = null
        stableWalkFingerprint = null
        OverlayService.restoreStickForEditing()
        job?.cancel()
        job = null
        publish { it.copy(running = false, status = "Stopped") }
    }

    fun togglePause() {
        pauseRequested = !pauseRequested
        publish {
            it.copy(status = if (pauseRequested) "Paused" else "Resumed")
        }
    }

    private suspend fun runLoop(svc: TrapAccessibilityService) {
        var settings = settingsRepo.current()
        if (!calibrationReady(settings)) {
            publish { it.copy(running = false, status = "Finish Calibration before starting") }
            return
        }

        publish {
            it.copy(
                running = true,
                caught = 0,
                shinies = 0,
                encounters = 0,
                status = "Starting — open the game",
                mode = "farm",
            )
        }
        delay(1500)

        var caught = 0
        var shinies = 0
        var encounters = 0

        while (!stopRequested) {
            waitIfPaused()
            settings = settingsRepo.current()
            if (caught >= settings.catchGoal) {
                publish { it.copy(status = "Catch goal reached", running = false) }
                break
            }

            // Sticky lock: once battle is seen, stay in battle mode (taps only).
            // Do NOT re-open walk just because one OCR frame missed "Fight".
            if (!battleLocked) {
                gateWalking(svc, settings)
            }

            if (battleLocked) {
                val result = handleBattle(svc, settings)
                encounters += 1
                if (result.caught) {
                    if (!result.shiny || settings.shiniesCountTowardGoal) caught += 1
                    if (result.shiny) shinies += 1
                }
                publish {
                    it.copy(
                        encounters = encounters,
                        caught = caught,
                        shinies = shinies,
                    )
                }
                // Only unlock walk after several clear "not in battle" reads.
                awaitBattleClear(svc, settings)
                battleLocked = false
            } else {
                searchCycle(svc, settings) {
                    if (battleLocked) return@searchCycle true
                    // Instant freeze on encounter splash, then OCR confirm — no mid-splash flicks.
                    gateWalking(svc, settings)
                }
                // If walk aborted into battle, loop back into handleBattle immediately.
                if (battleLocked) continue
            }
        }
        publish { it.copy(running = false, status = "Stopped") }
    }

    /** Wait until Fight/Bag/Run is gone for a few polls before allowing swipes again. */
    private suspend fun awaitBattleClear(svc: TrapAccessibilityService, s: TrapSettings) {
        var clearStreak = 0
        var guard = 0
        while (!stopRequested && clearStreak < 3 && guard < 40) {
            waitIfPaused()
            // Use OCR confirm here so a green flash doesn't unlock early.
            if (isBattleQuick(svc, s)) {
                clearStreak = 0
                publish { it.copy(status = "Still in battle — waiting (no swipes)") }
                if (guard > 8 && s.runPoint.valid()) {
                    humanTap(svc, s.runPoint, s)
                }
                delay(280)
            } else {
                clearStreak += 1
                delay(180)
            }
            guard += 1
        }
        publish { it.copy(status = "Battle clear — walking unlocked") }
        lastWalkFingerprint = null
        stableWalkFingerprint = null
    }

    private suspend fun waitIfPaused() {
        while (pauseRequested && !stopRequested) {
            delay(120)
        }
    }

    private suspend fun humanDelay(baseMs: Long, s: TrapSettings, spread: Float = 0.28f) {
        var ms = baseMs.coerceAtLeast(20L)
        if (s.humanizeInputs && ms > 40L) {
            val factor = 1f + Random.nextFloat() * (spread * 2f) - spread
            ms = (ms * factor).roundToLong().coerceAtLeast(30L)
            if (Random.nextFloat() < 0.12f) {
                ms += Random.nextLong(40L, 180L)
            }
        }
        delay(ms)
    }

    /** Sleep in short chunks and abort as soon as battle OCR lights up. */
    private suspend fun walkDelay(
        baseMs: Long,
        s: TrapSettings,
        battleCheck: suspend () -> Boolean,
    ): Boolean {
        var ms = baseMs.coerceAtLeast(20L)
        if (s.humanizeInputs && ms > 40L) {
            val factor = 1f + Random.nextFloat() * 0.56f - 0.28f
            ms = (ms * factor).roundToLong().coerceAtLeast(30L)
        }
        var left = ms
        while (left > 0 && !stopRequested) {
            if (battleLocked) return true
            // Check every slice — splash freeze must beat the next flick.
            if (battleCheck()) return true
            val slice = left.coerceAtMost(28L)
            delay(slice)
            left -= slice
        }
        return battleLocked || battleCheck()
    }

    private suspend fun humanTap(
        svc: TrapAccessibilityService,
        point: ScreenPoint,
        s: TrapSettings,
        durationMs: Long = 50L,
    ): Boolean {
        if (!point.valid()) return false
        var x = point.x.toFloat()
        var y = point.y.toFloat()
        var dur = durationMs
        if (s.humanizeInputs) {
            val radius = Random.nextInt(2, 8)
            x += Random.nextInt(-radius, radius + 1)
            y += Random.nextInt(-radius, radius + 1)
            dur = (durationMs * Random.nextDouble(0.85, 1.35)).roundToLong().coerceAtLeast(30L)
            humanDelay(Random.nextLong(30L, 110L), s, 0.35f)
        }
        return svc.tap(x, y, dur)
    }

    private suspend fun humanSwipe(
        svc: TrapAccessibilityService,
        s: TrapSettings,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long = 220L,
        humanize: Boolean = true,
    ): Boolean {
        // Hard rule: swipes are overworld-only. Never swipe in battle.
        if (battleLocked || stopRequested) return false
        var a = x1
        var b = y1
        var c = x2
        var d = y2
        var dur = durationMs
        if (humanize && s.humanizeInputs) {
            val j = Random.nextInt(3, 12)
            a += Random.nextInt(-j, j + 1)
            b += Random.nextInt(-j, j + 1)
            c += Random.nextInt(-j, j + 1)
            d += Random.nextInt(-j, j + 1)
            dur = (durationMs * Random.nextDouble(0.8, 1.35)).roundToLong().coerceAtLeast(25L)
        }
        return svc.swipe(a, b, c, d, dur)
    }

    private fun calibrationReady(s: TrapSettings): Boolean {
        val regions = mutableListOf(s.battleRegion, s.nameRegion, s.messageRegion)
        val points = mutableListOf(s.runPoint, s.itemsPoint)
        if (s.useFalseSwipe) {
            regions += s.hpRegion
            points += s.fightPoint
            points += s.falseSwipePoint
        }
        val approved = s.enabledBallsInPriority()
        if (approved.isEmpty()) return false
        if (approved.any { !s.pointForBall(it).valid() }) return false
        // Always require bag OCR so we never blind-click a replaced inventory slot.
        regions += s.ballNameRegion
        return regions.all { it.valid() } && points.all { it.valid() }
    }

    private suspend fun isBattle(svc: TrapAccessibilityService, s: TrapSettings): Boolean {
        return isBattleQuick(svc, s)
    }

    /**
     * Walking gate: OCR confirm locks battle; big scene jumps freeze walking
     * during the encounter splash (before Fight is readable) so no flick pans UI.
     * @return true if walking must stop (battle locked).
     */
    private suspend fun gateWalking(svc: TrapAccessibilityService, s: TrapSettings): Boolean {
        if (battleLocked) return true
        if (isBattleQuick(svc, s)) {
            markBattleLocked("ocr")
            return true
        }
        if (encounterSuspect(svc, s)) {
            return waitOutEncounterSplash(svc, s)
        }
        return false
    }

    /** Instant (no OCR) — battle region changed hard or looks like a dark menu. */
    private suspend fun encounterSuspect(svc: TrapAccessibilityService, s: TrapSettings): Boolean {
        if (battleLocked) return true
        val bmp = svc.screenshot() ?: return false
        return try {
            val fp = ocr.regionFingerprint(bmp, s.battleRegion)
            val baseline = stableWalkFingerprint ?: lastWalkFingerprint
            val delta = ocr.fingerprintDelta(baseline, fp)
            val menu = ocr.battleMenuLikely(bmp, s.battleRegion)
            // High bar so grass scrolling alone doesn't freeze forever.
            (baseline != null && delta >= 0.32f) || menu
        } finally {
            bmp.recycle()
        }
    }

    /**
     * Hold still while splash/transition plays. Confirm Fight via OCR, or resume
     * if the region settles back to overworld without menu text.
     */
    private suspend fun waitOutEncounterSplash(
        svc: TrapAccessibilityService,
        s: TrapSettings,
    ): Boolean {
        svc.interruptGestures()
        publish { it.copy(status = "Encounter splash — holding still (no walk)") }
        var settle = 0
        var guard = 0
        while (!stopRequested && !battleLocked && guard < 35) {
            waitIfPaused()
            if (isBattleQuick(svc, s)) {
                markBattleLocked("splash-ocr")
                return true
            }
            val bmp = svc.screenshot()
            if (bmp != null) {
                try {
                    val fp = ocr.regionFingerprint(bmp, s.battleRegion)
                    val baseline = stableWalkFingerprint ?: lastWalkFingerprint
                    val delta = ocr.fingerprintDelta(baseline, fp)
                    val menu = ocr.battleMenuLikely(bmp, s.battleRegion)
                    if (!menu && delta < 0.16f) {
                        settle += 1
                        if (fp != null) lastWalkFingerprint = fp
                        if (settle >= 2) {
                            publish { it.copy(status = "False splash — walking again") }
                            return false
                        }
                    } else {
                        settle = 0
                    }
                } finally {
                    bmp.recycle()
                }
            }
            delay(70)
            guard += 1
        }
        return battleLocked
    }

    /**
     * Fast battle gate used while walking.
     * OCR on the calibrated battle menu region is the source of truth for locking.
     */
    private suspend fun isBattleQuick(
        svc: TrapAccessibilityService,
        s: TrapSettings,
    ): Boolean {
        if (battleLocked) return true
        val bmp = svc.screenshot() ?: return false
        return try {
            val fp = ocr.regionFingerprint(bmp, s.battleRegion)
            val text = ocr.readTextFast(bmp, s.battleRegion)
            val hit = battleMenuTextHit(text, s.battleTextHint)
            if (hit) {
                lastWalkFingerprint = fp
                return true
            }
            // Refresh walking baseline only when the scene is stable overworld.
            val delta = ocr.fingerprintDelta(lastWalkFingerprint, fp)
            if (fp != null && delta < 0.18f) {
                lastWalkFingerprint = fp
                stableWalkFingerprint = fp
            }
            false
        } finally {
            bmp.recycle()
        }
    }

    /** Battle menu OCR must show Fight/Bag/Run (or the calibrated hint). */
    private fun battleMenuTextHit(text: String, hint: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        val hintLc = hint.trim().lowercase()
        if (hintLc.length >= 3 && hintLc in lower) return true
        return "fight" in lower || "bag" in lower || "run" in lower || "item" in lower
    }

    private fun markBattleLocked(reason: String) {
        if (!battleLocked) {
            battleLocked = true
            TrapAccessibilityService.instance?.interruptGestures()
            publish { it.copy(status = "Battle detected ($reason) — swipes locked") }
        }
    }

    private suspend fun searchCycle(
        svc: TrapAccessibilityService,
        s: TrapSettings,
        battleCheck: suspend () -> Boolean,
    ) {
        publish { it.copy(status = "Searching for ${s.catchTargets().joinToString(" / ")}") }
        if (battleCheck()) return
        when (s.movementMode) {
            "Swipe", "Hold" -> {
                // Joystick-style hold left/right — no drag, so battles can't pan.
                roamHold(svc, s, battleCheck)
            }
            "Vertical" -> {
                if (tapSteps(svc, s.upPoint, s.upSteps, s, "up", battleCheck)) return
                tapSteps(svc, s.downPoint, s.downSteps, s, "down", battleCheck)
            }
            "Custom" -> {
                if (tapSteps(svc, s.leftPoint, s.leftSteps, s, "left", battleCheck)) return
                if (tapSteps(svc, s.rightPoint, s.rightSteps, s, "right", battleCheck)) return
                if (tapSteps(svc, s.upPoint, s.upSteps, s, "up", battleCheck)) return
                tapSteps(svc, s.downPoint, s.downSteps, s, "down", battleCheck)
            }
            else -> { // Horizontal
                if (tapSteps(svc, s.leftPoint, s.leftSteps, s, "left", battleCheck)) return
                tapSteps(svc, s.rightPoint, s.rightSteps, s, "right", battleCheck)
            }
        }
    }

    /**
     * Joystick tilt from the draggable stick: touch center → slide to edge → hold.
     * Stick overlay is removed while running so gestures hit the game.
     */
    private suspend fun roamHold(
        svc: TrapAccessibilityService,
        s: TrapSettings,
        battleCheck: suspend () -> Boolean,
    ): Boolean {
        val c = OverlayService.walkStickCenterOrNull()
            ?: s.swipeCenter.takeIf { it.valid() }
            ?: ScreenPoint(540, 1200)
        OverlayService.prepareForGestures()
        val stickD = OverlayService.walkStickHoldOffsetOrDefault(72f)
        // Prefer stick size; allow settings distance when user raised it for a bigger pad.
        val d = maxOf(stickD, s.swipeDistance.toFloat() * 0.45f).coerceIn(48f, 220f)
        val tiltMs = 220L
        val holdMs = 420L
        suspend fun tiltSide(label: String, x2: Float, y2: Float): Boolean {
            if (stopRequested || battleLocked) return true
            if (battleCheck()) return true
            publish { it.copy(status = label) }
            if (stopRequested || battleLocked || battleCheck()) return true
            // Center → edge (activates virtual stick), then hold to walk.
            humanSwipe(
                svc, s,
                c.x.toFloat(), c.y.toFloat(),
                x2, y2,
                tiltMs,
                humanize = false,
            )
            if (battleLocked) {
                svc.interruptGestures()
                return true
            }
            if (battleCheck()) return true
            svc.hold(x2, y2, holdMs)
            if (battleLocked) {
                svc.interruptGestures()
                return true
            }
            return walkDelay(100L, s, battleCheck)
        }
        return tiltSide("Hold left", c.x - d, c.y.toFloat()) ||
            tiltSide("Hold right", c.x + d, c.y.toFloat())
    }

    /** @return true if battle was detected and walking must stop. */
    private suspend fun tapSteps(
        svc: TrapAccessibilityService,
        point: ScreenPoint,
        count: Int,
        s: TrapSettings,
        label: String,
        battleCheck: suspend () -> Boolean,
    ): Boolean {
        if (count <= 0) return false
        // Cap each gesture so battle OCR can interrupt between chunks.
        val totalHold = (count * s.stepDelayMs).coerceAtLeast(120L).coerceAtMost(4000L)
        val chunkMs = 280L
        val chunks = ((totalHold + chunkMs - 1) / chunkMs).toInt().coerceAtLeast(1)
        if (!point.valid()) {
            // Overworld fallback only — never swipe once battle is locked.
            if (battleLocked) return true
            val c = if (s.swipeCenter.valid()) s.swipeCenter else ScreenPoint(540, 1200)
            val d = s.swipeDistance.toFloat().coerceIn(120f, 520f)
            repeat(chunks) {
                if (stopRequested || pauseRequested || battleLocked) return battleLocked
                if (battleCheck()) return true
                publish { it.copy(status = "Walk swipe $label (${count} units)") }
                if (battleLocked) return true
                val dur = 180L
                when (label) {
                    "left" -> humanSwipe(svc, s, c.x.toFloat(), c.y.toFloat(), c.x - d, c.y.toFloat(), dur, humanize = false)
                    "right" -> humanSwipe(svc, s, c.x.toFloat(), c.y.toFloat(), c.x + d, c.y.toFloat(), dur, humanize = false)
                    "up" -> humanSwipe(svc, s, c.x.toFloat(), c.y.toFloat(), c.x.toFloat(), c.y - d, dur, humanize = false)
                    else -> humanSwipe(svc, s, c.x.toFloat(), c.y.toFloat(), c.x.toFloat(), c.y + d, dur, humanize = false)
                }
                if (battleLocked) {
                    svc.interruptGestures()
                    return true
                }
                if (walkDelay(140L, s, battleCheck)) return true
            }
            return false
        }
        repeat(chunks) {
            if (stopRequested || pauseRequested) return false
            if (battleLocked || battleCheck()) return true
            publish { it.copy(status = "Holding $label (${count} units)") }
            humanTap(svc, point, s, durationMs = chunkMs.coerceIn(100L, 320L))
            if (walkDelay(60L, s, battleCheck)) return true
        }
        return false
    }

    private data class BattleResult(val caught: Boolean, val shiny: Boolean)

    private suspend fun handleBattle(
        svc: TrapAccessibilityService,
        s: TrapSettings,
    ): BattleResult {
        publish { it.copy(status = "Battle detected") }
        delay(300)

        var best = PokemonMatcher.DecideResult(false, "", 0, "init")
        var rawBest = ""
        repeat(2) {
            val bmp = svc.screenshot() ?: return@repeat
            try {
                val raw = matcher.cleanName(ocr.readText(bmp, s.nameRegion))
                val decision = matcher.decideCatch(raw, s.catchTargets(), s.ocrMatchThreshold)
                if (decision.catch || decision.score > best.score) {
                    best = decision
                    rawBest = raw
                }
                if (decision.catch && decision.score >= s.ocrMatchThreshold) return@repeat
            } finally {
                bmp.recycle()
            }
            delay(100)
        }

        val shiny = if (s.catchAnyShiny) detectShiny(svc, s) else false
        publish {
            it.copy(
                detected = "OCR '${rawBest.ifBlank { "?" }}' → ${best.name.ifBlank { "unknown" }} " +
                    "(${best.score}% / ${best.reason}) — ${if (best.catch || shiny) "CATCH" else "FLEE"}",
            )
        }

        return when {
            shiny -> {
                publish { it.copy(status = "SHINY found — catching") }
                val ok = capture(svc, s)
                BattleResult(caught = ok, shiny = true)
            }
            best.catch -> {
                publish { it.copy(status = "Matched ${best.name} — catching (${best.score}%)") }
                val ok = capture(svc, s)
                BattleResult(caught = ok, shiny = false)
            }
            else -> {
                publish {
                    it.copy(
                        status = "Not ${s.catchTargets().joinToString(" / ")} (${best.name.ifBlank { rawBest }}, ${best.score}%) — Run",
                    )
                }
                flee(svc, s)
                BattleResult(caught = false, shiny = false)
            }
        }
    }

    private suspend fun detectShiny(svc: TrapAccessibilityService, s: TrapSettings): Boolean {
        val bmp = svc.screenshot() ?: return false
        return try {
            val hint = s.shinyTextHint.lowercase()
            val shinyText = ocr.readText(bmp, s.shinyRegion).lowercase()
            if (hint in shinyText) return true
            val nameText = ocr.readText(bmp, s.nameRegion).lowercase()
            hint in nameText
        } finally {
            bmp.recycle()
        }
    }

    private suspend fun flee(svc: TrapAccessibilityService, s: TrapSettings) {
        repeat(8) { attempt ->
            if (stopRequested) return
            if (!isBattle(svc, s)) {
                publish { it.copy(status = "Got away") }
                return
            }
            publish { it.copy(status = "Tapping Run (${attempt + 1}/8)") }
            humanTap(svc, s.runPoint, s)
            humanDelay(maxOf(300L, s.actionDelayMs / 2), s)
        }
        publish { it.copy(status = "Flee attempts done — continuing") }
    }

    /** @return true if the Pokémon was caught (confirmed or battle ended after a throw). */
    private suspend fun capture(svc: TrapAccessibilityService, s: TrapSettings): Boolean {
        if (s.useFalseSwipe) {
            if (!falseSwipePhase(svc, s)) return false
        } else {
            publish { it.copy(status = "False Swipe off — going straight to balls") }
        }

        val maxAttempts = s.captureRetryLimit.coerceAtLeast(25)
        var attempt = 0
        while (!stopRequested) {
            // Don't open the bag until Fight is back — mid-shake looks like "not in battle"
            // and a blind bag open often triggers a false NO_BALLS stop.
            when (waitReadyToThrow(svc, s)) {
                ThrowGate.CAUGHT -> {
                    publish { it.copy(status = "Pokémon caught") }
                    delay(700)
                    return true
                }
                ThrowGate.LEFT_BATTLE -> {
                    publish { it.copy(status = "Battle ended — counting as catch") }
                    delay(500)
                    return true
                }
                ThrowGate.READY -> Unit
                ThrowGate.STOPPED -> return false
            }

            attempt += 1
            if (attempt > maxAttempts) {
                // Last chance: catch anim may have finished without Fight returning.
                if (readCaughtMessage(svc, s) || !isBattle(svc, s)) {
                    publish { it.copy(status = "Pokémon caught (after retry wait)") }
                    return true
                }
                publish {
                    it.copy(
                        running = false,
                        status = "ERROR: Catch retry limit reached ($maxAttempts) — stopping",
                    )
                }
                stopRequested = true
                return false
            }

            publish { it.copy(status = "Throwing ball — attempt $attempt/$maxAttempts") }
            when (throwApprovedBall(svc, s)) {
                BallThrowResult.THREW -> Unit
                BallThrowResult.FAILED -> {
                    // Tap missed — wait and see if a catch already resolved.
                    if (waitCatchResult(svc, s) == CatchResult.CAUGHT) {
                        publish { it.copy(status = "Pokémon caught") }
                        return true
                    }
                    publish { it.copy(status = "Ball tap failed — retrying") }
                    delay(400)
                    continue
                }
                BallThrowResult.NO_BALLS -> {
                    // Bag OCR often fails while the catch animation is playing.
                    publish { it.copy(status = "Bag unclear — checking if already caught") }
                    when (waitCatchResult(svc, s)) {
                        CatchResult.CAUGHT -> {
                            publish { it.copy(status = "Pokémon caught") }
                            return true
                        }
                        CatchResult.BROKE_FREE -> {
                            publish { it.copy(status = "Still in battle — bag OCR missed ball, retrying") }
                            delay(400)
                            continue
                        }
                        CatchResult.TIMEOUT -> {
                            if (!isBattle(svc, s)) {
                                publish { it.copy(status = "Battle ended — counting as catch") }
                                return true
                            }
                        }
                    }
                    publish {
                        it.copy(
                            running = false,
                            status = "ERROR: No approved Poké Balls available.",
                        )
                    }
                    stopRequested = true
                    return false
                }
            }

            publish { it.copy(status = "Ball thrown — waiting for catch…") }
            when (waitCatchResult(svc, s)) {
                CatchResult.CAUGHT -> {
                    publish { it.copy(status = "Pokémon caught after $attempt ball(s)") }
                    delay(800)
                    return true
                }
                CatchResult.BROKE_FREE -> {
                    publish { it.copy(status = "Broke free — throwing another") }
                    delay(350)
                }
                CatchResult.TIMEOUT -> {
                    if (readCaughtMessage(svc, s) || !isBattle(svc, s)) {
                        publish { it.copy(status = "Battle ended — counting as catch") }
                        return true
                    }
                    publish { it.copy(status = "Catch result unclear — checking Fight menu") }
                }
            }
        }
        return false
    }

    private enum class ThrowGate { READY, CAUGHT, LEFT_BATTLE, STOPPED }
    private enum class CatchResult { CAUGHT, BROKE_FREE, TIMEOUT }

    /** Wait until Fight/Bag/Run is stable, or catch/overworld is already done. */
    private suspend fun waitReadyToThrow(
        svc: TrapAccessibilityService,
        s: TrapSettings,
    ): ThrowGate {
        var readyStreak = 0
        var guard = 0
        while (!stopRequested && guard < 50) {
            if (readCaughtMessage(svc, s)) return ThrowGate.CAUGHT
            val inBattle = isBattle(svc, s)
            if (!inBattle) {
                // No Fight menu — either catch celebration or overworld.
                delay(450)
                if (readCaughtMessage(svc, s)) return ThrowGate.CAUGHT
                if (!isBattle(svc, s)) return ThrowGate.LEFT_BATTLE
            }
            val menu = fightMenuReady(svc, s)
            if (menu) {
                readyStreak += 1
                if (readyStreak >= 2) return ThrowGate.READY
            } else {
                readyStreak = 0
            }
            delay(220)
            guard += 1
        }
        return if (stopRequested) ThrowGate.STOPPED else ThrowGate.READY
    }

    private suspend fun fightMenuReady(svc: TrapAccessibilityService, s: TrapSettings): Boolean {
        val bmp = svc.screenshot() ?: return false
        return try {
            battleMenuTextHit(ocr.readTextFast(bmp, s.battleRegion), s.battleTextHint)
        } finally {
            bmp.recycle()
        }
    }

    private suspend fun readCaughtMessage(svc: TrapAccessibilityService, s: TrapSettings): Boolean {
        val bmp = svc.screenshot() ?: return false
        return try {
            val msg = ocr.readText(bmp, s.messageRegion).lowercase()
            if (msg.isBlank()) return false
            val hint = s.caughtTextHint.trim().lowercase()
            if (hint.length >= 3 && hint in msg) return true
            val markers = listOf(
                "gotcha",
                "was caught",
                "successfully caught",
                "caught the",
                "captured",
                "caught",
            )
            markers.any { it in msg }
        } finally {
            bmp.recycle()
        }
    }

    /**
     * Poll after a throw. Do not call broke-free until the shake window has passed
     * and Fight is clearly back.
     */
    private suspend fun waitCatchResult(
        svc: TrapAccessibilityService,
        s: TrapSettings,
        timeoutMs: Long = 20_000L,
    ): CatchResult {
        val started = System.currentTimeMillis()
        val minBreakWaitMs = 6_500L
        var readyStreak = 0
        while (!stopRequested && System.currentTimeMillis() - started < timeoutMs) {
            if (readCaughtMessage(svc, s)) return CatchResult.CAUGHT
            val elapsed = System.currentTimeMillis() - started
            val menu = fightMenuReady(svc, s)
            if (elapsed >= minBreakWaitMs && menu) {
                readyStreak += 1
                if (readyStreak >= 2) {
                    // Double-check catch text one more time before re-throwing.
                    if (readCaughtMessage(svc, s)) return CatchResult.CAUGHT
                    return CatchResult.BROKE_FREE
                }
            } else {
                readyStreak = 0
            }
            // Battle fully gone after the shake window → treat as caught.
            if (elapsed >= 3_500L && !isBattle(svc, s) && !menu) {
                delay(400)
                if (readCaughtMessage(svc, s)) return CatchResult.CAUGHT
                if (!isBattle(svc, s)) return CatchResult.CAUGHT
            }
            delay(320)
        }
        if (readCaughtMessage(svc, s)) return CatchResult.CAUGHT
        if (!isBattle(svc, s)) return CatchResult.CAUGHT
        return CatchResult.TIMEOUT
    }

    private suspend fun falseSwipePhase(svc: TrapAccessibilityService, s: TrapSettings): Boolean {
        if (!humanTap(svc, s.fightPoint, s)) {
            publish { it.copy(running = false, status = "ERROR: Fight tap failed") }
            stopRequested = true
            return false
        }
        humanDelay(s.actionDelayMs, s)
        if (!humanTap(svc, s.falseSwipePoint, s)) {
            publish { it.copy(running = false, status = "ERROR: False Swipe tap failed") }
            stopRequested = true
            return false
        }
        humanDelay(s.actionDelayMs + 400, s)

        repeat(8) { swipeI ->
            if (stopRequested || !isBattle(svc, s)) return false
            val bmp = svc.screenshot() ?: return@repeat
            val ratio = try {
                ocr.hpRatio(bmp, s.hpRegion)
            } finally {
                bmp.recycle()
            }
            publish { it.copy(hp = "${"%.0f".format(ratio * 100)}%") }
            if (ratio < s.hpLowThreshold) {
                publish { it.copy(status = "HP low — switching to balls") }
                return true
            }
            if (swipeI >= 3 && ratio > 0.9f) {
                publish { it.copy(status = "HP stuck high — switching to balls") }
                return true
            }
            publish { it.copy(status = "HP ${"%.0f".format(ratio * 100)}% — False Swipe again") }
            humanTap(svc, s.fightPoint, s)
            humanDelay(s.actionDelayMs, s)
            humanTap(svc, s.falseSwipePoint, s)
            humanDelay(s.actionDelayMs + 400, s)
        }
        return true
    }

    private enum class BallThrowResult { THREW, FAILED, NO_BALLS }

    private suspend fun throwApprovedBall(
        svc: TrapAccessibilityService,
        s: TrapSettings,
    ): BallThrowResult {
        val approved = s.enabledBallsInPriority()
        if (approved.isEmpty()) return BallThrowResult.NO_BALLS

        if (!humanTap(svc, s.itemsPoint, s)) return BallThrowResult.FAILED
        humanDelay(s.actionDelayMs, s)

        val requireOcr = true
        var bagText = ""
        if (requireOcr) {
            if (!s.ballNameRegion.valid()) return BallThrowResult.NO_BALLS
            val bmp = svc.screenshot()
            bagText = try {
                bmp?.let { ocr.readText(it, s.ballNameRegion).lowercase() }.orEmpty()
            } finally {
                bmp?.recycle()
            }
        }

        for (ballName in approved) {
            val point = s.pointForBall(ballName)
            if (!point.valid()) continue
            if (requireOcr && !ballNameInText(ballName, bagText, s.ocrMatchThreshold)) {
                publish { it.copy(status = "Bag OCR missing '$ballName' — next approved ball") }
                continue
            }
            publish { it.copy(status = "Throwing $ballName") }
            if (!humanTap(svc, point, s)) return BallThrowResult.FAILED
            return BallThrowResult.THREW
        }
        return BallThrowResult.NO_BALLS
    }

    private fun ballNameInText(ballName: String, bagText: String, threshold: Int): Boolean {
        if (bagText.isBlank()) return false
        val aliases = when {
            "ultra" in ballName.lowercase() -> listOf("ultra ball", "ultraball")
            "great" in ballName.lowercase() -> listOf("great ball", "greatball")
            else -> listOf("poké ball", "poke ball", "pokeball")
        }
        val normalized = bagText.replace('é', 'e')
        for (alias in aliases) {
            val a = alias.replace('é', 'e')
            if (a in normalized || a.replace(" ", "") in normalized.replace(" ", "")) return true
            if (Fuzzy.partialRatio(a, normalized) >= maxOf(78, threshold)) return true
        }
        return false
    }

    private fun publish(block: (BotStats) -> BotStats) {
        _stats.value = block(_stats.value)
    }

    companion object {
        @Volatile private var singleton: BotEngine? = null
        fun get(context: Context): BotEngine {
            return singleton ?: synchronized(this) {
                singleton ?: BotEngine(context.applicationContext).also { singleton = it }
            }
        }
    }
}
