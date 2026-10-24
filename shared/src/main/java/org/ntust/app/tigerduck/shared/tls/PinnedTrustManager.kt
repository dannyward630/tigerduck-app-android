package org.ntust.app.tigerduck.shared.tls

import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * A pinned host's certificate failed only its pins: a system certificate authority vouches for
 * it, for a host the student may continue to. The school may have renewed its certificate
 * before the app's pins were rotated.
 */
class PinMismatchException(
    /** The pinned domain, as [PinnedDomains] names it. */
    val domain: String,
    val host: String,
    cause: CertificateException,
) : CertificateException("certificate pins don't match for $host", cause)

/** Whether a [PinMismatchException] is in this throwable's causes, as a failed call wraps it. */
fun Throwable.isPinMismatch(): Boolean =
    generateSequence(this) { it.cause }.take(16).any { it is PinMismatchException }

/** Which pinned domains the student chose to continue to, so their pins are not checked. */
fun interface PinOverrides {
    fun isOverridden(domain: String): Boolean
}

/**
 * The trust every TigerDuck HTTPS client uses: the platform's, which checks
 * network_security_config.xml's pins, except where the student chose to continue past them.
 *
 * - A host no pin-set covers, or a call that names no host: the platform's check alone.
 * - A pinned host the student continued to: [systemOnly]'s, the system certificate
 *   authorities without the pins.
 * - A pinned host whose chain the platform refuses: if [systemOnly] accepts it, only its pins
 *   failed, so [onMismatch] hears of it and [PinMismatchException] is thrown. If it refuses it
 *   too, the platform's own failure is thrown and nothing offers a way past it.
 *
 * Every failure is thrown during the handshake, before a byte of the request is written.
 */
class PinnedTrustManager(
    /** The platform's, which applies network_security_config.xml, pins included. */
    private val platform: X509ExtendedTrustManager,
    /** The system certificate authorities without pins, made when first needed. */
    systemOnly: () -> X509ExtendedTrustManager,
    private val overrides: PinOverrides,
    private val onMismatch: (domain: String, host: String) -> Unit,
    private val domains: List<PinnedDomains.Domain> = PinnedDomains.all,
) : X509ExtendedTrustManager() {

    private val systemOnly by lazy(systemOnly)

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
        check(
            host = (socket as? SSLSocket)?.handshakeSession?.peerHost,
            pinned = { platform.checkServerTrusted(chain, authType, socket) },
            unpinned = { systemOnly.checkServerTrusted(chain, authType, socket) },
        )

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
        check(
            host = engine?.handshakeSession?.peerHost ?: engine?.peerHost,
            pinned = { platform.checkServerTrusted(chain, authType, engine) },
            unpinned = { systemOnly.checkServerTrusted(chain, authType, engine) },
        )

    // Without a socket or an engine there is no host to look up a pin-set for, so this is the
    // platform's check alone, exactly as the platform would make it.
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) =
        platform.checkServerTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
        platform.checkClientTrusted(chain, authType, socket)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
        platform.checkClientTrusted(chain, authType, engine)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        platform.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers

    private fun check(host: String?, pinned: () -> Unit, unpinned: () -> Unit) {
        val domain = host?.let { PinnedDomains.of(it, domains) }
        if (host == null || domain == null) {
            pinned()
            return
        }
        if (overrides.isOverridden(domain)) {
            unpinned()
            return
        }
        try {
            pinned()
        } catch (e: CertificateException) {
            // Anything short of the system's own trust, an expired, self-signed or misnamed
            // certificate among them, is the platform's failure, with no way past it.
            val systemTrusts = try {
                unpinned()
                true
            } catch (_: Exception) {
                false
            }
            if (!systemTrusts) throw e
            onMismatch(domain, host)
            throw PinMismatchException(domain, host, e)
        }
    }
}

/** The TLS a client takes from [PinnedTrustManager]: its socket factory and the manager itself. */
class PinnedTls(val trustManager: X509TrustManager) {

    val socketFactory: SSLSocketFactory =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }.socketFactory

    companion object {
        /**
         * The platform's trust, network_security_config.xml applied, wrapped in a
         * [PinnedTrustManager] that consults [overrides] and tells [onMismatch].
         */
        fun forPlatform(overrides: PinOverrides, onMismatch: (domain: String, host: String) -> Unit): PinnedTls =
            PinnedTls(PinnedTrustManager(platformTrust(), ::systemOnlyTrust, overrides, onMismatch))

        /** The platform's default trust manager: on Android, the one network_security_config.xml configures. */
        fun platformTrust(): X509ExtendedTrustManager = trustManagerFor(null)

        /**
         * The system certificate authorities alone, with no pins. On Android a trust manager
         * made from a key store of its own leaves network_security_config.xml out, pins and
         * all, so the store holds `AndroidCAStore`'s system entries and none a user installed,
         * the anchors the app's config allows.
         */
        fun systemOnlyTrust(): X509ExtendedTrustManager {
            val installed = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
            val system = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
            for (alias in installed.aliases()) {
                if (alias.startsWith("system:")) system.setCertificateEntry(alias, installed.getCertificate(alias))
            }
            return trustManagerFor(system)
        }

        private fun trustManagerFor(store: KeyStore?): X509ExtendedTrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(store) }
                .trustManagers
                .filterIsInstance<X509ExtendedTrustManager>()
                .first()
    }
}
