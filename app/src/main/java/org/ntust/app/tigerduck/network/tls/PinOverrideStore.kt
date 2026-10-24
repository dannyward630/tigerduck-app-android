package org.ntust.app.tigerduck.network.tls

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import org.ntust.app.tigerduck.BuildConfig
import org.ntust.app.tigerduck.shared.tls.PinOverrides
import org.ntust.app.tigerduck.shared.tls.PinnedDomains
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pinned domains the student chose to continue to when their certificate stopped matching
 * the app's pins. A choice lasts until the app is updated: it is stored with the versionCode it
 * was made in, and a different running versionCode, which should carry rotated pins, ends it.
 * Nothing else does. A sign-out, an account change and a full reset keep it, since it is about
 * the school's certificate, not anyone's account; its file is one a full reset doesn't empty.
 */
@Singleton
class PinOverrideStore internal constructor(
    private val prefs: SharedPreferences,
    private val versionCode: Int,
    private val clock: () -> Long,
) : PinOverrides {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE),
        BuildConfig.VERSION_CODE,
        System::currentTimeMillis,
    )

    override fun isOverridden(domain: String): Boolean = prefs.getInt(versionKey(domain), NONE) == versionCode

    /** Continues to [domain] past its pins until the app is updated. */
    fun accept(domain: String) {
        prefs.edit()
            .putInt(versionKey(domain), versionCode)
            .putLong(acceptedKey(domain), clock())
            .apply()
    }

    /** Each domain continued to in this version, with when the student chose it, for the watch. */
    fun active(): List<Pair<String, Long>> =
        PinnedDomains.all.map { it.name }.filter(::isOverridden).map { it to prefs.getLong(acceptedKey(it), 0L) }

    private fun versionKey(domain: String) = "$domain.versionCode"

    private fun acceptedKey(domain: String) = "$domain.acceptedAtMs"

    companion object {
        const val FILE = "pin_overrides"
        private const val NONE = -1
    }
}
