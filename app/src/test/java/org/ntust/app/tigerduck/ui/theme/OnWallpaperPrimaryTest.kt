// Draws what sits on the accent under the wallpaper's dark colors and reads
// the pixels back: that primary is a light tone, so a mark drawn on it in
// white all but vanishes, and each takes the wallpaper's paired onPrimary.

package org.ntust.app.tigerduck.ui.theme

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.R
import org.ntust.app.tigerduck.data.model.CalendarEvent
import org.ntust.app.tigerduck.data.model.EventSource
import org.ntust.app.tigerduck.ui.screen.calendar.CalendarGrid
import org.ntust.app.tigerduck.ui.screen.settings.AccentColorSwatches
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one is a Hilt app these tests do not need.
@Config(application = Application::class, sdk = [31])
// Real drawing, so the captured image holds actual pixels.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnWallpaperPrimaryTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun resetTheme() {
        TigerDuckTheme.setDarkMode(false)
    }

    private fun Color.near(other: Color) =
        abs(red - other.red) < 0.06f && abs(green - other.green) < 0.06f && abs(blue - other.blue) < 0.06f

    private fun PixelMap.count(color: Color): Int {
        var n = 0
        for (y in 0 until height) for (x in 0 until width) if (this[x, y].near(color)) n++
        return n
    }

    private val wallpaper get() = dynamicDarkColorScheme(composeRule.activity)

    @Test
    fun `the selected day's number and event dots are drawn in onPrimary, not white`() {
        val taipei = ZoneId.of("Asia/Taipei")
        val day = LocalDate.of(2026, 10, 14)
        fun LocalDate.toDate(): Date = Date.from(atStartOfDay(taipei).toInstant())
        val events = listOf(
            CalendarEvent("a", "Lab report", Date.from(day.atTime(23, 59).atZone(taipei).toInstant()), EventSource.MOODLE.raw),
            CalendarEvent("b", "Career fair", Date.from(day.atTime(9, 30).atZone(taipei).toInstant()), EventSource.SCHOOL.raw),
        )
        TigerDuckTheme.setDarkMode(true)
        composeRule.setContent {
            TigerDuckAppTheme(darkTheme = true, useDynamicColor = true) {
                Box(Modifier.background(Color.Black)) {
                    CalendarGrid(
                        monthDate = LocalDate.of(2026, 10, 1).toDate(),
                        selectedDate = day.toDate(),
                        eventsByDay = mapOf(day to events),
                        onDateSelected = {},
                    )
                }
            }
        }

        val cell = composeRule.onNodeWithContentDescription("Wednesday, October 14, 2026, 2 events")
            .captureToImage().toPixelMap()
        assertTrue("the cell is filled with the wallpaper's primary", cell.count(wallpaper.primary) > 0)
        assertEquals("nothing on the cell is white", 0, cell.count(Color.White))
        assertTrue("its number and dots are onPrimary", cell.count(wallpaper.onPrimary) > 0)
    }

    @Test
    fun `the wallpaper swatch's check is drawn in onPrimary, not white`() {
        composeRule.setContent {
            // On black, so the corners around the round swatch are not white.
            Box(Modifier.background(Color.Black)) {
                AccentColorSwatches(
                    selectedHex = 0x007AFF,
                    followsWallpaper = true,
                    isDark = true,
                    onSelect = {},
                    onSelectWallpaper = {},
                )
            }
        }

        val swatch = composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.color_name_wallpaper))
            .captureToImage().toPixelMap()
        assertEquals("nothing on the swatch is white", 0, swatch.count(Color.White))
        assertTrue("its check is onPrimary", swatch.count(wallpaper.onPrimary) > 0)
    }
}
