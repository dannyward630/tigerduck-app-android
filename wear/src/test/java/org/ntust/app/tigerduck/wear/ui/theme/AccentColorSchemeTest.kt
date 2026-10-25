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
        assertEquals(Color.White, accentColorScheme(Color(0xFF1A237E)).onPrimary)
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
