package org.ntust.app.tigerduck.wear

import java.util.Locale

/**
 * When the watch recreates its activity to show the phone's language.
 *
 * The tag is stored twice: in the snapshot, and in a start-up copy that
 * `MainActivity.attachBaseContext` applies. When the copy is behind (the
 * process died between the two writes), every new instance comes up in the old
 * language and asks to recreate again: an endless relaunch with a blank screen.
 * So the copy is brought up to date before recreating, and a language is
 * recreated for at most once a process.
 */
internal object WearLocaleSwitch {

    enum class Step {
        /** The screen shows the wanted language, or the phone sent none. */
        Keep,

        /** Bring the start-up copy up to date, then recreate. */
        Recreate,

        /** Recreated for this language already and it didn't take: recreating again would loop. */
        Stay,
    }

    /** The language this process last recreated for, as [normalized] gives it. */
    @Volatile
    var triedFor: String? = null

    /** What to do when the phone wants [wanted], the screen shows [showing], and [triedFor] was tried. */
    fun step(wanted: String?, showing: String?, triedFor: String?): Step {
        val want = normalized(wanted) ?: return Step.Keep
        if (want == normalized(showing)) return Step.Keep
        if (want == normalized(triedFor)) return Step.Stay
        return Step.Recreate
    }

    /** [tag] in BCP 47's own form, lower case, so "zh-hant-tw" and "zh-Hant-TW" are one; null for none. */
    fun normalized(tag: String?): String? =
        tag?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { Locale.forLanguageTag(it).toLanguageTag().lowercase(Locale.ROOT) }
            ?.takeIf { it != "und" }
}
