// Lays a course's color picker out with Custom open on screens too short for all of it, and
// reads back what each control was given, so a sheet that squeezes its last controls fails here
// rather than on a short phone or one with its text size turned up.

package org.ntust.app.tigerduck.ui.component

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.ui.theme.TigerDuckAppTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val Code = "#1ABC9C"

@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one is a Hilt app these tests do not need.
@Config(application = Application::class, qualifiers = "en-w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorPickerSheetLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `on a short phone the custom picker shows its code whole and keeps both buttons whole`() {
        showCustomPicker()

        assertWhole("Cancel")
        assertWhole("Apply")
        assertCodeWhole()
    }

    @Test
    @Config(qualifiers = "en-w393dp-h851dp", fontScale = 1.5f)
    fun `at the largest text size the custom picker shows its code whole and keeps both buttons whole`() {
        showCustomPicker()
        assertEquals("the text is not at the largest size", 1.5f, text(Code).textLayout().layoutInput.density.fontScale)

        assertWhole("Cancel")
        assertWhole("Apply")
        assertCodeWhole()
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp", fontScale = 2f)
    fun `on a short phone at the app's largest text size the custom picker shows its code whole and keeps both buttons whole`() {
        showCustomPicker()

        assertWhole("Cancel")
        assertWhole("Apply")
        assertCodeWhole()
    }

    @Test
    fun `on a short phone the picker scrolls, with the buttons below what scrolls`() {
        showCustomPicker()

        val scroll = scrollContainer()
        val range = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("there is nothing to scroll", range.maxValue() > 0f)
        assertTrue("the buttons scroll with the controls", button("Cancel").boundsInWindow.top >= scroll.boundsInWindow.bottom)

        // Opening Custom scrolled down to it; a swipe brings the title back, and the buttons stay put.
        val cancelTop = button("Cancel").boundsInWindow.top
        val titleTop = text("Pick color").boundsInWindow.top
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performTouchInput { swipeDown(startY = centerY, endY = centerY + height / 4f) }
        composeRule.waitForIdle()
        assertTrue("a swipe did not scroll the controls", text("Pick color").boundsInWindow.top > titleTop)
        assertEquals("the buttons moved with the controls", cancelTop, button("Cancel").boundsInWindow.top, 0.5f)
    }

    @Test
    @Config(qualifiers = "en-w411dp-h1200dp")
    fun `where everything fits, nothing scrolls and the code sits above the buttons as before`() {
        showCustomPicker()

        val gap = (button("Cancel").boundsInWindow.top - text(Code).boundsInWindow.bottom) /
            RuntimeEnvironment.getApplication().resources.displayMetrics.density
        // The sheet's 22dp, plus the 4dp a 40dp button leaves of its 48dp touch target.
        assertEquals("the gap above the buttons changed", 26f, gap, 0.5f)
        scrollContainerOrNull()?.let {
            assertEquals("a picker that fits scrolls", 0f, it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue(), 0f)
        }
    }

    private fun showCustomPicker() {
        composeRule.setContent {
            TigerDuckAppTheme(darkTheme = false) {
                ColorPickerSheet(
                    courseName = "Operating Systems",
                    currentColor = Color(0xFF1ABC9C),
                    presetPalette = List(20) { Color(0xFF000000 + it * 0x0A0A0A) },
                    usedByOthers = emptySet(),
                    onApply = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Custom").performClick()
        composeRule.waitForIdle()
    }

    /** The button laid out at its full height and shown all of it. */
    private fun assertWhole(label: String) {
        val node = button(label)
        val minHeight = 40f * RuntimeEnvironment.getApplication().resources.displayMetrics.density
        assertTrue("$label was squeezed to ${node.size.height}px", node.size.height >= minHeight - 0.5f)
        assertEquals("$label is cut off", node.size.height.toFloat(), node.boundsInWindow.height, 0.5f)
    }

    /** The color code laid out at its full height, and shown all of it once Custom opens. */
    private fun assertCodeWhole() {
        val node = text(Code)
        assertFalse("the code was squeezed to ${node.size.height}px", node.textLayout().didOverflowHeight)
        assertEquals("the code is cut off", node.size.height.toFloat(), node.boundsInWindow.height, 0.5f)
        assertTrue("the code sits under the buttons", node.boundsInWindow.bottom <= button("Cancel").boundsInWindow.top)
    }

    private fun button(label: String): SemanticsNode =
        composeRule.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode()

    private fun text(text: String): SemanticsNode = composeRule
        .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .single { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() == text }

    private fun scrollContainerOrNull(): SemanticsNode? = composeRule
        .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        .fetchSemanticsNodes()
        .singleOrNull()

    private fun scrollContainer(): SemanticsNode =
        checkNotNull(scrollContainerOrNull()) { "nothing in the picker scrolls" }

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }
}
