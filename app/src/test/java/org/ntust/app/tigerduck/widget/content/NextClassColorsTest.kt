package org.ntust.app.tigerduck.widget.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.ntust.app.tigerduck.shared.Course
import org.ntust.app.tigerduck.ui.theme.VisualPreset
import org.ntust.app.tigerduck.ui.theme.VisualStylePolicy
import org.ntust.app.tigerduck.ui.theme.courseColorPalette
import org.ntust.app.tigerduck.ui.theme.pastel
import org.ntust.app.tigerduck.ui.theme.pastelOutline
import org.ntust.app.tigerduck.widget.WidgetTheme

/**
 * The Next Class widget under TAT is its course's card, as Home's time machine card is: the
 * class table's pastel with dark text in either theme, the badge and the progress in the course's
 * own darker line. TigerDuck, and a widget with no course to show, keep the theme's colors.
 */
class NextClassColorsTest {

    private val tigerDuck = VisualStylePolicy(VisualPreset.TigerDuck)
    private val tat = VisualStylePolicy(VisualPreset.Tat)

    private val calculus = Course(courseNo = "CS1001", courseName = "Calculus")
    private val light = courseColorPalette[2]
    private val courseColors = mapOf(calculus.courseNo to light)

    @Test
    fun `TAT draws the widget in its course's pastel in either theme, in dark text`() {
        listOf(WidgetTheme.Light, WidgetTheme.Dark).forEach { theme ->
            val drawn = nextClassColors(calculus, theme, tat, courseColors)

            assertEquals(light.pastel(), drawn.background)
            assertEquals(VisualStylePolicy.TextOnPastel, drawn.onSurface)
            assertEquals(VisualStylePolicy.TextOnPastelSecondary, drawn.onSurfaceVariant)
            assertEquals(light.pastelOutline(), drawn.highlight)
            assertFalse(drawn.isDark)
        }
    }

    @Test
    fun `TigerDuck keeps the theme's colors`() {
        listOf(WidgetTheme.Light, WidgetTheme.Dark).forEach { theme ->
            assertEquals(theme, nextClassColors(calculus, theme, tigerDuck, courseColors))
        }
    }

    @Test
    fun `with no course to show the widget keeps the theme's colors`() {
        assertEquals(WidgetTheme.Dark, nextClassColors(null, WidgetTheme.Dark, tat, courseColors))
    }
}
