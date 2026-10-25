package org.ntust.app.tigerduck.wear.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.shared.AccentGradientDirection
import org.ntust.app.tigerduck.wear.ui.theme.WearAccentGradient
import org.ntust.app.tigerduck.wear.ui.theme.accentTitleBrush
import org.ntust.app.tigerduck.wear.ui.theme.readableOnBlack
import androidx.compose.ui.graphics.luminance
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The watch's marks in the accent, drawn for real: the library QR's countdown
 * ring, the screen padding ring and the QR padding corners. With a gradient
 * picked on the phone, each runs from its start to its end across the mark.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccentMarksDrawingTest {

    private val red = Color(0xFFFF0000)
    private val blue = Color(0xFF0000FF)

    private fun render(width: Int, height: Int, draw: DrawScope.() -> Unit): Bitmap {
        val image = ImageBitmap(width, height)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat()), draw,
        )
        return image.asAndroidBitmap()
    }

    /** How far the pixel at [x], [y] is from red toward blue: 0 red, 1 blue. */
    private fun Bitmap.towardBlue(x: Int, y: Int): Float {
        val pixel = Color(getPixel(x, y))
        assertTrue("($x, $y) is drawn", pixel.alpha > 0.1f)
        return pixel.blue / (pixel.red + pixel.blue)
    }

    private fun Bitmap.alpha(x: Int, y: Int): Float = Color(getPixel(x, y)).alpha

    @Test
    fun `a title's brush runs from the start on its left to the end on its right, whatever its size`() {
        // A title doesn't know its size until it's laid out: the brush must lay the gradient across it then.
        val title = render(200, 30) {
            drawRect(accentTitleBrush(red, WearAccentGradient(blue, AccentGradientDirection.LeftToRight)))
        }
        assertTrue(title.towardBlue(2, 15) < 0.1f)
        assertTrue(title.towardBlue(197, 15) > 0.9f)
    }

    @Test
    fun `a title's gradient made readable reads on black all the way across, not only at its ends`() {
        // Both ends read on black, but mixed in sRGB the middle drops to 3.7:1.
        val blue = Color(0xFF007AFF)
        val pink = Color(0xFFFF2D55)
        val title = render(200, 30) {
            drawRect(accentTitleBrush(blue, WearAccentGradient(pink, AccentGradientDirection.LeftToRight), ::readableOnBlack))
        }
        for (x in 2 until 198 step 7) {
            val pixel = Color(title.getPixel(x, 15))
            assertTrue("($x) reads on black: ${(pixel.luminance() + 0.05f) / 0.05f}", (pixel.luminance() + 0.05f) / 0.05f >= 4.4f)
        }
    }

    @Test
    fun `with a single color a title's brush is that color`() {
        val title = render(200, 30) { drawRect(accentTitleBrush(red, null)) }
        assertEquals(0f, title.towardBlue(2, 15), 0.01f)
        assertEquals(0f, title.towardBlue(197, 15), 0.01f)
    }

    @Test
    fun `the countdown ring runs from the start on its left to the end on its right`() {
        val ring = render(100, 100) {
            drawCountdownRing(1f, red, WearAccentGradient(blue, AccentGradientDirection.LeftToRight), strokePx = 10f)
        }
        assertTrue(ring.towardBlue(2, 50) < 0.15f)
        assertTrue(ring.towardBlue(97, 50) > 0.85f)
    }

    @Test
    fun `with a single color the countdown ring is that color all round`() {
        val ring = render(100, 100) { drawCountdownRing(1f, red, null, strokePx = 10f) }
        assertEquals(0f, ring.towardBlue(2, 50), 0.01f)
        assertEquals(0f, ring.towardBlue(97, 50), 0.01f)
    }

    // A quarter left: the arc from the top round to the right, over a faint
    // track of the gradient the rest of the way.
    @Test
    fun `a quarter of the countdown is drawn solid over a faint track`() {
        val ring = render(100, 100) {
            drawCountdownRing(0.25f, red, WearAccentGradient(blue, AccentGradientDirection.LeftToRight), strokePx = 10f)
        }
        // Up and to the right, on the arc.
        assertEquals(1f, ring.alpha(84, 16), 0.05f)
        assertTrue(ring.towardBlue(84, 16) > 0.7f)
        // Left and bottom, on the track alone.
        assertEquals(0.18f, ring.alpha(2, 50), 0.05f)
        assertTrue(ring.towardBlue(2, 50) < 0.15f)
        assertEquals(0.18f, ring.alpha(50, 97), 0.05f)
    }

    // Across the ring, not the whole screen, which is wider than the ring here.
    @Test
    fun `the padding ring runs from the start on its left to the end on its right`() {
        val ring = render(300, 200) {
            drawPaddingEdgeRing(
                paddingPx = 20f, strokePx = 4f, accent = red,
                gradient = WearAccentGradient(blue, AccentGradientDirection.LeftToRight),
            )
        }
        // A ring of radius 80 round the middle, 150, 100.
        assertTrue(ring.towardBlue(70, 100) < 0.1f)
        assertTrue(ring.towardBlue(230, 100) > 0.9f)
        assertEquals(0.6f, ring.alpha(70, 100), 0.05f)
    }

    // Across the QR square the corners mark, not the whole screen: the
    // corners are then as far apart in color as the gradient goes.
    @Test
    fun `the QR corners run from the start at the top left to the end at the bottom right`() {
        val corners = render(300, 200) {
            drawQrCornerBrackets(
                sidePx = 100f, legPx = 20f, strokePx = 4f, accent = red,
                gradient = WearAccentGradient(blue, AccentGradientDirection.TopLeftToBottomRight),
            )
        }
        // The square spans 100 to 200 across and 50 to 150 down; each
        // corner's top or bottom leg.
        assertTrue(corners.towardBlue(104, 50) < 0.1f)
        assertTrue(corners.towardBlue(196, 150) > 0.9f)
        assertEquals(0.85f, corners.alpha(104, 50), 0.05f)
    }
}
