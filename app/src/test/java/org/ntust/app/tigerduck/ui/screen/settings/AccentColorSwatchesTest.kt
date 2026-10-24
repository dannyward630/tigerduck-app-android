package org.ntust.app.tigerduck.ui.screen.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What TalkBack says for the accent color swatches in Settings, and how they lay out. */
@RunWith(RobolectricTestRunner::class)
// A window wider than any card these tests lay out, so none is cut by the window.
@Config(application = Application::class, qualifiers = "w480dp-h800dp")
class AccentColorSwatchesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var picked: Int? = null
    private var wallpaperPicks = 0

    private val wallpaper: String get() = composeRule.activity.getString(R.string.color_name_wallpaper)

    private fun show(
        selectedHex: Int,
        followsWallpaper: Boolean = false,
        isDark: Boolean = false,
        width: Dp = 400.dp,
    ) {
        composeRule.setContent {
            Box(Modifier.requiredWidth(width).testTag(CARD)) {
                AccentColorSwatches(
                    selectedHex = selectedHex,
                    followsWallpaper = followsWallpaper,
                    isDark = isDark,
                    onSelect = { picked = it },
                    onSelectWallpaper = { wallpaperPicks++ },
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** Each swatch's line, by the swatches on it. */
    private fun swatchLines(): List<Int> = composeRule
        .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        .fetchSemanticsNodes()
        .groupBy { it.boundsInRoot.top }
        .toSortedMap()
        .values
        .map { it.size }

    private fun assertEverySwatchWholeInsideTheCard() {
        val card = composeRule.onNodeWithTag(CARD).fetchSemanticsNode().boundsInRoot
        val side = with(composeRule.density) { 32.dp.toPx() }
        val swatches = composeRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .fetchSemanticsNodes()
        assertEquals(9, swatches.size)
        swatches.forEach {
            val name = it.config[SemanticsProperties.ContentDescription].joinToString()
            val bounds = it.boundsInRoot
            assertEquals("$name is cut", side, bounds.width, 0.5f)
            assertEquals("$name is cut", side, bounds.height, 0.5f)
            assertTrue("$name is outside the card", bounds.left >= card.left - 0.5f && bounds.right <= card.right + 0.5f)
        }
    }

    // The default SDK, 33, shows the wallpaper's swatch first in the row; the
    // colors after it keep their names and their order.
    @Test
    fun `each swatch is named by its color and the chosen one is selected`() {
        show(selectedHex = 0x007AFF)

        composeRule.onNodeWithContentDescription("Blue").assertIsSelected()
        composeRule.onNodeWithContentDescription("Purple").assertIsNotSelected()
        composeRule.onNodeWithContentDescription("Indigo").assertIsNotSelected()
        composeRule.onNodeWithContentDescription(wallpaper).assertIsNotSelected()
    }

    @Test
    fun `choosing a swatch in dark mode stores its light color`() {
        show(selectedHex = 0x007AFF, isDark = true)

        composeRule.onNodeWithContentDescription("Cyan").performClick()

        assertEquals(0x5AC8FA, picked)
    }

    // A 360dp phone's card: the screen less the card's margins and its padding.
    @Test
    fun `every swatch is whole and inside a narrow phone's card`() {
        show(selectedHex = 0x007AFF, width = 296.dp)

        assertEverySwatchWholeInsideTheCard()
    }

    // A 411dp phone's card fits eight swatches to a line: the ninth goes
    // under them with the line before it, not on a line of its own.
    @Test
    fun `swatches that do not fit on one line wrap into even lines`() {
        show(selectedHex = 0x007AFF, width = 347.dp)

        assertEverySwatchWholeInsideTheCard()
        assertEquals(listOf(5, 4), swatchLines())
    }

    // 368dp is nine swatches to the dp, but at 2.75x the row rounds each gap
    // up to 28px, and the ninth no longer fits beside the other eight.
    @Config(qualifiers = "w480dp-h800dp-440dpi")
    @Test
    fun `lines are counted in the pixels the row lays them out in`() {
        show(selectedHex = 0x007AFF, width = 368.dp)

        assertEverySwatchWholeInsideTheCard()
        assertEquals(listOf(5, 4), swatchLines())
    }

    @Test
    fun `swatches that fit on one line stay on one line`() {
        show(selectedHex = 0x007AFF, width = 400.dp)

        assertEverySwatchWholeInsideTheCard()
        assertEquals(listOf(9), swatchLines())
    }

    @Config(sdk = [30])
    @Test
    fun `wallpaper swatch is hidden on Android 11 and earlier`() {
        show(selectedHex = 0x007AFF)

        composeRule.onNodeWithContentDescription(wallpaper).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Blue").assertIsSelected()
    }

    @Config(sdk = [31])
    @Test
    fun `while the wallpaper is picked its swatch is the one selected, not the swatch it keeps`() {
        show(selectedHex = 0x007AFF, followsWallpaper = true)

        composeRule.onNodeWithContentDescription(wallpaper).assertIsSelected()
        composeRule.onNodeWithContentDescription("Blue").assertIsNotSelected()
    }

    @Config(sdk = [31])
    @Test
    fun `choosing the wallpaper leaves the swatch's color alone`() {
        show(selectedHex = 0xFF2D55)

        composeRule.onNodeWithContentDescription(wallpaper).performClick()

        assertEquals(1, wallpaperPicks)
        assertNull(picked)
    }

    private companion object {
        const val CARD = "card"
    }
}
