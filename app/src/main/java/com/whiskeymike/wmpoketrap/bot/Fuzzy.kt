package com.whiskeymike.wmpoketrap.bot

import kotlin.math.max
import kotlin.math.min

/** Lightweight RapidFuzz-style scorers for Pokémon name matching. */
object Fuzzy {
    fun ratio(a: String, b: String): Int {
        if (a.isEmpty() && b.isEmpty()) return 100
        if (a.isEmpty() || b.isEmpty()) return 0
        val dist = levenshtein(a, b)
        val len = max(a.length, b.length)
        return ((1.0 - dist.toDouble() / len) * 100).toInt().coerceIn(0, 100)
    }

    fun partialRatio(a: String, b: String): Int {
        val shorter: String
        val longer: String
        if (a.length <= b.length) {
            shorter = a; longer = b
        } else {
            shorter = b; longer = a
        }
        if (shorter.isEmpty()) return 0
        if (longer.contains(shorter)) return 100
        var best = 0
        val window = shorter.length
        for (i in 0..longer.length - window) {
            best = max(best, ratio(shorter, longer.substring(i, i + window)))
        }
        // Also try slightly shorter windows for OCR cuts.
        for (w in (window - 2).coerceAtLeast(3)..window) {
            for (i in 0..longer.length - w) {
                best = max(best, ratio(shorter.take(w), longer.substring(i, i + w)))
            }
        }
        return best
    }

    fun tokenSetRatio(a: String, b: String): Int {
        val ta = a.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        val tb = b.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return ratio(a, b)
        val inter = ta.intersect(tb).sorted().joinToString(" ")
        val sa = (ta.sorted()).joinToString(" ")
        val sb = (tb.sorted()).joinToString(" ")
        return maxOf(ratio(inter, sa), ratio(inter, sb), ratio(sa, sb))
    }

    fun wRatio(a: String, b: String): Int {
        val aa = a.trim()
        val bb = b.trim()
        if (aa.isEmpty() || bb.isEmpty()) return 0
        return maxOf(ratio(aa, bb), partialRatio(aa, bb), tokenSetRatio(aa, bb))
    }

    fun extractOne(query: String, choices: List<String>): Pair<String, Int>? {
        if (query.isBlank() || choices.isEmpty()) return null
        var bestName = ""
        var bestScore = -1
        for (c in choices) {
            val s = wRatio(query, c)
            if (s > bestScore) {
                bestScore = s
                bestName = c
            }
        }
        return if (bestScore < 0) null else bestName to bestScore
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        var prev = IntArray(n + 1) { it }
        var cur = IntArray(n + 1)
        for (i in 1..m) {
            cur[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[n]
    }
}
