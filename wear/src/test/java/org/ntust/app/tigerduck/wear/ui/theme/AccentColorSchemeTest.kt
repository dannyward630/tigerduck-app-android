package org.ntust.app.tigerduck.wear.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The watch's Material colors, tinted with the phone's accent as the iPhone tints its watch. */
class AccentColorSchemeTest {

    private val accents = listOf(Color(0xFFFF6B35), Color(0xFFFFD60A), Color(0xFF1A237E), Color(0xFF007AFF), Color.White, Color.Black)

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test
    fun `an accent that reads on black is kept as it is`() {
        assertEquals(Color(0xFFFF6B35), readableOnBlack(Color(0xFFFF6B35)))
        assertEquals(Color(0xFFFFD60A), readableOnBlack(Color(0xFFFFD60A)))
    }

    @Test
    fun `a dark accent is lifted until it reads on black, keeping its hue`() {
        // The iPhone's own Indigo preset: 3.7:1 on black as it is.
        val indigo = Color(0xFF5856D6)
        val lifted = readableOnBlack(indigo)
        assertTrue(contrast(lifted, Color.Black) >= 4.5f)
        assertTrue(lifted.blue > lifted.red && lifted.blue > lifted.green)
        assertTrue(contrast(readableOnBlack(Color.Black), Color.Black) >= 4.5f)
    }

    @Test
    fun `the primary color is the accent made readable on black`() {
        assertTrue(contrast(accentColorScheme(Color(0xFF5856D6)).primary, Color.Black) >= 4.5f)
    }

    @Test
    fun `the accent is the primary color`() {
        assertEquals(Color(0xFFFF6B35), accentColorScheme(Color(0xFFFF6B35)).primary)
    }

    @Test
    fun `text on the accent reads, light accent or dark`() {
        for (accent in accents) {
            val scheme = accentColorScheme(accent)
            assertTrue("on $accent", contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
        }
        assertTrue(accentColorScheme(Color(0xFFFFD60A)).onPrimary.luminance() < 0.2f)
    }

    @Test
    fun `the accent's container is a dark tone of it, with text that reads`() {
        for (accent in accents) {
            val scheme = accentColorScheme(accent)
            assertTrue("container of $accent", scheme.primaryContainer.luminance() < 0.2f)
            assertTrue("on the container of $accent", contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
        }
    }
}
