package org.ntust.app.tigerduck.shared.tls

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager
import kotlin.concurrent.thread

/**
 * [PinnedTrustManager] over a real handshake with a local TLS server and hand-made certificates.
 * A test root stands in for the system's certificate authorities, and [platform] for the
 * platform's network_security_config.xml check: the root's trust, plus pins on the hosts a
 * pin-set covers.
 */
class PinnedTrustManagerTest {

    private val root = HeldCertificate.Builder().certificateAuthority(0).commonName("Test Root").build()

    /** The certificate the pins were made from. */
    private val pinnedLeaf = leafSignedBy(root)

    /** The school's renewal: the same name, a new key, from the same trusted root. */
    private val renewedLeaf = leafSignedBy(root)

    /** No system authority vouches for this one. */
    private val selfSigned = HeldCertificate.Builder().addSubjectAlternativeName(HOST).build()

    private val systemOnly: X509ExtendedTrustManager =
        HandshakeCertificates.Builder().addTrustedCertificate(root.certificate).build().trustManager
            as X509ExtendedTrustManager

    private val pins = setOf(pinnedLeaf.certificate.spki)

    private val platform = object : X509ExtendedTrustManager() {
        private fun pinned(chain: Array<X509Certificate>) {
            if (chain.none { it.spki in pins }) throw CertificateException("Pin verification failed")
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
            systemOnly.checkServerTrusted(chain, authType, socket)
            pinned(chain)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
            systemOnly.checkServerTrusted(chain, authType, engine)
            pinned(chain)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            systemOnly.checkServerTrusted(chain, authType)
            pinned(chain)
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) = Unit
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) = Unit
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = systemOnly.acceptedIssuers
    }

    private val overridden = Collections.synchronizedSet(mutableSetOf<String>())
    private val mismatches = Collections.synchronizedList(mutableListOf<Pair<String, String>>())
    private val servers = mutableListOf<LocalTlsServer>()

    @After
    fun closeServers() = servers.forEach { it.close() }

    private fun client(domains: List<PinnedDomains.Domain> = listOf(PinnedDomains.Domain(HOST, false))): OkHttpClient {
        val tls = PinnedTls(
            PinnedTrustManager(
                platform = platform,
                systemOnly = { systemOnly },
                overrides = { it in overridden },
                onMismatch = { domain, host -> mismatches += domain to host },
                domains = domains,
            ),
        )
        return OkHttpClient.Builder()
            .sslSocketFactory(tls.socketFactory, tls.trustManager)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private fun serve(leaf: HeldCertificate, chain: List<X509Certificate> = emptyList()) =
        LocalTlsServer(leaf, chain).also { servers += it }

    private fun OkHttpClient.get(server: LocalTlsServer): String =
        newCall(Request.Builder().url("https://$HOST:${server.port}/code").build()).execute().use { it.body.string() }

    private fun OkHttpClient.failure(server: LocalTlsServer): IOException =
        try {
            get(server)
            fail("the call went through")
            throw AssertionError()
        } catch (e: IOException) {
            e
        }

    @Test
    fun `the pinned certificate is trusted`() {
        val server = serve(pinnedLeaf)

        assertEquals("ok", client().get(server))
        assertTrue(mismatches.isEmpty())
    }

    @Test
    fun `a renewal a system authority vouches for fails as a pin mismatch, before the request is written`() {
        val server = serve(renewedLeaf)

        val failure = client().failure(server)

        assertTrue("not a pin mismatch: $failure", failure.isPinMismatch())
        assertEquals(listOf(HOST to HOST), mismatches.toList())
        assertEquals(0, server.requestBytes.get())
    }

    @Test
    fun `a certificate no system authority vouches for fails as the platform's own failure`() {
        val server = serve(selfSigned)

        val failure = client().failure(server)

        assertFalse("offered as a pin mismatch: $failure", failure.isPinMismatch())
        assertTrue(mismatches.isEmpty())
        assertEquals(0, server.requestBytes.get())
    }

    @Test
    fun `a domain the student continued to takes the renewal`() {
        overridden += HOST
        val server = serve(renewedLeaf)

        assertEquals("ok", client().get(server))
        assertTrue(mismatches.isEmpty())
    }

    @Test
    fun `continuing never lets in a certificate no system authority vouches for`() {
        overridden += HOST
        val server = serve(selfSigned)

        val failure = client().failure(server)

        assertFalse(failure.isPinMismatch())
        assertEquals(0, server.requestBytes.get())
    }

    @Test
    fun `a host no pin-set covers is never offered a way past a failure`() {
        val server = serve(renewedLeaf)

        val failure = client(domains = listOf(PinnedDomains.Domain("other.test", false))).failure(server)

        assertFalse(failure.isPinMismatch())
        assertTrue(mismatches.isEmpty())
    }

    @Test
    fun `a continued domain doesn't cover another`() {
        overridden += "other.test"
        val server = serve(renewedLeaf)

        assertTrue(client().failure(server).isPinMismatch())
    }

    private class LocalTlsServer(leaf: HeldCertificate, chain: List<X509Certificate>) : AutoCloseable {
        private val socket = HandshakeCertificates.Builder()
            .heldCertificate(leaf, *chain.toTypedArray())
            .build()
            .sslContext()
            .serverSocketFactory
            .createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket

        val port: Int = socket.localPort

        /** The bytes of request a client sent once its handshake was through. */
        val requestBytes = AtomicInteger()

        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() as SSLSocket }.getOrNull() ?: break
                    runCatching {
                        client.use {
                            it.soTimeout = 5_000
                            it.startHandshake()
                            val input = it.inputStream
                            val head = StringBuilder()
                            while (!head.endsWith("\r\n\r\n")) {
                                val b = input.read()
                                if (b < 0) break
                                requestBytes.incrementAndGet()
                                head.append(b.toChar())
                            }
                            it.outputStream.write(
                                "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray(),
                            )
                            it.outputStream.flush()
                        }
                    }
                }
            }
        }

        override fun close() = socket.close()
    }

    private companion object {
        const val HOST = "localhost"

        fun leafSignedBy(root: HeldCertificate): HeldCertificate =
            HeldCertificate.Builder().addSubjectAlternativeName(HOST).signedBy(root).build()

        val X509Certificate.spki: String
            get() = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(publicKey.encoded))
    }
}
