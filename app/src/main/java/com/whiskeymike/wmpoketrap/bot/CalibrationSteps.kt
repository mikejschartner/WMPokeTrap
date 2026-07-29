package com.whiskeymike.wmpoketrap.bot

data class CalibrationStep(
    val key: String,
    val title: String,
    val hint: String,
    val isRegion: Boolean,
    val optional: Boolean = false,
)

object CalibrationSteps {
    val all: List<CalibrationStep> = listOf(
        CalibrationStep(
            "battle_region",
            "Battle menu",
            "Drag a box around the Fight / Bag / Run menu text",
            isRegion = true,
        ),
        CalibrationStep(
            "name_region",
            "Opponent name",
            "Drag a tight box around the wild Pokémon name",
            isRegion = true,
        ),
        CalibrationStep(
            "shiny_region",
            "Shiny marker",
            "Drag a box where SHINY / shiny label appears",
            isRegion = true,
        ),
        CalibrationStep(
            "hp_region",
            "Opponent HP bar",
            "Drag a TIGHT box around only the colored HP fill (only if False Swipe is on)",
            isRegion = true,
        ),
        CalibrationStep(
            "message_region",
            "Battle message",
            "Drag a box around caught / got away message text",
            isRegion = true,
        ),
        CalibrationStep(
            "ball_name_region",
            "Bag ball name(s)",
            "Open Items, then drag around ball name text used to verify ball type",
            isRegion = true,
        ),
        CalibrationStep(
            "fight_point",
            "Fight button",
            "Tap the center of Fight",
            isRegion = false,
        ),
        CalibrationStep(
            "run_point",
            "Run button",
            "Tap the center of Run",
            isRegion = false,
        ),
        CalibrationStep(
            "false_swipe_point",
            "False Swipe",
            "Open Fight, then tap False Swipe",
            isRegion = false,
        ),
        CalibrationStep(
            "items_point",
            "Items button",
            "Tap the center of Items / Bag",
            isRegion = false,
        ),
        CalibrationStep(
            "pokeball_point",
            "Poké Ball",
            "Open Items, then tap Poké Ball",
            isRegion = false,
        ),
        CalibrationStep(
            "greatball_point",
            "Great Ball",
            "Open Items, then tap Great Ball (if allowed)",
            isRegion = false,
            optional = true,
        ),
        CalibrationStep(
            "ultraball_point",
            "Ultra Ball",
            "Open Items, then tap Ultra Ball (if allowed)",
            isRegion = false,
            optional = true,
        ),
        CalibrationStep(
            "left_point",
            "D-pad Left",
            "Tap Left on the virtual d-pad (optional)",
            isRegion = false,
            optional = true,
        ),
        CalibrationStep(
            "right_point",
            "D-pad Right",
            "Tap Right on the virtual d-pad (optional)",
            isRegion = false,
            optional = true,
        ),
        CalibrationStep(
            "swipe_center",
            "Walk stick center",
            "Tap where you usually put your thumb for Hold mode (optional)",
            isRegion = false,
            optional = true,
        ),
    )

    /** Whatnot WN calibrations only. */
    val wn: List<CalibrationStep> = listOf(
        CalibrationStep(
            "wn_giveaway_region",
            "WN Giveaway / Enter box",
            "Drag a box around Enter Giveaway (or the compact Giveaway chip)",
            isRegion = true,
        ),
        CalibrationStep(
            "wn_follow_region",
            "WN Follow popup (optional)",
            "If Follow-for-giveaway appears, drag a box around that dialog",
            isRegion = true,
            optional = true,
        ),
        CalibrationStep(
            "wn_follow_point",
            "WN Follow button",
            "Tap the Follow confirm button (used when popup appears)",
            isRegion = false,
            optional = true,
        ),
        CalibrationStep(
            "wn_swipe_point",
            "WN winner-watch anchor (optional)",
            "Tap near chat / center — used to watch for “won” (swipe itself is disabled)",
            isRegion = false,
            optional = true,
        ),
    )

    /** Steps needed for the current settings (wizard can skip unused FS/ball steps). */
    fun required(s: TrapSettings): List<CalibrationStep> {
        val skip = mutableSetOf<String>()
        if (!s.useFalseSwipe) {
            skip += setOf("hp_region", "fight_point", "false_swipe_point")
        }
        if (!s.allowGreatBall) skip += "greatball_point"
        if (!s.allowUltraBall) skip += "ultraball_point"
        if (!s.allowPokeBall && (s.allowGreatBall || s.allowUltraBall)) {
            // Still keep pokeball_point optional if not allowed.
            skip += "pokeball_point"
        }
        return all.filter { it.key !in skip }
    }

    fun applyPoint(s: TrapSettings, key: String, p: ScreenPoint): TrapSettings =
        when (key) {
            "fight_point" -> s.copy(fightPoint = p)
            "run_point" -> s.copy(runPoint = p)
            "false_swipe_point" -> s.copy(falseSwipePoint = p)
            "items_point" -> s.copy(itemsPoint = p)
            "pokeball_point" -> s.copy(pokeballPoint = p)
            "greatball_point" -> s.copy(greatballPoint = p)
            "ultraball_point" -> s.copy(ultraballPoint = p)
            "left_point" -> s.copy(leftPoint = p)
            "right_point" -> s.copy(rightPoint = p)
            "up_point" -> s.copy(upPoint = p)
            "down_point" -> s.copy(downPoint = p)
            "swipe_center" -> s.copy(swipeCenter = p)
            "wn_follow_point" -> s.copy(wnFollowPoint = p)
            "wn_swipe_point" -> s.copy(wnSwipePoint = p)
            else -> s
        }

    fun applyRegion(s: TrapSettings, key: String, r: ScreenRect): TrapSettings =
        when (key) {
            "battle_region" -> s.copy(battleRegion = r)
            "name_region" -> s.copy(nameRegion = r)
            "shiny_region" -> s.copy(shinyRegion = r)
            "hp_region" -> s.copy(hpRegion = r)
            "message_region" -> s.copy(messageRegion = r)
            "ball_name_region" -> s.copy(ballNameRegion = r)
            "wn_giveaway_region" -> s.copy(wnGiveawayRegion = r)
            "wn_follow_region" -> s.copy(wnFollowRegion = r)
            else -> s
        }
}
