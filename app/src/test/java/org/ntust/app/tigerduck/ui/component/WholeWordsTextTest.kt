// Lays WholeWordsText out with real text (native graphics), so whether it hyphenates is
// decided by Android's own line breaker, as on a phone.

package org.ntust.app.tigerduck.ui.component

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WholeWordsTextTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var width by mutableStateOf(120.dp)

    @Test
    fun `a word wider than its line is hyphenated`() {
        show("Electroencephalography")

        val layout = layout()
        assertEquals(Hyphens.Auto, layout.layoutInput.style.hyphens)
        assertTrue("it fit on one line, so the test proves nothing", layout.lineCount > 1)
    }

    @Test
    fun `words that fit their lines whole are not hyphenated`() {
        show("Operating Systems Laboratory")

        val layout = layout()
        assertTrue("it fit on one line, so the test proves nothing", layout.lineCount > 1)
        assertNotEquals(Hyphens.Auto, layout.layoutInput.style.hyphens)
    }

    @Test
    fun `it goes back to whole words once the line is wide enough`() {
        show("Electroencephalography")
        assertEquals(Hyphens.Auto, layout().layoutInput.style.hyphens)

        width = 600.dp
        composeRule.waitForIdle()

        assertNotEquals(Hyphens.Auto, layout().layoutInput.style.hyphens)
        assertEquals(1, layout().lineCount)
    }

    @Test
    @Config(qualifiers = "zh-rTW")
    fun `Chinese breaks between characters and is never hyphenated`() {
        show("資訊工程系計算機結構實驗室")

        val layout = layout()
        assertTrue("it fit on one line, so the test proves nothing", layout.lineCount > 1)
        assertNotEquals(Hyphens.Auto, layout.layoutInput.style.hyphens)
    }

    private fun show(text: String) {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.requiredWidth(width)) {
                    WholeWordsText(text = text, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun layout(): TextLayoutResult {
        val node = composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .single()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }
}
