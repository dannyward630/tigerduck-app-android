package org.ntust.app.tigerduck.ui.component

import android.app.Application
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [Build.VERSION_CODES.TIRAMISU])
class SearchFieldIconTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val spinner = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo.Indeterminate,
    )

    private fun showField(searching: () -> Boolean) {
        composeRule.setContent {
            OutlinedTextField(
                value = "期中考",
                onValueChange = {},
                leadingIcon = { SearchFieldIcon(searching = searching()) },
            )
        }
    }

    @Test
    fun `at rest the field shows the magnifier`() {
        showField { false }
        composeRule.onNode(spinner).assertDoesNotExist()
    }

    @Test
    fun `while a search runs the magnifier turns into a spinner, and back once it ends`() {
        val searching = mutableStateOf(true)
        showField { searching.value }
        composeRule.onNode(spinner).assertExists()

        searching.value = false
        composeRule.waitForIdle()
        composeRule.onNode(spinner).assertDoesNotExist()
    }
}
