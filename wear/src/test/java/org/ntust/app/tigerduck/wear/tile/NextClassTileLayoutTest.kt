package org.ntust.app.tigerduck.wear.tile

import androidx.wear.protolayout.LayoutElementBuilders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import androidx.compose.ui.graphics.luminance
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The tile's lines: the status line in the phone's accent, as the app's titles are; the rest as the face shows text. */
@RunWith(RobolectricTestRunner::class)
class NextClassTileLayoutTest {

    @Test
    fun `the status line is drawn in the accent`() {
        val line = tileTextLine("Starts 12:20", accentArgb = 0xFFFF6B35.toInt()) as LayoutElementBuilders.Text
        assertEquals(0xFFFF6B35.toInt(), line.fontStyle?.color?.argb)
    }

    @Test
    fun `the other lines keep the default color`() {
        val line = tileTextLine("Calculus", accentArgb = null) as LayoutElementBuilders.Text
        assertNull(line.fontStyle?.color)
    }

    @Test
    fun `an accent the watch can't read falls back to the default blue`() {
        assertEquals(0xFF007AFF.toInt(), tileAccentArgb("not a color"))
        assertEquals(0xFFFF6B35.toInt(), tileAccentArgb("#FF6B35"))
    }

    @Test
    fun `a dark accent is lifted until the status line reads on black`() {
        val argb = tileAccentArgb("#5856D6")
        val lifted = androidx.compose.ui.graphics.Color(argb)
        assertTrue(androidx.compose.ui.graphics.Color(0xFF5856D6) != lifted)
        assertTrue((lifted.luminance() + 0.05f) / 0.05f >= 4.5f)
    }
}
