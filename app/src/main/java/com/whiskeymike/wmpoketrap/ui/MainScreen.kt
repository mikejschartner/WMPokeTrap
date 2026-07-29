package com.whiskeymike.wmpoketrap.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whiskeymike.wmpoketrap.bot.BotStats
import com.whiskeymike.wmpoketrap.bot.ScreenPoint
import com.whiskeymike.wmpoketrap.bot.ScreenRect
import com.whiskeymike.wmpoketrap.bot.TrapSettings
import com.whiskeymike.wmpoketrap.data.PokemonCatalog

private val Purple = Color(0xFF8B5CF6)
private val Panel = Color(0xFF111116)
private val Panel2 = Color(0xFF17171E)
private val Muted = Color(0xFFA1A1AA)
private val Success = Color(0xFF22C55E)
private val Danger = Color(0xFFDC2626)

@Composable
fun MainScreen(
    settings: TrapSettings,
    stats: BotStats,
    wnStats: BotStats = BotStats(mode = "wn"),
    accessibilityOn: Boolean,
    overlayOn: Boolean,
    onSave: (TrapSettings) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onStartBot: () -> Unit,
    onPauseBot: () -> Unit,
    onStopBot: () -> Unit,
    onStartWn: () -> Unit = {},
    onPauseWn: () -> Unit = {},
    onStopWn: () -> Unit = {},
    onCalibrate: (String) -> Unit,
    onStartCalibrationWizard: () -> Unit,
    onStartWnCalibrationWizard: () -> Unit = {},
    updateStatus: String = "",
    updateBusy: Boolean = false,
    onCheckUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    updateReady: Boolean = false,
) {
    var tab by remember { mutableIntStateOf(0) }
    var draft by remember(settings) { mutableStateOf(settings) }
    val liveStats = if (wnStats.running) wnStats else stats

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07070A))
            .padding(16.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Black-background art blends straight into the app background.
            Image(
                painter = painterResource(id = com.whiskeymike.wmpoketrap.R.drawable.gengar_header),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(140.dp),
                contentScale = ContentScale.Fit,
            )
            Column {
                Text("WHISKEY MIKE'S", color = Purple, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("WM POKETRAP", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Phone farming • WN giveaways • Accessibility taps",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        UpdateBanner(
            updateStatus = updateStatus,
            updateBusy = updateBusy,
            updateReady = updateReady,
            onCheckUpdate = onCheckUpdate,
            onInstallUpdate = onInstallUpdate,
        )

        Spacer(modifier = Modifier.height(8.dp))
        StatusPill(liveStats)

        Spacer(modifier = Modifier.height(8.dp))
        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = Panel,
            edgePadding = 0.dp,
        ) {
            listOf("Farm", "WN", "Calibration", "Setup").forEachIndexed { i, label ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(label) },
                    selectedContentColor = Purple,
                    unselectedContentColor = Muted,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        when (tab) {
            0 -> FarmTab(
                draft = draft,
                stats = stats,
                onDraft = { draft = it },
                onSave = { onSave(draft) },
                onStart = {
                    onSave(draft)
                    onStartOverlay()
                    onStartBot()
                },
                onPause = onPauseBot,
                onStop = onStopBot,
                onStartCalibrationWizard = onStartCalibrationWizard,
            )
            1 -> WnTab(
                draft = draft,
                stats = wnStats,
                onDraft = { draft = it },
                onSave = { onSave(draft) },
                onStart = {
                    onSave(draft)
                    onStartOverlay()
                    onStartWn()
                },
                onPause = onPauseWn,
                onStop = onStopWn,
                onCalibrate = onCalibrate,
                onStartWnCalibrationWizard = onStartWnCalibrationWizard,
            )
            2 -> CalibrationTab(
                draft = draft,
                onCalibrate = onCalibrate,
                onStartCalibrationWizard = onStartCalibrationWizard,
            ) { draft = it; onSave(it) }
            else -> SetupTab(
                accessibilityOn = accessibilityOn,
                overlayOn = overlayOn,
                draft = draft,
                onDraft = { draft = it },
                onSave = { onSave(draft) },
                onOpenAccessibility = onOpenAccessibility,
                onOpenOverlayPermission = onOpenOverlayPermission,
                onStartOverlay = onStartOverlay,
                onStopOverlay = onStopOverlay,
                updateStatus = updateStatus,
                updateBusy = updateBusy,
                updateReady = updateReady,
                onCheckUpdate = onCheckUpdate,
                onInstallUpdate = onInstallUpdate,
            )
        }
    }
}

@Composable
private fun UpdateBanner(
    updateStatus: String,
    updateBusy: Boolean,
    updateReady: Boolean,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel2, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "UPDATES",
                color = Purple,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                updateStatus.ifBlank { "Checking GitHub for updates..." },
                color = Color.White,
                fontSize = 12.sp,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (updateReady) {
            Button(
                onClick = onInstallUpdate,
                enabled = !updateBusy,
                colors = ButtonDefaults.buttonColors(containerColor = Success),
            ) { Text("Install", fontSize = 12.sp) }
        } else {
            Button(
                onClick = onCheckUpdate,
                enabled = !updateBusy,
                colors = ButtonDefaults.buttonColors(containerColor = Purple),
            ) { Text(if (updateBusy) "..." else "Check", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun StatusPill(stats: BotStats) {
    val label = when {
        stats.running && stats.mode == "wn" -> "● WN"
        stats.running -> "● FARM"
        else -> "○ IDLE"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (stats.running) Color(0xFF0F1F14) else Panel2, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (stats.running) Success else Muted,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            stats.status,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WnTab(
    draft: TrapSettings,
    stats: BotStats,
    onDraft: (TrapSettings) -> Unit,
    onSave: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onCalibrate: (String) -> Unit,
    onStartWnCalibrationWizard: () -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        PanelCard("WHATNOT (WN)") {
            Text(
                "Stay on one live: tap Giveaway + Follow, watch chat for a winner, then re-enter.\n" +
                    "No swipe (leaving can drop your entry). Farm stays separate.",
                color = Muted,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            StatLine("Status", stats.status)
            StatLine("Entered this run", stats.wnEntered.toString())
            StatLine("Last OCR", stats.detected)
        }
        Spacer(modifier = Modifier.height(10.dp))
        PanelCard("WN CALIBRATION") {
            Button(
                onClick = onStartWnCalibrationWizard,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Purple),
            ) { Text("START WN OVERLAY WIZARD") }
            Spacer(modifier = Modifier.height(8.dp))
            CalRow("Giveaway / Enter box", draft.wnGiveawayRegion) {
                onCalibrate("wn_giveaway_region")
            }
            CalRow("Follow popup (optional)", draft.wnFollowRegion) {
                onCalibrate("wn_follow_region")
            }
            CalPoint("Follow button (optional)", draft.wnFollowPoint) {
                onCalibrate("wn_follow_point")
            }
            CalPoint("Swipe-up start (unused — kept; also anchors winner watch)", draft.wnSwipePoint) {
                onCalibrate("wn_swipe_point")
            }
            NumberField("Swipe distance (unused)", draft.wnSwipeDistance.toString()) {
                onDraft(
                    draft.copy(
                        wnSwipeDistance = it.toIntOrNull()?.coerceIn(200, 2000)
                            ?: draft.wnSwipeDistance,
                    ),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onStart,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
            ) { Text("START WN") }
            Button(
                onClick = onPause,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Panel2),
            ) { Text("PAUSE") }
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
            ) { Text("STOP") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Panel2),
        ) { Text("SAVE WN SETTINGS") }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Loop: tap Giveaway → tap Follow → watch for “won” → wait 2s → re-enter. No swipe.",
            color = Muted,
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FarmTab(
    draft: TrapSettings,
    stats: BotStats,
    onDraft: (TrapSettings) -> Unit,
    onSave: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onStartCalibrationWizard: () -> Unit,
) {
    val context = LocalContext.current
    val all = remember { PokemonCatalog.all(context) }
    var query by remember(draft.targetPokemon) { mutableStateOf(draft.targetPokemon) }
    var query2 by remember(draft.targetPokemon2) { mutableStateOf(draft.targetPokemon2) }
    fun matchList(qRaw: String): List<String> {
        val q = qRaw.trim()
        if (q.isEmpty()) return all.take(30)
        val starts = all.filter { it.startsWith(q, ignoreCase = true) }
        val contains = all.filter { it.contains(q, ignoreCase = true) && it !in starts }
        return (starts + contains).take(40)
    }
    val matches = remember(query) { matchList(query) }
    val matches2 = remember(query2) { matchList(query2) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        PanelCard("TARGET POKÉMON") {
            Text("Target 1 (required)", color = Muted, fontSize = 12.sp)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("First Pokémon") },
                colors = fieldColors(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            matches.take(6).forEach { name ->
                Text(
                    text = name,
                    color = if (name == draft.targetPokemon) Purple else Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            query = name
                            onDraft(draft.copy(targetPokemon = name))
                        }
                        .padding(vertical = 6.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Target 2 (optional)", color = Muted, fontSize = 12.sp)
            OutlinedTextField(
                value = query2,
                onValueChange = {
                    query2 = it
                    if (it.isBlank()) onDraft(draft.copy(targetPokemon2 = ""))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Second Pokémon — leave blank for one target") },
                colors = fieldColors(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (draft.targetPokemon2.isNotBlank()) {
                Text(
                    text = "Clear second target",
                    color = Danger,
                    modifier = Modifier
                        .clickable {
                            query2 = ""
                            onDraft(draft.copy(targetPokemon2 = ""))
                        }
                        .padding(vertical = 4.dp),
                )
            }
            matches2.take(6).forEach { name ->
                Text(
                    text = name,
                    color = if (name == draft.targetPokemon2) Purple else Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            query2 = name
                            onDraft(draft.copy(targetPokemon2 = name))
                        }
                        .padding(vertical = 6.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        PanelCard("FARM SETTINGS") {
            NumberField("Catch goal", draft.catchGoal.toString()) {
                onDraft(draft.copy(catchGoal = it.toIntOrNull()?.coerceAtLeast(1) ?: draft.catchGoal))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Movement", color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Horizontal", "Vertical", "Custom", "Hold").forEach { mode ->
                    FilterChip(
                        selected = draft.movementMode == mode ||
                            (mode == "Hold" && draft.movementMode == "Swipe"),
                        onClick = { onDraft(draft.copy(movementMode = mode)) },
                        label = { Text(mode, fontSize = 11.sp) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    NumberField("Left", draft.leftSteps.toString()) {
                        onDraft(draft.copy(leftSteps = it.toIntOrNull()?.coerceAtLeast(0) ?: 0))
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    NumberField("Right", draft.rightSteps.toString()) {
                        onDraft(draft.copy(rightSteps = it.toIntOrNull()?.coerceAtLeast(0) ?: 0))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Catch any shiny",
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = draft.catchAnyShiny,
                    onCheckedChange = { onDraft(draft.copy(catchAnyShiny = it)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Use False Swipe",
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = draft.useFalseSwipe,
                    onCheckedChange = { onDraft(draft.copy(useFalseSwipe = it)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Humanize inputs",
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = draft.humanizeInputs,
                    onCheckedChange = { onDraft(draft.copy(humanizeInputs = it)) },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Approved balls", color = Muted, fontSize = 12.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Poké Ball", color = Color.White, modifier = Modifier.weight(1f))
                Switch(
                    checked = draft.allowPokeBall,
                    onCheckedChange = { onDraft(draft.copy(allowPokeBall = it)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Great Ball", color = Color.White, modifier = Modifier.weight(1f))
                Switch(
                    checked = draft.allowGreatBall,
                    onCheckedChange = { onDraft(draft.copy(allowGreatBall = it)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ultra Ball", color = Color.White, modifier = Modifier.weight(1f))
                Switch(
                    checked = draft.allowUltraBall,
                    onCheckedChange = { onDraft(draft.copy(allowUltraBall = it)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Stop if approved ball unavailable",
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = draft.stopWhenBallUnavailable,
                    onCheckedChange = { onDraft(draft.copy(stopWhenBallUnavailable = it)) },
                )
            }
            OutlinedTextField(
                value = draft.ballPriority,
                onValueChange = { onDraft(draft.copy(ballPriority = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ball priority (comma-separated)") },
                colors = fieldColors(),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        PanelCard("LIVE STATUS") {
            StatLine("Detected", stats.detected)
            StatLine("Caught", stats.caught.toString())
            StatLine("Shinies", stats.shinies.toString())
            StatLine("Encounters", stats.encounters.toString())
            StatLine("HP", stats.hp)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onStart,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Purple),
            ) { Text("START") }
            Button(
                onClick = onPause,
                colors = ButtonDefaults.buttonColors(containerColor = Panel2),
            ) { Text("PAUSE") }
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
            ) { Text("STOP") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onStartCalibrationWizard,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
        ) { Text("CALIBRATE OVER GAME (recommended)") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Panel2),
        ) { Text("SAVE SETTINGS") }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CalibrationTab(
    draft: TrapSettings,
    onCalibrate: (String) -> Unit,
    onStartCalibrationWizard: () -> Unit,
    onChanged: (TrapSettings) -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Button(
            onClick = onStartCalibrationWizard,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Purple),
        ) { Text("START OVERLAY WIZARD") }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Best: open a battle, tap the button above, stay on the game. " +
                "The wizard walks you through every region/tap — no tab switching. " +
                "SELECT below is only for fixing one item.",
            color = Muted,
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        PanelCard("SCREEN REGIONS") {
            CalRow("Battle menu", draft.battleRegion) { onCalibrate("battle_region") }
            CalRow("Opponent name", draft.nameRegion) { onCalibrate("name_region") }
            CalRow("Shiny marker", draft.shinyRegion) { onCalibrate("shiny_region") }
            if (draft.useFalseSwipe) {
                CalRow("HP bar (False Swipe on)", draft.hpRegion) { onCalibrate("hp_region") }
            }
            CalRow("Battle message", draft.messageRegion) { onCalibrate("message_region") }
            CalRow("Bag ball name(s)", draft.ballNameRegion) { onCalibrate("ball_name_region") }
        }
        Spacer(modifier = Modifier.height(10.dp))
        PanelCard("TAP POINTS") {
            if (draft.useFalseSwipe) {
                CalPoint("Fight", draft.fightPoint) { onCalibrate("fight_point") }
            }
            CalPoint("Run / flee", draft.runPoint) { onCalibrate("run_point") }
            if (draft.useFalseSwipe) {
                CalPoint("False Swipe", draft.falseSwipePoint) { onCalibrate("false_swipe_point") }
            }
            CalPoint("Items", draft.itemsPoint) { onCalibrate("items_point") }
            if (draft.allowPokeBall) {
                CalPoint("Poké Ball", draft.pokeballPoint) { onCalibrate("pokeball_point") }
            }
            if (draft.allowGreatBall) {
                CalPoint("Great Ball", draft.greatballPoint) { onCalibrate("greatball_point") }
            }
            if (draft.allowUltraBall) {
                CalPoint("Ultra Ball", draft.ultraballPoint) { onCalibrate("ultraball_point") }
            }
            CalPoint("D-pad Left (optional)", draft.leftPoint) { onCalibrate("left_point") }
            CalPoint("D-pad Right (optional)", draft.rightPoint) { onCalibrate("right_point") }
            CalPoint("Walk stick center (or drag ◎ STICK on overlay)", draft.swipeCenter) {
                onCalibrate("swipe_center")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        NumberField("Hold offset fallback (px)", draft.swipeDistance.toString()) {
            onChanged(
                draft.copy(
                    swipeDistance = it.toIntOrNull()?.coerceIn(80, 800) ?: draft.swipeDistance,
                ),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SetupTab(
    accessibilityOn: Boolean,
    overlayOn: Boolean,
    draft: TrapSettings,
    onDraft: (TrapSettings) -> Unit,
    onSave: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    updateStatus: String,
    updateBusy: Boolean,
    updateReady: Boolean,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        PanelCard("UPDATES") {
            Text(
                "App checks GitHub for newer builds. Install keeps your settings.",
                color = Muted,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(updateStatus.ifBlank { "Ready to check for updates" }, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCheckUpdate,
                    enabled = !updateBusy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple),
                ) { Text(if (updateBusy) "Working…" else "Check Update") }
                Button(
                    onClick = onInstallUpdate,
                    enabled = updateReady && !updateBusy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                ) { Text("Install Update") }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        PanelCard("REQUIRED PERMISSIONS") {
            Text(
                if (accessibilityOn) {
                    "Accessibility: ON"
                } else {
                    "Accessibility: OFF — required for taps + screenshots"
                },
                color = if (accessibilityOn) Success else Danger,
            )
            Button(
                onClick = onOpenAccessibility,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open Accessibility Settings") }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (overlayOn) {
                    "Overlay: allowed"
                } else {
                    "Overlay: needed for floating START/STOP"
                },
                color = if (overlayOn) Success else Danger,
            )
            Button(
                onClick = onOpenOverlayPermission,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Allow Display Over Other Apps") }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStartOverlay,
                    modifier = Modifier.weight(1f),
                ) { Text("Show Overlay") }
                Button(
                    onClick = onStopOverlay,
                    modifier = Modifier.weight(1f),
                ) { Text("Hide Overlay") }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        PanelCard("TIMING / OCR") {
            NumberField("Step delay ms", draft.stepDelayMs.toString()) {
                onDraft(
                    draft.copy(
                        stepDelayMs = it.toLongOrNull()?.coerceAtLeast(50) ?: draft.stepDelayMs,
                    ),
                )
            }
            NumberField("Action delay ms", draft.actionDelayMs.toString()) {
                onDraft(
                    draft.copy(
                        actionDelayMs = it.toLongOrNull()?.coerceAtLeast(200) ?: draft.actionDelayMs,
                    ),
                )
            }
            NumberField("OCR match threshold", draft.ocrMatchThreshold.toString()) {
                onDraft(
                    draft.copy(
                        ocrMatchThreshold = it.toIntOrNull()?.coerceIn(50, 95)
                            ?: draft.ocrMatchThreshold,
                    ),
                )
            }
            OutlinedTextField(
                value = draft.battleTextHint,
                onValueChange = { onDraft(draft.copy(battleTextHint = it)) },
                label = { Text("Battle text hint") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            OutlinedTextField(
                value = draft.caughtTextHint,
                onValueChange = { onDraft(draft.copy(caughtTextHint = it)) },
                label = { Text("Caught text hint") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Purple),
            ) { Text("SAVE") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "How to use:\n" +
                "1) Enable Accessibility for WM PokeTrap\n" +
                "2) Allow overlay permission\n" +
                "3) Calibrate regions/points in battle\n" +
                "4) Pick target Pokémon\n" +
                "5) Open the game, press START (or overlay START)",
            color = Muted,
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PanelCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(title, color = Purple, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel2, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 13.sp)
    }
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun CalRow(label: String, rect: ScreenRect, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                if (rect.valid()) {
                    "${rect.left},${rect.top} → ${rect.right},${rect.bottom}"
                } else {
                    "Not set"
                },
                color = if (rect.valid()) Purple else Muted,
                fontSize = 11.sp,
            )
        }
        Button(
            onClick = onSelect,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1065)),
        ) { Text("SELECT") }
    }
}

@Composable
private fun CalPoint(label: String, point: ScreenPoint, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                if (point.valid()) "${point.x}, ${point.y}" else "Not set",
                color = if (point.valid()) Purple else Muted,
                fontSize = 11.sp,
            )
        }
        Button(
            onClick = onSelect,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1065)),
        ) { Text("SELECT") }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = fieldColors(),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Purple,
    unfocusedBorderColor = Color(0xFF2E1065),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Muted,
    unfocusedLabelColor = Muted,
    cursorColor = Purple,
)
