package org.ntust.app.tigerduck.ui.screen.whatsnew

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.data.model.AppFeature
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 2.4.0 demos: each one draws, animated or still, in both languages, and
 * TalkBack hears it as the one description, not its screens' text or the
 * captions that take turns under it.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one is a Hilt app these tests do not need.
@Config(application = Application::class)
class WhatsNewDemosTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class Demo(val page: WhatsNewPage, val description: WhatsNewText, val captions: List<WhatsNewText>)

    private val demos = listOf(
        Demo(
            moodleCoursePage(),
            WhatsNewText(en = "Demo: a course opens", zhHant = "示範：從課表點開課程"),
            listOf(WhatsNewText(en = "Without the Moodle app", zhHant = "沒有 Moodle App 時")),
        ),
        Demo(moodleAssignmentPage(), WhatsNewText(en = "Demo: an assignment opens", zhHant = "示範：點開作業"), emptyList()),
        Demo(
            classTableSharePage(),
            WhatsNewText(en = "Demo: one phone shows", zhHant = "示範：一支手機顯示"),
            listOf(
                WhatsNewText(en = "Scanned in TigerDuck", zhHant = "用 TigerDuck 掃描"),
                WhatsNewText(en = "Any QR scanner", zhHant = "用任何 QR code 掃描器"),
            ),
        ),
        Demo(
            libraryPage(isSignedIn = { false }),
            WhatsNewText(en = "Demo: the library pass opens", zhHant = "示範：從底部功能列"),
            listOf(WhatsNewText(en = "From More", zhHant = "從「更多」")),
        ),
        Demo(mailNotificationsPage(), WhatsNewText(en = "Demo: in School Mail", zhHant = "示範：在校園信箱通知"), emptyList()),
        Demo(
            emptyClassroomPage(),
            WhatsNewText(en = "Demo: a suggested free room", zhHant = "示範：推薦兩堂課之間"),
            listOf(
                WhatsNewText(en = "Along the same floor", zhHant = "本樓平移"),
                WhatsNewText(en = "Over to another building", zhHant = "去其他棟"),
            ),
        ),
        Demo(displaySizePage(), WhatsNewText(en = "Demo: the display size slider", zhHant = "示範：拉動顯示大小滑桿"), emptyList()),
    )

    private fun WhatsNewPage.visual(): WhatsNewVisual.Custom = when (this) {
        is WhatsNewPage.Feature -> visual as WhatsNewVisual.Custom
        is WhatsNewPage.OptIn -> visual as WhatsNewVisual.Custom
        else -> error("$id has no demo")
    }

    @Test
    fun `every demo draws through its loop, animated and still, in both languages`() {
        var language by mutableStateOf(WhatsNewLanguage.En)
        var animate by mutableStateOf(true)
        var index by mutableStateOf(0)
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalWhatsNewLanguage provides language) {
                    // As wide as the sheet's page on a small phone.
                    Box(Modifier.width(312.dp)) { demos[index].page.visual().content(animate) }
                }
            }
        }
        composeRule.mainClock.autoAdvance = false
        for (i in demos.indices) for (lang in WhatsNewLanguage.entries) for (moving in listOf(true, false)) {
            index = i
            language = lang
            animate = moving
            // With the clock held, the change reaches the screen once the main thread has run.
            composeRule.waitForIdle()
            // Through a whole loop, a step at a time.
            repeat(12) { composeRule.mainClock.advanceTimeBy(1_000) }
            val demo = demos[i]
            composeRule.onAllNodesWithContentDescription(demo.description.resolve(lang), substring = true)
                .assertCountEquals(1)
            demo.captions.forEach { caption ->
                composeRule.onAllNodesWithText(caption.resolve(lang), substring = true).assertCountEquals(0)
            }
        }
    }

    @Test
    fun `the library question's demo draws the bar with library added`() {
        composeRule.setContent {
            MaterialTheme {
                libraryBottomBarPage(
                    isSignedIn = { false },
                    tabs = { listOf(AppFeature.HOME, AppFeature.CLASS_TABLE) },
                    setTabs = {},
                ).visual().content(true)
            }
        }
        composeRule.onAllNodesWithContentDescription("After: Home, Class table, Library, More", substring = true)
            .assertCountEquals(1)
    }
}
