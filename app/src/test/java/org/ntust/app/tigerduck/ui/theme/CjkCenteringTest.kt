package org.ntust.app.tigerduck.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ceil

/**
 * [CjkCentering] against a model of how Android lays a line out, at bodyLarge's
 * size on a 2.625 density phone: an 18sp font in a 24sp (63 px) line, Latin
 * from a font 44 px up and 12 down, CJK from one 55 up and 14 down whose ink
 * sits 2.5 px below the middle of that.
 */
class CjkCenteringTest {

    private val style = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

    /**
     * A device. [cjkKeepsItsHeight] is recent Android, where a line is never
     * shorter than its fonts, so one with CJK in it is as tall as the CJK font
     * whatever the style asks; otherwise the style's line height trims every
     * line.
     */
    private class Device(
        val cjkKeepsItsHeight: Boolean,
        val lineHeight: Int = 63,
        val cjkAscent: Float = 55f,
        val cjkDescent: Float = 14f,
        val cjkInkBelowMiddle: Float = 2.5f,
    ) : CjkCentering.LineLayout, CjkCentering.Glyphs {
        val latinAscent = 44f
        val latinDescent = 12f

        override fun ascent(style: TextStyle) = latinAscent

        override fun inkMiddle(text: String, style: TextStyle) =
            if (text == CjkCentering.CJK_SAMPLE) -(cjkAscent - cjkDescent) / 2f + cjkInkBelowMiddle else -15f

        /** The px a shift moves glyphs by: negative is up. */
        fun shiftPx(shift: Float) = ceil(-latinAscent * shift)

        override fun line(text: String, style: TextStyle, topRatio: Float, shift: Float): CjkCentering.Line {
            val cjk = text == CjkCentering.CJK_SAMPLE
            // A shift up makes room above the primary font, as Android's measure does.
            val primaryAscent = latinAscent - shiftPx(shift)
            val ascent = if (cjk) maxOf(primaryAscent, cjkAscent) else primaryAscent
            val descent = if (cjk) maxOf(latinDescent, cjkDescent) else latinDescent
            if (cjkKeepsItsHeight && ascent + descent >= lineHeight) {
                return CjkCentering.Line(baseline = ascent, height = ascent + descent)
            }
            // Compose's LineHeightStyleSpan: the line made `lineHeight` tall, the difference
            // shared out by the top ratio.
            val ratio = topRatio
            val diff = lineHeight - (ascent + descent).toInt()
            val descentDiff = if (diff <= 0) ceil(diff * ratio) else ceil(diff * (1f - ratio))
            val newDescent = descent + descentDiff
            return CjkCentering.Line(baseline = lineHeight - newDescent, height = lineHeight.toFloat())
        }

        fun cjkLow(c: CjkCentering.Correction?) =
            CjkCentering.cjkOffset(TextStyle(), c?.topRatio ?: 0.5f, c?.shift ?: 0f, this, this)

        /** Where a Latin line's glyphs sit from its middle, where a row centers it: its baseline plus the shift. */
        fun latinGlyphs(c: CjkCentering.Correction?) =
            line(CjkCentering.LATIN_SAMPLE, TextStyle(), c?.topRatio ?: 0.5f, c?.shift ?: 0f).fromMiddle + shiftPx(c?.shift ?: 0f)

        fun correction(style: TextStyle) = CjkCentering.correction(style, this, this)
    }

    @Test
    fun `on recent android, cjk ink comes up to the middle of its line`() {
        val device = Device(cjkKeepsItsHeight = true)
        assertEquals(2.5f, device.cjkLow(null), 0.01f)

        val c = device.correction(style)

        assertTrue("still ${device.cjkLow(c)} px low", abs(device.cjkLow(c)) <= 0.5f)
    }

    @Test
    fun `and a latin line stays where it was`() {
        val device = Device(cjkKeepsItsHeight = true)

        val c = device.correction(style)

        assertEquals(device.latinGlyphs(null), device.latinGlyphs(c), 0.5f)
    }

    @Test
    fun `where the line height trims cjk lines too, cjk ink comes no lower than it was`() {
        val device = Device(cjkKeepsItsHeight = false)

        val c = device.correction(style)

        assertTrue(abs(device.cjkLow(c)) <= abs(device.cjkLow(null)))
    }

    @Test
    fun `a cjk font already in the middle leaves the style as it is`() {
        val device = Device(cjkKeepsItsHeight = true, cjkInkBelowMiddle = 0.2f)

        assertEquals(style, CjkCentering.centered(style, device, device))
    }

    @Test
    fun `a font that sits further down is raised further`() {
        val device = Device(cjkKeepsItsHeight = true, cjkInkBelowMiddle = 5f)

        val c = device.correction(style)

        assertTrue(abs(device.cjkLow(c)) <= 0.5f)
        assertEquals(device.latinGlyphs(null), device.latinGlyphs(c), 1f)
    }

    @Test
    fun `a line with no room to put latin back splits the difference`() {
        // bodySmall's: a line no taller than the Latin font, which a shift then makes
        // taller, so Latin rises half as far as CJK. Raising CJK the whole 3 px would
        // move Latin 1.5; 2 px leaves each 1 px off.
        val device = Device(cjkKeepsItsHeight = true, lineHeight = 56, cjkInkBelowMiddle = 3f)

        val c = device.correction(style)!!

        val cjkLow = abs(device.cjkLow(c))
        val latinMoved = abs(device.latinGlyphs(c) - device.latinGlyphs(null))
        assertTrue("CJK $cjkLow px off, Latin moved $latinMoved px", maxOf(cjkLow, latinMoved) <= 1f)
    }

    @Test
    fun `every style of the typography is corrected, in its own place`() {
        val device = Device(cjkKeepsItsHeight = true)
        val other = style.copy(fontSize = 16.sp, lineHeight = 20.sp)
        val base = Typography(bodyLarge = style, labelLarge = other, titleSmall = style)

        val corrections = CjkCentering.corrections(base, device, device)
        val applied = CjkCentering.applied(base, corrections)

        assertEquals(15, corrections.size)
        assertEquals(with(CjkCentering) { style.aligned(device.correction(style)!!.topRatio, device.correction(style)!!.shift) }, applied.bodyLarge)
        assertEquals(CjkCentering.centered(other, device, device), applied.labelLarge)
        assertNotEquals(style, applied.titleSmall)
    }

    @Test
    fun `corrections are kept and read back as they were`() {
        val corrections = listOf(CjkCentering.Correction(0.068f, 0.75f), null) + List(13) { CjkCentering.Correction(0.1f, 0.5f) }

        assertEquals(corrections, CjkCentering.decode(CjkCentering.encode(corrections)))
    }

    @Test
    fun `kept corrections that don't read back whole are measured again`() {
        assertNull(CjkCentering.decode(""))
        assertNull(CjkCentering.decode("0.1,0.5;-"))
        assertNull(CjkCentering.decode(List(15) { "0.1,2.0" }.joinToString(";")))
        assertNull(CjkCentering.decode(List(15) { "x,0.5" }.joinToString(";")))
    }
}
