package org.ntust.app.tigerduck.widget.content

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ntust.app.tigerduck.shared.Course
import org.ntust.app.tigerduck.ui.theme.VisualPreset
import org.ntust.app.tigerduck.ui.theme.VisualStylePolicy
import org.ntust.app.tigerduck.ui.theme.courseColorPalette
import org.ntust.app.tigerduck.ui.theme.courseColorPaletteDark
import org.ntust.app.tigerduck.ui.theme.pastel
import org.ntust.app.tigerduck.widget.WidgetTheme

/**
 * Covers [todayRowFill] and its text colors: a class that is on takes its
 * course's own color with white text under TigerDuck, and the class table's
 * pastel with dark text under TAT, while a row not on keeps the surface.
 */
class TodayRowColorsTest {

    private val tigerDuck = VisualStylePolicy(VisualPreset.TigerDuck)
    private val tat = VisualStylePolicy(VisualPreset.Tat)

    private val calculus = Course(courseNo = "CS1001", courseName = "Calculus")
    private val light = courseColorPalette[2]
    private val dark = courseColorPaletteDark[2]
    private val courseColors = mapOf(calculus.courseNo to light)

    private val lightTheme = WidgetTheme.Light
    private val darkTheme = WidgetTheme.Dark

    @Test
    fun `TigerDuck fills a class that is on with its color for the theme, in white`() {
        assertEquals(light, todayRowFill(calculus, true, lightTheme, tigerDuck, courseColors))
        assertEquals(dark, todayRowFill(calculus, true, darkTheme, tigerDuck, courseColors))
        assertEquals(Color.White, todayRowTextColor(true, darkTheme, tigerDuck))
        assertEquals(Color.White.copy(alpha = 0.85f), todayRowSecondaryTextColor(true, darkTheme, tigerDuck))
    }

    @Test
    fun `TAT fills a class that is on with the light color's pastel in either theme, in dark text`() {
        assertEquals(light.pastel(), todayRowFill(calculus, true, lightTheme, tat, courseColors))
        assertEquals(light.pastel(), todayRowFill(calculus, true, darkTheme, tat, courseColors))
        listOf(lightTheme, darkTheme).forEach { theme ->
            assertEquals(VisualStylePolicy.TextOnPastel, todayRowTextColor(true, theme, tat))
            assertEquals(VisualStylePolicy.TextOnPastelSecondary, todayRowSecondaryTextColor(true, theme, tat))
        }
    }

    @Test
    fun `a row not on keeps the surface and the theme's text under either style`() {
        listOf(tigerDuck, tat).forEach { style ->
            listOf(lightTheme, darkTheme).forEach { theme ->
                assertEquals(theme.surface, todayRowFill(calculus, false, theme, style, courseColors))
                assertEquals(theme.onSurface, todayRowTextColor(false, theme, style))
                assertEquals(theme.onSurfaceVariant, todayRowSecondaryTextColor(false, theme, style))
            }
        }
    }
}
