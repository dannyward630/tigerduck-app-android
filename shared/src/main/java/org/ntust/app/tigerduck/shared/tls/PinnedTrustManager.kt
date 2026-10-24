package org.ntust.app.tigerduck.shared.tls

import android.net.http.X509TrustManagerExtensions
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * A pinned host's chain passed system trust but shares no SPKI with its set's effective pins
 * (TLS pins spec, A4): what a prompt shows and what Continue stores.
 */
class PinMismatch(
    val setId: String,
    val host: String,
    /** The set's domains, which a Continue covers. */
    val domains: List<PinDomain>,
    /** The chain the platform validated, leaf first, up to its trust anchor. */
    val chain: List<X509Certificate>,
    /** Every SPKI of [chain]. */
    val spkis: Set<String>,
) {
    /** The SPKI of the chain's trust anchor, its last certificate, which D18 checks. */
    val anchorSpki: String get() = chain.last().spki

    /**
     * What a Continue stores (D17): the leaf's and the intermediates' SPKIs, never the
     * anchor's. A chain with no intermediate stores its leaf alone.
     */
    val acceptableSpkis: Set<String>
        get() = (if (chain.size > 1) chain.dropLast(1) else chain).map { it.spki }.toSet()
}

/** Thrown in the handshake for a [PinMismatch], before a byte of the request goes out. */
class PinMismatchException(val mismatch: PinMismatch) :
    CertificateException("certificate pins don't match for ${mismatch.host}")

/** The [PinMismatch] among this throwable's causes, as a failed call wraps it, or null. */
fun Throwable.pinMismatch(): PinMismatch? =
    generateSequence(this) { it.cause }.take(16).filterIsInstance<PinMismatchException>().firstOrNull()?.mismatch

/** The base64 SHA-256 of a certificate's SubjectPublicKeyInfo DER, the form pins take. */
val X509Certificate.spki: String
    get() = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(publicKey.encoded))

/**
 * Validates a server's chain with the platform's system trust for [host], returning the chain it
 * built up to its trust anchor, or throwing as the platform does.
 */
fun interface ChainValidator {
    fun validate(chain: Array<X509Certificate>, authType: String, host: String): List<X509Certificate>
}

/**
 * Every TigerDuck HTTPS client's trust (A9). The platform's network-security-config trust
 * manager decides system trust, as it always has; this applies the pins on top (A4):
 *
 * - a host no pin set covers, or a call naming no host: the platform's check alone;
 * - a pinned host: the platform validates the chain and builds it up to its anchor, so a root
 *   pin matches even when the server leaves the root out. A chain it refuses fails as the
 *   platform failed, never as a mismatch. A validated chain is allowed when its set has expired
 *   or any of its SPKIs is an effective pin; otherwise [onMismatch] hears of it and
 *   [PinMismatchException] fails the handshake.
 */
class PinnedTrustManager(
    private val platform: X509ExtendedTrustManager,
    private val validator: ChainValidator,
    private val policy: () -> PinPolicy,
    private val onMismatch: (PinMismatch) -> Unit,
    private val now: () -> Instant = Instant::now,
) : X509ExtendedTrustManager() {

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
        check(chain, authType, (socket as? SSLSocket)?.handshakeSession?.peerHost) {
            platform.checkServerTrusted(chain, authType, socket)
        }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
        check(chain, authType, engine?.handshakeSession?.peerHost ?: engine?.peerHost) {
            platform.checkServerTrusted(chain, authType, engine)
        }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) =
        platform.checkServerTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
        platform.checkClientTrusted(chain, authType, socket)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
        platform.checkClientTrusted(chain, authType, engine)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        platform.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers

    /**
     * For a request to [host] carried over a connection made for [connectedTo], as OkHttp's
     * HTTP/2 coalescing does without asking this manager again: the connection's
     * [peerCertificates] must meet [host]'s own pins when its set isn't [connectedTo]'s.
     * Throws before the request is written.
     */
    fun checkReused(host: String, connectedTo: String, peerCertificates: List<Certificate>) {
        if (host.equals(connectedTo, ignoreCase = true)) return
        val rules = policy()
        val at = now()
        val set = rules.setFor(host, at) ?: return
        if (rules.setFor(connectedTo, at)?.id == set.id) return
        val spkis = peerCertificates.filterIsInstance<X509Certificate>().map { it.spki }.toSet()
        if (rules.decide(set, spkis, at) != PinDecision.Allowed) {
            throw SSLPeerUnverifiedException("pins for $host not met on a connection made for $connectedTo")
        }
    }

    private fun check(chain: Array<X509Certificate>, authType: String, host: String?, unpinned: () -> Unit) {
        val rules = policy()
        val at = now()
        val set = host?.let { rules.setFor(it, at) }
        if (host == null || set == null) {
            unpinned()
            return
        }
        val validated = validator.validate(chain, authType, host)
        val spkis = validated.map { it.spki }.toSet()
        when (val decision = rules.decide(set, spkis, at)) {
            PinDecision.Allowed -> Unit
            is PinDecision.Mismatch -> {
                val mismatch = PinMismatch(decision.set.id, host, decision.set.domains, validated, spkis)
                runCatching { onMismatch(mismatch) }
                throw PinMismatchException(mismatch)
            }
        }
    }
}

/**
 * What a client does once a call failed on a [PinMismatch]: whether to try it once more, which
 * may wait for a newer pin document or, in the foreground, the student's answer (A4, A6).
 */
fun interface PinMismatchResolver {
    /** Whether to try once more; [isCanceled] says the call was given up, by its timeout or its caller. */
    fun mayRetry(mismatch: PinMismatch, isCanceled: () -> Boolean): Boolean
}

/** The TLS every TigerDuck HTTPS client takes: [PinnedTrustManager]'s socket factory and the manager. */
class PinnedTls(
    val trustManager: X509TrustManager,
    val resolver: PinMismatchResolver = PinMismatchResolver { _, _ -> false },
) {

    val socketFactory: SSLSocketFactory =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }.socketFactory

    companion object {
        /**
         * The platform's trust, network_security_config.xml applied, with [state]'s pins on top.
         * The platform builds the validated chain through [X509TrustManagerExtensions], public
         * since API 17.
         */
        fun forPlatform(
            state: PinState,
            onMismatch: (PinMismatch) -> Unit,
            resolver: PinMismatchResolver,
        ): PinnedTls {
            val platform = platformTrust()
            val extensions = X509TrustManagerExtensions(platform)
            val validator = ChainValidator { chain, authType, host -> extensions.checkServerTrusted(chain, authType, host) }
            return PinnedTls(PinnedTrustManager(platform, validator, { state.policy }, onMismatch), resolver)
        }

        /** The platform's default trust manager: on Android, the one network_security_config.xml configures. */
        fun platformTrust(): X509ExtendedTrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(null as KeyStore?) }
                .trustManagers
                .filterIsInstance<X509ExtendedTrustManager>()
                .first()
    }
}
