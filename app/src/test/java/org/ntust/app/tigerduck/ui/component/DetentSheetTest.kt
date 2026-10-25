// Drags, flings and taps a DetentSheet the way a phone reports a finger, and reads back where the
// sheet went and how it got there.

package org.ntust.app.tigerduck.ui.component

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one is a Hilt app these tests do not need. A phone's size and density.
@Config(application = Application::class, qualifiers = "en-w411dp-h891dp-420dpi")
class DetentSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var state: DetentSheetState
    private var dismissed = 0
    /** How far a finger goes before a drag starts, in px: Robolectric's is 16 dp, a phone's 8. */
    private var touchSlop = 0f
    private val density get() = composeRule.density.density

    private val height get() = state.draggable.anchors.positionOf(DetentSheetValue.Hidden)
    private val offset get() = state.draggable.offset

    private fun show(openAt: DetentSheetValue = DetentSheetValue.Half) {
        composeRule.setContent {
            touchSlop = LocalViewConfiguration.current.touchSlop
            state = rememberDetentSheetState()
            DetentSheetLayer(
                state = state,
                openAt = openAt,
                onDismissed = { dismissed++ },
                containerColor = Color.White,
            ) {
                Box(Modifier.fillMaxSize().testTag(CONTENT)) { Spacer(Modifier.fillMaxWidth().height(10.dp)) }
            }
        }
        composeRule.waitForIdle()
    }

    /** Steps the clock a frame at a time for [millis], noting the highest the sheet's top went. */
    private fun watch(millis: Long): Float {
        var highest = Float.MAX_VALUE
        repeat((millis / 16).toInt()) {
            composeRule.mainClock.advanceTimeByFrame()
            highest = minOf(highest, offset)
        }
        return highest
    }

    /** A finger's drag of [dy] px over [millis] ms, in 8 ms steps; a held finger before the lift drops the speed to nothing. */
    private fun TouchInjectionScope.drag(from: Offset, dy: Float, millis: Long, holdMillis: Long = 0) {
        val steps = (millis / 8).toInt().coerceAtLeast(1)
        down(from)
        for (i in 1..steps) moveTo(Offset(from.x, from.y + dy * i / steps), delayMillis = 8)
        if (holdMillis > 0) {
            repeat((holdMillis / 8).toInt()) { moveTo(Offset(from.x, from.y + dy), delayMillis = 8) }
        }
        up()
    }

    @Test
    fun `opens at half height`() {
        show()
        assertEquals(DetentSheetValue.Half, state.currentValue)
        assertEquals(height / 2f, offset, 1f)
    }

    @Test
    fun `opens in full when asked to`() {
        show(openAt = DetentSheetValue.Full)
        assertEquals(DetentSheetValue.Full, state.currentValue)
        assertEquals(0f, offset, 1f)
    }

    @Test
    fun `a short slow drag up from half height carries on to full screen`() {
        show()
        // The sheet moves 44 dp past the touch slop, then the finger holds still: no fling, only how
        // far it went. Over this sheet's 32 dp, under Material's 56.
        composeRule.onNodeWithTag(CONTENT).performTouchInput {
            drag(from = Offset(centerX, 200f), dy = -(touchSlop + 44f * density), millis = 500, holdMillis = 200)
        }
        composeRule.waitForIdle()
        assertEquals(DetentSheetValue.Full, state.currentValue)
    }

    @Test
    fun `a shorter drag than that settles back`() {
        show()
        // 24 dp past the touch slop, under this sheet's 32.
        composeRule.onNodeWithTag(CONTENT).performTouchInput {
            drag(from = Offset(centerX, 200f), dy = -(touchSlop + 24f * density), millis = 500, holdMillis = 200)
        }
        composeRule.waitForIdle()
        assertEquals(DetentSheetValue.Half, state.currentValue)
    }

    @Test
    fun `a gentle flick up from half height carries on to full screen`() {
        show()
        // 100 dp a second: under Material's 125 dp/s, over this sheet's 80. The sheet moves 24 dp
        // past the touch slop, too short to carry on by distance alone.
        val distance = touchSlop + 24f * density
        composeRule.onNodeWithTag(CONTENT).performTouchInput {
            drag(from = Offset(centerX, 200f), dy = -distance, millis = (distance / density * 10f).toLong())
        }
        composeRule.waitForIdle()
        assertEquals(DetentSheetValue.Full, state.currentValue)
    }

    @Test
    fun `it settles more slowly than Material's sheet`() {
        show()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(CONTENT).performTouchInput {
            drag(from = Offset(centerX, 400f), dy = -120f * density, millis = 120)
        }
        // Material's spring is all but there 180 ms after the lift; this one is still well on its way.
        watch(180)
        assertTrue("arrived as quickly as Material's: $offset", offset > 0.05f * height / 2f)
        watch(1000)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(DetentSheetValue.Full, state.currentValue)
    }

    @Test
    fun `a hard flick up from half height never carries the sheet past the top`() {
        show()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(CONTENT).performTouchInput {
            drag(from = Offset(centerX, 700f), dy = -600f, millis = 48)
        }
        val highest = watch(1200)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertTrue("went $highest px past the top", highest >= -0.5f)
        assertEquals(DetentSheetValue.Full, state.currentValue)
    }

    @Test
    fun `a hard flick up on the handle at full screen leaves the sheet where it is`() {
        show(openAt = DetentSheetValue.Full)
        composeRule.mainClock.autoAdvance = false
        // The strip at the top of the sheet, beside the handle: Material's own drag took these.
        composeRule.onRoot().performTouchInput {
            drag(from = Offset(40f, 30f * density), dy = -25f * density, millis = 24)
        }
        val highest = watch(800)
        assertTrue("went $highest px past the top", highest >= -0.5f)
        assertFalse("still animating, and would take the next touch", state.draggable.isAnimationRunning)
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun `a fling down at full screen with the content at its top goes to half height`() {
        show(openAt = DetentSheetValue.Full)
        composeRule.onNodeWithTag(CONTENT).performTouchInput {
            drag(from = Offset(centerX, 300f), dy = 300f, millis = 80)
        }
        composeRule.waitForIdle()
        assertEquals(DetentSheetValue.Half, state.currentValue)
        assertEquals(0, dismissed)
    }

    @Test
    fun `a fling down from half height closes the sheet`() {
        show()
        composeRule.onNodeWithTag(CONTENT).performTouchInput {
            drag(from = Offset(centerX, 100f), dy = 300f, millis = 80)
        }
        composeRule.waitForIdle()
        assertEquals(DetentSheetValue.Hidden, state.currentValue)
        assertEquals(1, dismissed)
    }

    @Test
    fun `a tap above the sheet closes it`() {
        show()
        composeRule.onRoot().performTouchInput { click(Offset(centerX, 100f)) }
        composeRule.waitForIdle()
        assertEquals(DetentSheetValue.Hidden, state.currentValue)
        assertEquals(1, dismissed)
    }

    @Test
    fun `back at full screen goes to half height, and back again closes the sheet`() {
        show(openAt = DetentSheetValue.Full)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(DetentSheetValue.Half, state.currentValue)
        assertEquals(0, dismissed)

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(DetentSheetValue.Hidden, state.currentValue)
        assertEquals(1, dismissed)
    }

    @Test
    fun `the handle raises a sheet at half height and closes one at full`() {
        show()
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).performClick()
        composeRule.waitForIdle()
        assertEquals(DetentSheetValue.Full, state.currentValue)

        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse)).performClick()
        composeRule.waitForIdle()
        assertEquals(DetentSheetValue.Hidden, state.currentValue)
        assertEquals(1, dismissed)
    }

    @Test
    fun `TalkBack can expand, collapse and dismiss from the handle`() {
        show()
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            .performSemanticsAction(SemanticsActions.Expand)
        composeRule.waitForIdle()
        assertEquals(DetentSheetValue.Full, state.currentValue)

        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
            .performSemanticsAction(SemanticsActions.Collapse)
        composeRule.waitForIdle()
        assertEquals(DetentSheetValue.Half, state.currentValue)

        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .performSemanticsAction(SemanticsActions.Dismiss)
        composeRule.waitForIdle()
        assertEquals(DetentSheetValue.Hidden, state.currentValue)
        assertEquals(1, dismissed)
    }

    private companion object {
        const val CONTENT = "sheet-content"
    }
}

class DetentSheetMotionTest {

    @Test
    fun `a fling toward the height is held to what the spring stops from without passing it`() {
        val distance = -500f
        val limit = kotlin.math.sqrt(DetentSheetMotion.STIFFNESS) * 500f
        assertEquals(-limit, DetentSheetMotion.startVelocity(-20_000f, distance), 0.01f)
    }

    @Test
    fun `a slow fling toward the height keeps its speed`() {
        assertEquals(-300f, DetentSheetMotion.startVelocity(-300f, -500f), 0f)
    }

    @Test
    fun `a fling away from the height keeps its speed`() {
        assertEquals(4_000f, DetentSheetMotion.startVelocity(4_000f, -500f), 0f)
    }

    @Test
    fun `no distance left, no speed added`() {
        assertEquals(-4_000f, DetentSheetMotion.startVelocity(-4_000f, 0f), 0f)
    }
}
