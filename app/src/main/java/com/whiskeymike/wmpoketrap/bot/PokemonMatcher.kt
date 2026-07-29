package com.whiskeymike.wmpoketrap.bot

import android.content.Context
import com.whiskeymike.wmpoketrap.data.PokemonCatalog

class PokemonMatcher(private val context: Context) {
    private val dex by lazy { PokemonCatalog.all(context) }

    fun cleanName(raw: String): String {
        var s = raw.replace("♀", "").replace("♂", "").replace("|", "I")
        s = s.replace(Regex("""(?i)\blv\.?\s*\d+\b"""), " ")
        s = s.replace(Regex("""(?i)\blevel\s*\d+\b"""), " ")
        s = s.replace(Regex("""\d+"""), " ")
        s = s.filter { it.isLetter() || it == ' ' || it == '-' || it == '\'' || it == '.' }
        return s.replace(Regex("""\s+"""), " ").trim()
    }

    fun identity(rawName: String): Pair<String, Int> {
        if (rawName.isBlank()) return "" to 0
        val a = rawName.lowercase().trim()
        val aCompact = a.filter { it.isLetterOrDigit() }

        for (name in dex) {
            val b = name.lowercase()
            val bCompact = b.filter { it.isLetterOrDigit() }
            if (a == b || aCompact == bCompact) return name to 100
            if (b in a || (a in b && aCompact.length >= maxOf(4, bCompact.length - 1))) {
                return name to 96
            }
        }

        val result = Fuzzy.extractOne(rawName, dex) ?: return "" to 0
        val (name, wratio) = result
        val confirm = maxOf(
            Fuzzy.ratio(a, name.lowercase()),
            Fuzzy.partialRatio(a, name.lowercase()),
            Fuzzy.tokenSetRatio(a, name.lowercase()),
        )
        var confidence = ((wratio * 0.55) + (confirm * 0.45)).toInt()
        if (kotlin.math.abs(wratio - confirm) > 25) {
            confidence = minOf(wratio, confirm)
        }
        return name to confidence
    }

    fun scoreAgainst(rawName: String, species: String): Int {
        if (rawName.isBlank() || species.isBlank()) return 0
        val a = rawName.lowercase().trim()
        val b = species.lowercase().trim()
        if (a == b) return 100
        return maxOf(
            Fuzzy.wRatio(a, b),
            Fuzzy.partialRatio(a, b),
            Fuzzy.tokenSetRatio(a, b),
            Fuzzy.ratio(a, b),
        )
    }

    /** Catch when the species matches any selected target. */
    fun decideCatch(rawName: String, targets: List<String>, threshold: Int): DecideResult {
        val cleanTargets = targets.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanTargets.isEmpty()) {
            return DecideResult(false, "", 0, "no target set")
        }
        if (cleanTargets.size == 1) {
            return decideCatch(rawName, cleanTargets[0], threshold)
        }
        var bestCatch: DecideResult? = null
        var bestAny = DecideResult(false, "", 0, "no text read")
        for (target in cleanTargets) {
            val d = decideCatch(rawName, target, threshold)
            if (d.score > bestAny.score) bestAny = d
            if (d.catch && (bestCatch == null || d.score > bestCatch.score)) {
                bestCatch = d
            }
        }
        return bestCatch ?: bestAny
    }

    /** Catch only when identified species is the selected target (accurate, species-agnostic). */
    fun decideCatch(rawName: String, target: String, threshold: Int): DecideResult {
        if (rawName.isBlank()) return DecideResult(false, "", 0, "no text read")
        val minId = maxOf(72, threshold)
        val (species, confidence) = identity(rawName)
        val targetScore = scoreAgainst(rawName, target)

        if (species.isNotBlank() && species.equals(target, true) && confidence >= minId) {
            return DecideResult(true, species, confidence, "identified target")
        }
        if (targetScore >= minId && (
                species.isBlank() ||
                    species.equals(target, true) ||
                    targetScore >= confidence + 8
                )
        ) {
            if (
                species.isNotBlank() &&
                !species.equals(target, true) &&
                confidence >= minId &&
                confidence >= targetScore + 10
            ) {
                return DecideResult(false, species, confidence, "identified as $species")
            }
            return DecideResult(true, target, maxOf(targetScore, confidence), "target text match")
        }
        if (species.isNotBlank() && !species.equals(target, true) && confidence >= minId) {
            return DecideResult(false, species, confidence, "identified as $species")
        }
        return DecideResult(false, species.ifBlank { rawName }, maxOf(confidence, targetScore), "low confidence")
    }

    data class DecideResult(
        val catch: Boolean,
        val name: String,
        val score: Int,
        val reason: String,
    )
}
