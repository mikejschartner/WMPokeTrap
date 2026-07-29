package com.whiskeymike.wmpoketrap.bot

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("wm_poketrap_settings")

class SettingsRepository(private val context: Context) {
    val settingsFlow: Flow<TrapSettings> = context.dataStore.data.map { prefs ->
        TrapSettings(
            targetPokemon = prefs[KEY_TARGET] ?: "Magikarp",
            targetPokemon2 = prefs[KEY_TARGET2] ?: "",
            catchGoal = prefs[KEY_GOAL] ?: 10,
            catchAnyShiny = prefs[KEY_SHINY] ?: true,
            shiniesCountTowardGoal = prefs[KEY_SHINY_GOAL] ?: true,
            useFalseSwipe = prefs[KEY_USE_FS] ?: true,
            humanizeInputs = prefs[KEY_HUMANIZE] ?: false,
            allowPokeBall = prefs[KEY_ALLOW_POKE] ?: true,
            allowGreatBall = prefs[KEY_ALLOW_GREAT] ?: false,
            allowUltraBall = prefs[KEY_ALLOW_ULTRA] ?: false,
            ballPriority = prefs[KEY_BALL_PRIO] ?: "Poké Ball,Great Ball,Ultra Ball",
            stopWhenBallUnavailable = prefs[KEY_STOP_BALL] ?: true,
            movementMode = prefs[KEY_MOVE_MODE] ?: "Horizontal",
            leftSteps = prefs[KEY_LEFT] ?: 4,
            rightSteps = prefs[KEY_RIGHT] ?: 4,
            upSteps = prefs[KEY_UP] ?: 0,
            downSteps = prefs[KEY_DOWN] ?: 0,
            stepDelayMs = prefs[KEY_STEP_DELAY] ?: 180L,
            actionDelayMs = prefs[KEY_ACTION_DELAY] ?: 700L,
            ocrMatchThreshold = prefs[KEY_OCR] ?: 72,
            hpLowThreshold = prefs[KEY_HP] ?: 0.25f,
            captureRetryLimit = (prefs[KEY_CATCH_RETRY] ?: 25).coerceAtLeast(25),
            battleTextHint = prefs[KEY_BATTLE_HINT] ?: "fight",
            caughtTextHint = prefs[KEY_CAUGHT_HINT] ?: "caught",
            shinyTextHint = prefs[KEY_SHINY_HINT] ?: "shiny",
            battleRegion = rect(prefs[KEY_BATTLE_R]),
            nameRegion = rect(prefs[KEY_NAME_R]),
            shinyRegion = rect(prefs[KEY_SHINY_R]),
            hpRegion = rect(prefs[KEY_HP_R]),
            messageRegion = rect(prefs[KEY_MSG_R]),
            ballNameRegion = rect(prefs[KEY_BALL_NAME_R]),
            fightPoint = point(prefs[KEY_FIGHT]),
            runPoint = point(prefs[KEY_RUN]),
            falseSwipePoint = point(prefs[KEY_FS]),
            itemsPoint = point(prefs[KEY_ITEMS]),
            pokeballPoint = point(prefs[KEY_BALL]),
            greatballPoint = point(prefs[KEY_GREAT]),
            ultraballPoint = point(prefs[KEY_ULTRA]),
            leftPoint = point(prefs[KEY_LEFT_P]),
            rightPoint = point(prefs[KEY_RIGHT_P]),
            upPoint = point(prefs[KEY_UP_P]),
            downPoint = point(prefs[KEY_DOWN_P]),
            swipeCenter = point(prefs[KEY_SWIPE_C]),
            swipeDistance = prefs[KEY_SWIPE_D] ?: 220,
            overlayPanelX = prefs[KEY_OVERLAY_PX] ?: -1,
            overlayPanelY = prefs[KEY_OVERLAY_PY] ?: -1,
            wnGiveawayRegion = rect(prefs[KEY_WN_GIVE_R]),
            wnFollowRegion = rect(prefs[KEY_WN_FOLLOW_R]),
            wnFollowPoint = point(prefs[KEY_WN_FOLLOW_P]),
            wnSwipePoint = point(prefs[KEY_WN_SWIPE_P]),
            wnSwipeDistance = prefs[KEY_WN_SWIPE_D] ?: 900,
        )
    }

    suspend fun current(): TrapSettings = settingsFlow.first()

    suspend fun save(s: TrapSettings) {
        context.dataStore.edit { p ->
            p[KEY_TARGET] = s.targetPokemon
            p[KEY_TARGET2] = s.targetPokemon2
            p[KEY_GOAL] = s.catchGoal
            p[KEY_SHINY] = s.catchAnyShiny
            p[KEY_SHINY_GOAL] = s.shiniesCountTowardGoal
            p[KEY_USE_FS] = s.useFalseSwipe
            p[KEY_HUMANIZE] = s.humanizeInputs
            p[KEY_ALLOW_POKE] = s.allowPokeBall
            p[KEY_ALLOW_GREAT] = s.allowGreatBall
            p[KEY_ALLOW_ULTRA] = s.allowUltraBall
            p[KEY_BALL_PRIO] = s.ballPriority
            p[KEY_STOP_BALL] = s.stopWhenBallUnavailable
            p[KEY_MOVE_MODE] = s.movementMode
            p[KEY_LEFT] = s.leftSteps
            p[KEY_RIGHT] = s.rightSteps
            p[KEY_UP] = s.upSteps
            p[KEY_DOWN] = s.downSteps
            p[KEY_STEP_DELAY] = s.stepDelayMs
            p[KEY_ACTION_DELAY] = s.actionDelayMs
            p[KEY_OCR] = s.ocrMatchThreshold
            p[KEY_HP] = s.hpLowThreshold
            p[KEY_CATCH_RETRY] = s.captureRetryLimit
            p[KEY_BATTLE_HINT] = s.battleTextHint
            p[KEY_CAUGHT_HINT] = s.caughtTextHint
            p[KEY_SHINY_HINT] = s.shinyTextHint
            p[KEY_BATTLE_R] = encodeRect(s.battleRegion)
            p[KEY_NAME_R] = encodeRect(s.nameRegion)
            p[KEY_SHINY_R] = encodeRect(s.shinyRegion)
            p[KEY_HP_R] = encodeRect(s.hpRegion)
            p[KEY_MSG_R] = encodeRect(s.messageRegion)
            p[KEY_BALL_NAME_R] = encodeRect(s.ballNameRegion)
            p[KEY_FIGHT] = encodePoint(s.fightPoint)
            p[KEY_RUN] = encodePoint(s.runPoint)
            p[KEY_FS] = encodePoint(s.falseSwipePoint)
            p[KEY_ITEMS] = encodePoint(s.itemsPoint)
            p[KEY_BALL] = encodePoint(s.pokeballPoint)
            p[KEY_GREAT] = encodePoint(s.greatballPoint)
            p[KEY_ULTRA] = encodePoint(s.ultraballPoint)
            p[KEY_LEFT_P] = encodePoint(s.leftPoint)
            p[KEY_RIGHT_P] = encodePoint(s.rightPoint)
            p[KEY_UP_P] = encodePoint(s.upPoint)
            p[KEY_DOWN_P] = encodePoint(s.downPoint)
            p[KEY_SWIPE_C] = encodePoint(s.swipeCenter)
            p[KEY_SWIPE_D] = s.swipeDistance
            p[KEY_OVERLAY_PX] = s.overlayPanelX
            p[KEY_OVERLAY_PY] = s.overlayPanelY
            p[KEY_WN_GIVE_R] = encodeRect(s.wnGiveawayRegion)
            p[KEY_WN_FOLLOW_R] = encodeRect(s.wnFollowRegion)
            p[KEY_WN_FOLLOW_P] = encodePoint(s.wnFollowPoint)
            p[KEY_WN_SWIPE_P] = encodePoint(s.wnSwipePoint)
            p[KEY_WN_SWIPE_D] = s.wnSwipeDistance
        }
    }

    private fun encodeRect(r: ScreenRect) = "${r.left},${r.top},${r.right},${r.bottom}"
    private fun encodePoint(p: ScreenPoint) = "${p.x},${p.y}"

    private fun rect(raw: String?): ScreenRect {
        if (raw.isNullOrBlank()) return ScreenRect()
        val p = raw.split(",")
        if (p.size != 4) return ScreenRect()
        return ScreenRect(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt())
    }

    private fun point(raw: String?): ScreenPoint {
        if (raw.isNullOrBlank()) return ScreenPoint()
        val p = raw.split(",")
        if (p.size != 2) return ScreenPoint()
        return ScreenPoint(p[0].toInt(), p[1].toInt())
    }

    companion object {
        private val KEY_TARGET = stringPreferencesKey("target")
        private val KEY_TARGET2 = stringPreferencesKey("target2")
        private val KEY_GOAL = intPreferencesKey("goal")
        private val KEY_SHINY = booleanPreferencesKey("shiny")
        private val KEY_SHINY_GOAL = booleanPreferencesKey("shiny_goal")
        private val KEY_USE_FS = booleanPreferencesKey("use_false_swipe")
        private val KEY_HUMANIZE = booleanPreferencesKey("humanize_inputs")
        private val KEY_ALLOW_POKE = booleanPreferencesKey("allow_poke")
        private val KEY_ALLOW_GREAT = booleanPreferencesKey("allow_great")
        private val KEY_ALLOW_ULTRA = booleanPreferencesKey("allow_ultra")
        private val KEY_BALL_PRIO = stringPreferencesKey("ball_priority")
        private val KEY_STOP_BALL = booleanPreferencesKey("stop_when_ball_unavailable")
        private val KEY_MOVE_MODE = stringPreferencesKey("move_mode")
        private val KEY_LEFT = intPreferencesKey("left")
        private val KEY_RIGHT = intPreferencesKey("right")
        private val KEY_UP = intPreferencesKey("up")
        private val KEY_DOWN = intPreferencesKey("down")
        private val KEY_STEP_DELAY = longPreferencesKey("step_delay")
        private val KEY_ACTION_DELAY = longPreferencesKey("action_delay")
        private val KEY_OCR = intPreferencesKey("ocr")
        private val KEY_HP = floatPreferencesKey("hp")
        private val KEY_CATCH_RETRY = intPreferencesKey("catch_retry")
        private val KEY_BATTLE_HINT = stringPreferencesKey("battle_hint")
        private val KEY_CAUGHT_HINT = stringPreferencesKey("caught_hint")
        private val KEY_SHINY_HINT = stringPreferencesKey("shiny_hint")
        private val KEY_BATTLE_R = stringPreferencesKey("battle_r")
        private val KEY_NAME_R = stringPreferencesKey("name_r")
        private val KEY_SHINY_R = stringPreferencesKey("shiny_r")
        private val KEY_HP_R = stringPreferencesKey("hp_r")
        private val KEY_MSG_R = stringPreferencesKey("msg_r")
        private val KEY_BALL_NAME_R = stringPreferencesKey("ball_name_r")
        private val KEY_FIGHT = stringPreferencesKey("fight")
        private val KEY_RUN = stringPreferencesKey("run")
        private val KEY_FS = stringPreferencesKey("fs")
        private val KEY_ITEMS = stringPreferencesKey("items")
        private val KEY_BALL = stringPreferencesKey("ball")
        private val KEY_GREAT = stringPreferencesKey("great")
        private val KEY_ULTRA = stringPreferencesKey("ultra")
        private val KEY_LEFT_P = stringPreferencesKey("left_p")
        private val KEY_RIGHT_P = stringPreferencesKey("right_p")
        private val KEY_UP_P = stringPreferencesKey("up_p")
        private val KEY_DOWN_P = stringPreferencesKey("down_p")
        private val KEY_SWIPE_C = stringPreferencesKey("swipe_c")
        private val KEY_SWIPE_D = intPreferencesKey("swipe_d")
        private val KEY_OVERLAY_PX = intPreferencesKey("overlay_px")
        private val KEY_OVERLAY_PY = intPreferencesKey("overlay_py")
        private val KEY_WN_GIVE_R = stringPreferencesKey("wn_give_r")
        private val KEY_WN_FOLLOW_R = stringPreferencesKey("wn_follow_r")
        private val KEY_WN_FOLLOW_P = stringPreferencesKey("wn_follow_p")
        private val KEY_WN_SWIPE_P = stringPreferencesKey("wn_swipe_p")
        private val KEY_WN_SWIPE_D = intPreferencesKey("wn_swipe_d")
    }
}
