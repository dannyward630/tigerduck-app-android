package org.ntust.app.tigerduck.widget

import android.app.Application
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.runComposition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.data.preferences.AppPreferences
import org.ntust.app.tigerduck.data.preferences.DisplayScale
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The widgets at the display size and text size picked for them: their type
 * and their own padding multiplied, the phone's sizes left as they are.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [Build.VERSION_CODES.R])
class WidgetScaleTest {

    private val app: Application = RuntimeEnvironment.getApplication()
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        app.getSharedPreferences("tigerduck_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = AppPreferences(app).apply { appLanguage = "en" }
    }

    private fun todayWidget() = object : BaseClassTableWidget(
        WidgetLayout.Today,
        SizeMode.Single,
        preferencesOf = { prefs },
        loadState = {
            WidgetState(
                courses = emptyList(),
                activeWeekdays = (1..5).toList(),
                activePeriodIds = emptyList(),
                currentWeekday = 1,
                currentMinuteOfDay = 7 * 60,
                isLoggedIn = true,
                isQuietToday = false,
                ongoingBlocks = emptyList(),
                nextTodayBlock = null,
                tomorrowFirstCourseNo = null,
                tomorrowFirstCourseWeekday = null,
                tomorrowFirstCoursePeriodId = null,
                courseColors = emptyMap(),
                courseNameScale = 1f,
            )
        },
    ) {}

    @OptIn(ExperimentalGlanceApi::class)
    private fun draw(widget: GlanceAppWidget): View = runBlocking {
        val views: RemoteViews = withTimeout(10_000) {
            widget.runComposition(app).first { texts(it.apply(app, FrameLayout(app))).isNotEmpty() }
        }
        views.apply(app, FrameLayout(app))
    }

    private fun texts(view: View): List<TextView> {
        val out = mutableListOf<TextView>()
        fun walk(v: View) {
            if (v is TextView && v.text.isNotEmpty()) out += v
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(view)
        return out
    }

    private fun textSize(root: View, text: String): Float =
        texts(root).single { it.text.toString() == text }.textSize

    /** The largest left padding in the widget: the Today list's own, around everything. */
    private fun outerPadding(root: View): Int {
        var most = 0
        fun walk(v: View) {
            most = maxOf(most, v.paddingLeft)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        return most
    }

    @Test
    fun `the widget text size multiplies the widget's type`() {
        val before = textSize(draw(todayWidget()), "No classes today")

        prefs.setDisplayScale(DisplayScale.WidgetText, 1.5f)
        val after = textSize(draw(todayWidget()), "No classes today")

        assertEquals(before * 1.5f, after, 0.01f)
    }

    @Test
    fun `the widget display size multiplies the widget's own padding, not its type`() {
        val plain = draw(todayWidget())
        val before = outerPadding(plain)
        val beforeText = textSize(plain, "No classes today")

        prefs.setDisplayScale(DisplayScale.WidgetUi, 1.3f)
        val scaled = draw(todayWidget())

        assertTrue("$before", before > 0)
        assertEquals(before * 1.3f, outerPadding(scaled).toFloat(), 1f)
        assertEquals(beforeText, textSize(scaled, "No classes today"), 0.01f)
    }

    @Test
    fun `the library shortcut follows the widget text size too`() {
        val before = textSize(draw(LibraryShortcutWidget(preferencesOf = { prefs })), "Library")

        prefs.setDisplayScale(DisplayScale.WidgetText, 0.8f)
        val after = textSize(draw(LibraryShortcutWidget(preferencesOf = { prefs })), "Library")

        assertEquals(before * 0.8f, after, 0.01f)
    }

    @Test
    fun `the app's own sizes leave the widgets alone`() {
        val before = textSize(draw(todayWidget()), "No classes today")

        prefs.setDisplayScale(DisplayScale.AppText, 1.5f)
        prefs.setDisplayScale(DisplayScale.AppUi, 1.3f)

        assertEquals(before, textSize(draw(todayWidget()), "No classes today"), 0.01f)
    }
}
