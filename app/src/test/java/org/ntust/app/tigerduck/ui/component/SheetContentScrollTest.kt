// Drags and flings the content of a Material3 bottom sheet, as the class table's course sheet holds
// it, and reads back where the content and the sheet ended up.

package org.ntust.app.tigerduck.ui.component

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one is a Hilt app these tests do not need.
@Config(application = Application::class, qualifiers = "en-w360dp-h640dp")
class SheetContentScrollTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var sheet: SheetState
    private lateinit var scroll: ScrollState
    private var dismissed = false
    private lateinit var scope: CoroutineScope

    /** A course sheet pulled up to full, its content about one and a half screens tall. */
    private fun showExpandedSheet() {
        composeRule.setContent {
            sheet = rememberModalBottomSheetState()
            scope = rememberCoroutineScope()
            scroll = rememberScrollState()
            val sheetScroll = remember(scroll) { SheetContentScroll(scroll) }
            ModalBottomSheet(onDismissRequest = { dismissed = true }, sheetState = sheet) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag(CONTENT)
                        .then(sheetScroll.modifier)
                        .verticalScroll(scroll),
                ) {
                    Column { Spacer(Modifier.fillMaxWidth().height(900.dp)) }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { scope.launch { sheet.expand() } }
        composeRule.waitForIdle()
        assertEquals("the sheet did not open in full", SheetValue.Expanded, sheet.currentValue)
        assertTrue("the content is too short to scroll", scroll.maxValue > 0)
    }

    private fun scrollToBottom() {
        composeRule.runOnIdle { scope.launch { scroll.scrollTo(scroll.maxValue) } }
        composeRule.waitForIdle()
    }

    private fun swipeContentDown(durationMillis: Long) {
        composeRule.onNodeWithTag(CONTENT, useUnmergedTree = true).performTouchInput {
            swipeDown(startY = top + 40f, endY = bottom - 40f, durationMillis = durationMillis)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a hard fling down from the bottom scrolls the content and leaves the sheet up`() {
        showExpandedSheet()
        scrollToBottom()

        swipeContentDown(durationMillis = 50)

        assertTrue("the content did not scroll", scroll.value < scroll.maxValue)
        assertEquals("the fling moved the sheet", SheetValue.Expanded, sheet.currentValue)
        assertFalse("the fling closed the sheet", dismissed)
    }

    @Test
    fun `a hard fling down from the bottom ends once the content reaches its top`() {
        showExpandedSheet()
        scrollToBottom()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(CONTENT, useUnmergedTree = true).performTouchInput {
            swipeDown(startY = top + 40f, endY = bottom - 40f, durationMillis = 50)
        }
        composeRule.mainClock.advanceTimeBy(600)

        // A fling that ran on past the top would still be scrolling, unseen, and take the next tap.
        assertEquals("the fling did not reach the top", 0, scroll.value)
        assertFalse("the fling ran on past the top", scroll.isScrollInProgress)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(SheetValue.Expanded, sheet.currentValue)
    }

    @Test
    fun `a slow drag down from the bottom stops at the top without moving the sheet`() {
        showExpandedSheet()
        scrollToBottom()

        swipeContentDown(durationMillis = 1500)

        assertEquals("the content did not reach its top", 0, scroll.value)
        assertEquals("the drag moved the sheet", SheetValue.Expanded, sheet.currentValue)
        assertFalse(dismissed)
    }

    @Test
    fun `once the content is at its top, the next fling down pulls the sheet down`() {
        showExpandedSheet()
        scrollToBottom()
        swipeContentDown(durationMillis = 50)
        composeRule.runOnIdle { scope.launch { scroll.scrollTo(0) } }
        composeRule.waitForIdle()

        swipeContentDown(durationMillis = 50)

        assertNotEquals("a fling at the top left the sheet up", SheetValue.Expanded, sheet.currentValue)
    }

    @Test
    fun `a drag down that starts at the top pulls the sheet down`() {
        showExpandedSheet()

        swipeContentDown(durationMillis = 1500)

        assertNotEquals("a drag at the top left the sheet up", SheetValue.Expanded, sheet.currentValue)
    }

    @Test
    fun `a fling up from the top still scrolls the content`() {
        showExpandedSheet()

        composeRule.onNodeWithTag(CONTENT, useUnmergedTree = true).performTouchInput {
            swipeUp(startY = bottom - 40f, endY = top + 40f, durationMillis = 200)
        }
        composeRule.waitForIdle()

        assertTrue("the content did not scroll up", scroll.value > 0)
        assertEquals(SheetValue.Expanded, sheet.currentValue)
    }

    private companion object {
        const val CONTENT = "sheet-content"
    }
}
