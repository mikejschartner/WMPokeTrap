package com.whiskeymike.wmpoketrap.bot

data class ScreenRect(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    fun valid(): Boolean = width > 8 && height > 8
    fun centerX(): Float = (left + right) / 2f
    fun centerY(): Float = (top + bottom) / 2f
}

data class ScreenPoint(
    val x: Int = 0,
    val y: Int = 0,
) {
    fun valid(): Boolean = x > 0 && y > 0
}

data class TrapSettings(
    val targetPokemon: String = "Magikarp",
    /** Optional second catch target. Blank = only use targetPokemon. */
    val targetPokemon2: String = "",
    val catchGoal: Int = 10,
    val catchAnyShiny: Boolean = true,
    val shiniesCountTowardGoal: Boolean = true,
    val useFalseSwipe: Boolean = true,
    /** Soften robotic taps / delays with small random jitter. */
    val humanizeInputs: Boolean = false,
    val allowPokeBall: Boolean = true,
    val allowGreatBall: Boolean = false,
    val allowUltraBall: Boolean = false,
    /** Comma-separated priority, highest first. */
    val ballPriority: String = "Poké Ball,Great Ball,Ultra Ball",
    val stopWhenBallUnavailable: Boolean = true,
    val movementMode: String = "Horizontal", // Horizontal | Vertical | Custom | Hold (legacy: Swipe)
    val leftSteps: Int = 4,
    val rightSteps: Int = 4,
    val upSteps: Int = 0,
    val downSteps: Int = 0,
    val stepDelayMs: Long = 180,
    val actionDelayMs: Long = 700,
    val ocrMatchThreshold: Int = 72,
    val hpLowThreshold: Float = 0.25f,
    val captureRetryLimit: Int = 25,
    val battleTextHint: String = "fight",
    val caughtTextHint: String = "caught",
    val shinyTextHint: String = "shiny",
    val battleRegion: ScreenRect = ScreenRect(),
    val nameRegion: ScreenRect = ScreenRect(),
    val shinyRegion: ScreenRect = ScreenRect(),
    val hpRegion: ScreenRect = ScreenRect(),
    val messageRegion: ScreenRect = ScreenRect(),
    val ballNameRegion: ScreenRect = ScreenRect(),
    val fightPoint: ScreenPoint = ScreenPoint(),
    val runPoint: ScreenPoint = ScreenPoint(),
    val falseSwipePoint: ScreenPoint = ScreenPoint(),
    val itemsPoint: ScreenPoint = ScreenPoint(),
    val pokeballPoint: ScreenPoint = ScreenPoint(),
    val greatballPoint: ScreenPoint = ScreenPoint(),
    val ultraballPoint: ScreenPoint = ScreenPoint(),
    val leftPoint: ScreenPoint = ScreenPoint(),
    val rightPoint: ScreenPoint = ScreenPoint(),
    val upPoint: ScreenPoint = ScreenPoint(),
    val downPoint: ScreenPoint = ScreenPoint(),
    // Walk-stick anchors for Hold mode (press left/right of center — no drag).
    val swipeCenter: ScreenPoint = ScreenPoint(),
    val swipeDistance: Int = 220,
    /** Control panel overlay top-left. -1 = use default. */
    val overlayPanelX: Int = -1,
    val overlayPanelY: Int = -1,

    // ── WN (Whatnot giveaway hopper) — separate from Farm ──
    /** OCR box around Giveaway / Enter Giveaway (top area). */
    val wnGiveawayRegion: ScreenRect = ScreenRect(),
    /** Optional OCR box for the Follow-for-giveaway popup. */
    val wnFollowRegion: ScreenRect = ScreenRect(),
    /** Tap target for Follow confirm (used when Follow popup appears). */
    val wnFollowPoint: ScreenPoint = ScreenPoint(),
    /** Where the finger starts the swipe-up to next live. */
    val wnSwipePoint: ScreenPoint = ScreenPoint(),
    /** Swipe length upward in px. */
    val wnSwipeDistance: Int = 900,
) {
    fun catchTargets(): List<String> =
        listOf(targetPokemon, targetPokemon2)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    fun wnReady(): Boolean = wnGiveawayRegion.valid()

    fun enabledBallsInPriority(): List<String> {
        val flags = mapOf(
            "Poké Ball" to allowPokeBall,
            "Great Ball" to allowGreatBall,
            "Ultra Ball" to allowUltraBall,
        )
        val ordered = mutableListOf<String>()
        for (raw in ballPriority.split(",")) {
            val key = raw.trim().lowercase().replace('é', 'e')
            if (key.isEmpty()) continue
            val canon = when {
                "ultra" in key -> "Ultra Ball"
                "great" in key -> "Great Ball"
                else -> "Poké Ball"
            }
            if (flags[canon] == true && canon !in ordered) ordered += canon
        }
        for ((canon, enabled) in flags) {
            if (enabled && canon !in ordered) ordered += canon
        }
        return ordered
    }

    fun pointForBall(ballName: String): ScreenPoint {
        val key = ballName.lowercase().replace('é', 'e')
        return when {
            "ultra" in key -> ultraballPoint
            "great" in key -> greatballPoint
            else -> pokeballPoint
        }
    }
}

data class BotStats(
    val running: Boolean = false,
    val status: String = "Stopped",
    val detected: String = "No encounter yet",
    val caught: Int = 0,
    val shinies: Int = 0,
    val encounters: Int = 0,
    val hp: String = "—",
    /** Active mode label for overlay/UI: "", "farm", or "wn". */
    val mode: String = "",
    /** WN: giveaways successfully entered this run. */
    val wnEntered: Int = 0,
)
