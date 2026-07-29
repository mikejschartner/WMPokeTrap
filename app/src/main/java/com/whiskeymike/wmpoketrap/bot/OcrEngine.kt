package com.whiskeymike.wmpoketrap.bot

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.min

class OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun readText(full: Bitmap, region: ScreenRect): String = withContext(Dispatchers.Default) {
        if (!region.valid() || full.width <= 0) return@withContext ""
        val crop = crop(full, region) ?: return@withContext ""
        try {
            val image = InputImage.fromBitmap(crop, 0)
            val result = recognizer.process(image).await()
            result.text.trim()
        } catch (_: Exception) {
            ""
        } finally {
            if (crop !== full) crop.recycle()
        }
    }

    /** Downscaled OCR — much faster for battle Fight/Bag/Run checks while walking. */
    suspend fun readTextFast(full: Bitmap, region: ScreenRect, maxWidth: Int = 220): String =
        withContext(Dispatchers.Default) {
            if (!region.valid() || full.width <= 0) return@withContext ""
            val crop = crop(full, region) ?: return@withContext ""
            var scaled: Bitmap? = null
            try {
                val use = if (crop.width > maxWidth) {
                    val h = (crop.height * maxWidth / crop.width).coerceAtLeast(12)
                    Bitmap.createScaledBitmap(crop, maxWidth, h, true).also { scaled = it }
                } else {
                    crop
                }
                val image = InputImage.fromBitmap(use, 0)
                val result = recognizer.process(image).await()
                result.text.trim()
            } catch (_: Exception) {
                ""
            } finally {
                scaled?.recycle()
                if (crop !== full) crop.recycle()
            }
        }

    /**
     * Instant (no ML Kit) guess that the battle menu is on screen.
     * Looks for dark panel + bright glyphs and low grass-green content in the battle region.
     */
    fun battleMenuLikely(full: Bitmap, region: ScreenRect): Boolean {
        if (!region.valid() || full.width <= 0) return false
        val crop = crop(full, region) ?: return false
        var small: Bitmap? = null
        try {
            small = Bitmap.createScaledBitmap(crop, 40, 20, true)
            val w = small.width
            val h = small.height
            val n = w * h
            if (n < 8) return false
            val px = IntArray(n)
            small.getPixels(px, 0, w, 0, 0, w, h)
            var greenish = 0
            var bright = 0
            var dark = 0
            var panel = 0
            for (c in px) {
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val lum = (r * 3 + g * 4 + b) / 8
                if (g > r + 18 && g > b + 12) greenish++
                if (lum >= 195) bright++
                if (lum <= 55) dark++
                if (lum in 25..110 && (b >= g || r in 35..130)) panel++
            }
            val greenRatio = greenish.toFloat() / n
            val brightRatio = bright.toFloat() / n
            val darkRatio = dark.toFloat() / n
            val panelRatio = panel.toFloat() / n
            val contrastMenu = brightRatio >= 0.025f && darkRatio >= 0.20f && greenRatio < 0.18f
            val panelMenu = panelRatio >= 0.26f && greenRatio < 0.20f && brightRatio >= 0.02f
            return contrastMenu || panelMenu
        } catch (_: Exception) {
            return false
        } finally {
            small?.recycle()
            if (crop !== full) crop.recycle()
        }
    }

    /** Compact fingerprint of a region for instant scene-change detection. */
    fun regionFingerprint(full: Bitmap, region: ScreenRect): IntArray? {
        if (!region.valid() || full.width <= 0) return null
        val crop = crop(full, region) ?: return null
        var small: Bitmap? = null
        try {
            small = Bitmap.createScaledBitmap(crop, 16, 10, true)
            val n = small.width * small.height
            val out = IntArray(n)
            small.getPixels(out, 0, small.width, 0, 0, small.width, small.height)
            // Quantize to cut noise.
            for (i in out.indices) {
                val c = out[i]
                val r = Color.red(c) / 24
                val g = Color.green(c) / 24
                val b = Color.blue(c) / 24
                out[i] = (r shl 10) or (g shl 5) or b
            }
            return out
        } catch (_: Exception) {
            return null
        } finally {
            small?.recycle()
            if (crop !== full) crop.recycle()
        }
    }

    fun fingerprintDelta(a: IntArray?, b: IntArray?): Float {
        if (a == null || b == null || a.size != b.size || a.isEmpty()) return 0f
        var diff = 0
        for (i in a.indices) if (a[i] != b[i]) diff++
        return diff.toFloat() / a.size
    }

    fun hpRatio(full: Bitmap, region: ScreenRect): Float {
        if (!region.valid()) return 1f
        val crop = crop(full, region) ?: return 1f
        try {
            val w = crop.width
            val h = crop.height
            if (w < 2 || h < 2) return 1f
            val row = IntArray(w)
            val active = mutableListOf<Int>()
            val mid = h / 2
            crop.getPixels(row, 0, w, 0, mid, w, 1)
            for (x in 0 until w) {
                val c = row[x]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val isBar = (g > 90 && g >= r && g >= b) || // green
                    (r > 140 && g > 90 && b < 80) || // yellow
                    (r > 140 && g < 90 && b < 90) // red
                if (isBar) active += x
            }
            if (active.isEmpty()) return 0f
            return min(1f, (active.last() - active.first() + 1).toFloat() / w.toFloat())
        } finally {
            if (crop !== full) crop.recycle()
        }
    }

    private fun crop(full: Bitmap, region: ScreenRect): Bitmap? {
        val l = region.left.coerceIn(0, full.width - 1)
        val t = region.top.coerceIn(0, full.height - 1)
        val r = region.right.coerceIn(l + 1, full.width)
        val b = region.bottom.coerceIn(t + 1, full.height)
        val w = r - l
        val h = b - t
        if (w < 2 || h < 2) return null
        return Bitmap.createBitmap(full, l, t, w, h)
    }
}
