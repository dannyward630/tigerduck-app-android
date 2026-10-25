package org.ntust.app.tigerduck.debug

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ntust.app.tigerduck.BuildConfig

/** An app outside TigerDuck that it hands something to; [missing] is how the developer settings name its absence. */
internal enum class OutsideApp(val missing: String, val detail: String) {
    MOODLE("No Moodle app", "moodlemobile:// links: Open in Moodle falls back to the web"),
    BROWSER("No browser", "Web links, Custom Tabs, and the default-browser lookup"),
    MAIL("No mail app", "mailto: links"),
    FILE_VIEWER("No app that opens files", "Downloaded Moodle files, mail attachments, browser downloads"),
    PLAY_STORE("No Play Store", "market:// links"),
}

/**
 * Debug builds only: makes TigerDuck act as on a phone without some of the
 * apps it hands things to, so the fallbacks can be tried on any phone.
 *
 * The activities pass every intent they start through [filter]. An intent
 * started on its own fails as Android fails it, with an
 * [ActivityNotFoundException], and the app's own fallback runs; a chooser
 * keeps its sheet but has nothing to offer, as the system shows it. Probes
 * that look for an app without starting one ask [isMissing].
 *
 * Kept in its own preferences file, for the same reason [DebugFixtureStore]
 * is: it must never end up inside an AppPreferences migration.
 */
internal object PretendMissingApps {
    private const val FILE = "debug_outside_apps"
    private const val KEY = "missing"

    /** A link no app opens: what a chooser for a missing app is left to show. */
    private val NOTHING = "tigerduck-debug-missing:nothing".toUri()

    private var prefs: SharedPreferences? = null
    private val _missing = MutableStateFlow<Set<OutsideApp>>(emptySet())
    val missing: StateFlow<Set<OutsideApp>> = _missing.asStateFlow()

    /** Reads the switches as they were left. Called as the app starts. */
    fun init(context: Context) {
        if (!BuildConfig.DEBUG) return
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).also { this.prefs = it }
        _missing.value = prefs.getStringSet(KEY, emptySet()).orEmpty()
            .mapNotNull { name -> OutsideApp.entries.firstOrNull { it.name == name } }
            .toSet()
    }

    fun setMissing(app: OutsideApp, missing: Boolean) {
        if (!BuildConfig.DEBUG) return
        val now = if (missing) _missing.value + app else _missing.value - app
        _missing.value = now
        prefs?.edit()?.putStringSet(KEY, now.map { it.name }.toSet())?.apply()
    }

    fun isMissing(app: OutsideApp): Boolean = BuildConfig.DEBUG && app in _missing.value

    /** The outside app [intent] needs, or null for one to TigerDuck itself or of no kind listed. */
    fun needs(intent: Intent, ownPackage: String): OutsideApp? {
        if (intent.component?.packageName == ownPackage || intent.`package` == ownPackage) return null
        val scheme = intent.data?.scheme?.lowercase() ?: return null
        val action = intent.action
        return when {
            scheme == "moodlemobile" -> OutsideApp.MOODLE
            scheme == "market" -> OutsideApp.PLAY_STORE
            scheme == "mailto" && (action == Intent.ACTION_SENDTO || action == Intent.ACTION_VIEW) -> OutsideApp.MAIL
            action != Intent.ACTION_VIEW -> null
            scheme == "http" || scheme == "https" -> OutsideApp.BROWSER
            scheme == "content" || scheme == "file" -> OutsideApp.FILE_VIEWER
            else -> null
        }
    }

    /**
     * [intent] as a phone without the apps marked missing would take it: an
     * intent for one throws, a chooser for one is left with nothing to offer,
     * and anything else goes as it is.
     */
    fun filter(intent: Intent, ownPackage: String): Intent {
        if (!BuildConfig.DEBUG || _missing.value.isEmpty()) return intent
        if (intent.action == Intent.ACTION_CHOOSER) {
            val inner = intent.chosen() ?: return intent
            val app = needs(inner, ownPackage) ?: return intent
            if (!isMissing(app)) return intent
            return Intent(intent).putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_VIEW, NOTHING))
        }
        val app = needs(intent, ownPackage) ?: return intent
        if (isMissing(app)) throw ActivityNotFoundException("No Activity found to handle $intent (pretending: ${app.missing})")
        return intent
    }

    private fun Intent.chosen(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_INTENT)
        }
}
