package org.ntust.app.tigerduck.ui.theme

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.LocaleList
import android.text.TextPaint
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ntust.app.tigerduck.BuildConfig
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Raises text so CJK glyphs sit in the middle of their line, as Latin ones do.
 *
 * The system's CJK font leaves more room above its glyphs than below them. A
 * line that holds CJK text is as tall as that font, whatever the style's line
 * height (recent Android keeps the fallback font's height for the line), so
 * the glyphs land about 1 dp low: Settings' rows, buttons and every other
 * piece of Chinese text sit a little below the icons and switches beside them.
 *
 * A [BaselineShift] raises every line's glyphs. It moves a Latin-only line
 * too, whose height does follow the style, so the [LineHeightStyle] alignment
 * puts that line back where it was. Both are measured on the device, per
 * style, since phones ship different CJK fonts; where the shift wouldn't
 * help, the style is left as it is.
 */
internal object CjkCentering {

    /** What the measurements need from a laid-out line, in px. */
    data class Line(val baseline: Float, val height: Float) {
        /** The baseline from the middle of the line, where a row centers it. */
        val fromMiddle: Float get() = baseline - height / 2f
    }

    /**
     * Lays [text] out on one line, as Text would, in a style aligned by
     * [topRatio] and raised by [shift] ([aligned]). The device's own is a
     * [TextMeasurer]; tests give a model of one.
     */
    fun interface LineLayout {
        fun line(text: String, style: TextStyle, topRatio: Float, shift: Float): Line
    }

    /** The glyph metrics the measurements need, in px, for a style's size and weight. */
    interface Glyphs {
        /** The primary font's ascent, positive, which a [BaselineShift] is a fraction of. */
        fun ascent(style: TextStyle): Float

        /** The middle of [text]'s ink, from the baseline: negative is above it. */
        fun inkMiddle(text: String, style: TextStyle): Float
    }

    /** A style's [BaselineShift] and its [LineHeightStyle.Alignment]'s top ratio. */
    data class Correction(val shift: Float, val topRatio: Float)

    const val CJK_SAMPLE = "中文字"
    const val LATIN_SAMPLE = "Hxg"

    /**
     * How to raise [style]'s glyphs so [CJK_SAMPLE]'s ink sits in the middle of
     * its line, and align Latin-only lines to stay where they were; null to
     * leave the style as it is.
     *
     * Where a line has no room to put Latin back (a line height no taller than
     * the font), raising CJK the whole way would lift Latin as far: the raise
     * is then the one that leaves the larger of the two the smallest.
     */
    fun correction(style: TextStyle, layout: LineLayout, glyphs: Glyphs): Correction? {
        val low = cjkOffset(style, 0.5f, 0f, layout, glyphs)
        val most = low.roundToInt()
        if (most <= 0) return null
        val ascent = glyphs.ascent(style)
        if (ascent <= 0f) return null
        // Where a Latin line's glyphs sit, from the middle of the line: rows center their text.
        val latinBefore = layout.line(LATIN_SAMPLE, style, 0.5f, 0f).fromMiddle

        var best: Correction? = null
        var bestOff = abs(low)
        for (raise in most downTo 1) {
            // ceil(-ascent * shift) is the shift in whole px Compose applies: `raise` px up.
            val shift = (raise + 0.5f) / ascent
            val shiftPx = shiftPx(ascent, shift)
            // A Latin line's glyphs sit at its baseline plus the shift. Put them where they
            // were, through the alignment, as far as its line height leaves room to.
            val atHalf = layout.line(LATIN_SAMPLE, style, 0.5f, shift).fromMiddle
            val atTop = layout.line(LATIN_SAMPLE, style, 1f, shift).fromMiddle
            val perRatio = (atTop - atHalf) / 0.5f
            val ratio = if (abs(perRatio) < 0.5f) {
                0.5f
            } else {
                (0.5f + (latinBefore - shiftPx - atHalf) / perRatio).coerceIn(0f, 1f)
            }
            val latinMoved = layout.line(LATIN_SAMPLE, style, ratio, shift).fromMiddle + shiftPx - latinBefore
            // Where the device lays CJK lines out some other way, the shift may not help.
            val cjkLow = cjkOffset(style, ratio, shift, layout, glyphs)
            val off = maxOf(abs(cjkLow), abs(latinMoved))
            if (off < bestOff) {
                best = Correction(shift, ratio)
                bestOff = off
            }
            // Latin kept in place: a smaller raise would only leave CJK lower.
            if (abs(latinMoved) <= 0.5f) break
        }
        return best
    }

    /** [style], corrected; see [correction]. */
    fun centered(style: TextStyle, layout: LineLayout, glyphs: Glyphs): TextStyle =
        correction(style, layout, glyphs)?.let { style.aligned(it.topRatio, it.shift) } ?: style

    /** How far [CJK_SAMPLE]'s ink sits below the middle of its line, in px. */
    fun cjkOffset(style: TextStyle, topRatio: Float, shift: Float, layout: LineLayout, glyphs: Glyphs): Float {
        val line = layout.line(CJK_SAMPLE, style, topRatio, shift)
        return line.baseline + shiftPx(glyphs.ascent(style), shift) + glyphs.inkMiddle(CJK_SAMPLE, style) - line.height / 2f
    }

    /** The px a [BaselineShift] of [shift] moves glyphs by, as Compose rounds it: negative is up. */
    fun shiftPx(ascent: Float, shift: Float): Float = ceil(-ascent * shift)

    /** [this] aligned in its line by [topRatio] and raised by [shift]. */
    fun TextStyle.aligned(topRatio: Float, shift: Float): TextStyle {
        val current = lineHeightStyle ?: LineHeightStyle.Default
        return copy(
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment(topRatio), current.trim, current.mode),
            baselineShift = BaselineShift(shift),
        )
    }

    /** The styles of a [Typography], in the order [corrections] lists them. */
    private val Typography.styles: List<TextStyle>
        get() = listOf(
            displayLarge, displayMedium, displaySmall,
            headlineLarge, headlineMedium, headlineSmall,
            titleLarge, titleMedium, titleSmall,
            bodyLarge, bodyMedium, bodySmall,
            labelLarge, labelMedium, labelSmall,
        )

    /** A [correction] for each style of [typography]. */
    fun corrections(typography: Typography, layout: LineLayout, glyphs: Glyphs): List<Correction?> =
        typography.styles.map { correction(it, layout, glyphs) }

    /** [typography] with [corrections], from [corrections], applied. */
    fun applied(typography: Typography, corrections: List<Correction?>): Typography {
        val styles = typography.styles.zip(corrections) { style, c -> c?.let { style.aligned(it.topRatio, it.shift) } ?: style }
        return typography.copy(
            displayLarge = styles[0], displayMedium = styles[1], displaySmall = styles[2],
            headlineLarge = styles[3], headlineMedium = styles[4], headlineSmall = styles[5],
            titleLarge = styles[6], titleMedium = styles[7], titleSmall = styles[8],
            bodyLarge = styles[9], bodyMedium = styles[10], bodySmall = styles[11],
            labelLarge = styles[12], labelMedium = styles[13], labelSmall = styles[14],
        )
    }

    /** [corrections] as they're kept: `shift,ratio` per style, `-` for none, `;` between. */
    fun encode(corrections: List<Correction?>): String =
        corrections.joinToString(";") { c -> c?.let { "${it.shift},${it.topRatio}" } ?: "-" }

    /** What [encode] wrote, or null when it isn't [count] styles' worth. */
    fun decode(kept: String, count: Int = 15): List<Correction?>? {
        val parts = kept.split(";")
        if (parts.size != count) return null
        return parts.map { part ->
            if (part == "-") return@map null
            val (shift, ratio) = part.split(",").takeIf { it.size == 2 }?.map { it.toFloatOrNull() } ?: return null
            if (shift == null || ratio == null || ratio !in 0f..1f) return null
            Correction(shift, ratio)
        }
    }
}

/** The device's lines, laid out by Compose itself. */
private class MeasuredLines(private val measurer: TextMeasurer) : CjkCentering.LineLayout {
    override fun line(text: String, style: TextStyle, topRatio: Float, shift: Float): CjkCentering.Line {
        val result = with(CjkCentering) { measurer.measure(text, style.aligned(topRatio, shift)) }
        return CjkCentering.Line(result.firstBaseline, result.size.height.toFloat())
    }
}

/** The device's glyphs, through a paint set up as Compose sets up its own. */
private class PaintGlyphs(private val density: Density, private val locales: LocaleList) : CjkCentering.Glyphs {
    private val bounds = Rect()

    private fun paint(style: TextStyle): TextPaint {
        // Text's own default, where a style has no size.
        val size = with(density) { (if (style.fontSize.isSpecified) style.fontSize else 14.sp).toPx() }
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, (style.fontWeight ?: FontWeight.Normal).weight, false)
            textLocales = locales
        }
    }

    override fun ascent(style: TextStyle): Float = -paint(style).ascent()

    override fun inkMiddle(text: String, style: TextStyle): Float {
        paint(style).getTextBounds(text, 0, text.length, bounds)
        return (bounds.top + bounds.bottom) / 2f
    }
}

/**
 * The last measurement, kept across launches so a cold start draws centered
 * text from its first frame. Measured again when the app, the system (and so
 * its fonts), the density, the font scale or the languages change.
 */
private object KeptCorrections {
    private const val FILE = "cjk_centering"

    fun key(density: Density, locales: LocaleList) = listOf(
        BuildConfig.VERSION_CODE, Build.FINGERPRINT, density.density, density.fontScale, locales.toLanguageTags(),
    ).joinToString("|")

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(context: Context, key: String): List<CjkCentering.Correction?>? {
        val prefs = prefs(context)
        if (prefs.getString("key", null) != key) return null
        return prefs.getString("corrections", null)?.let(CjkCentering::decode)
    }

    fun write(context: Context, key: String, corrections: List<CjkCentering.Correction?>) {
        prefs(context).edit().putString("key", key).putString("corrections", CjkCentering.encode(corrections)).apply()
    }
}

/**
 * [base] with each style [CjkCentering.correction]-ed for this device, its
 * density and its languages. Measuring takes a while, so it runs off the main
 * thread, once per device and setting ([KeptCorrections]); until it's done,
 * the styles are as they were.
 */
@Composable
internal fun rememberCjkCenteredTypography(base: Typography): Typography {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val locales = LocalConfiguration.current.locales
    val fonts = LocalFontFamilyResolver.current
    val key = remember(density, locales) { KeptCorrections.key(density, locales) }
    val kept = remember(key) { KeptCorrections.read(context, key) }
    val corrections by produceState(kept, key, base) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.Default) {
            val measurer = TextMeasurer(fonts, density, LayoutDirection.Ltr, cacheSize = 0)
            CjkCentering.corrections(base, MeasuredLines(measurer), PaintGlyphs(density, locales))
        }.also { KeptCorrections.write(context, key, it) }
    }
    return remember(base, corrections) { corrections?.let { CjkCentering.applied(base, it) } ?: base }
}
