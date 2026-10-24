package org.ntust.app.tigerduck.shared.tls

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager
import kotlin.concurrent.thread

/** A hand-made Ed25519 key and signed pin documents, for tests only. */
class TestSigner(val keyId: String = "td-pins-test") {
    private val pair = Ed25519KeyPairGenerator().run {
        init(Ed25519KeyGenerationParameters(SecureRandom()))
        generateKeyPair()
    }

    val publicKey: ByteArray = (pair.public as Ed25519PublicKeyParameters).encoded

    val keys: Map<String, ByteArray> get() = mapOf(keyId to publicKey)

    fun sign(bytes: ByteArray): ByteArray = Ed25519Signer().run {
        init(true, pair.private as Ed25519PrivateKeyParameters)
        update(bytes, 0, bytes.size)
        generateSignature()
    }

    /** An envelope over [payload]'s bytes, as TigerSync serves it. */
    fun envelope(
        payload: String,
        format: Int = 1,
        keyId: String = this.keyId,
        signature: ByteArray = sign(payload.toByteArray()),
    ): ByteArray = JsonObject().apply {
        addProperty("format", format)
        addProperty("key_id", keyId)
        addProperty("payload", base64Url(payload.toByteArray()))
        addProperty("signature", base64Url(signature))
    }.toString().toByteArray()

    companion object {
        fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        fun payload(
            serial: Long,
            expiresAt: Instant,
            sets: List<PinSet>,
            format: Int = 1,
            issuedAt: Instant = expiresAt.minusSeconds(30L * 24 * 3600),
        ): String = JsonObject().apply {
            addProperty("format", format)
            addProperty("serial", serial)
            addProperty("issued_at", issuedAt.toString())
            addProperty("expires_at", expiresAt.toString())
            add(
                "pin_sets",
                JsonArray().apply {
                    sets.forEach { set ->
                        add(
                            JsonObject().apply {
                                addProperty("id", set.id)
                                add(
                                    "domains",
                                    JsonArray().apply {
                                        set.domains.forEach { domain ->
                                            add(
                                                JsonObject().apply {
                                                    addProperty("name", domain.name)
                                                    addProperty("include_subdomains", domain.includeSubdomains)
                                                },
                                            )
                                        }
                                    },
                                )
                                add("pins", JsonArray().apply { set.pins.forEach(::add) })
                                addProperty("expires_at", set.expiresAt.toString())
                            },
                        )
                    }
                },
            )
        }.toString()
    }
}

/**
 * The platform's validation as a JVM test can have it: [trust]'s check, then the chain built up to
 * the trust anchor that issued its last certificate, as Android's X509TrustManagerExtensions
 * returns it, so a root the server leaves out is still in the validated chain.
 */
class AnchoringValidator(private val trust: X509ExtendedTrustManager) : ChainValidator {
    override fun validate(chain: Array<X509Certificate>, authType: String, host: String): List<X509Certificate> {
        trust.checkServerTrusted(chain, authType)
        val built = chain.toMutableList()
        while (true) {
            val last = built.last()
            if (last.subjectX500Principal == last.issuerX500Principal) break
            val anchor = trust.acceptedIssuers.firstOrNull { candidate ->
                candidate.subjectX500Principal == last.issuerX500Principal &&
                    runCatching { last.verify(candidate.publicKey) }.isSuccess
            } ?: throw CertificateException("no anchor for ${last.issuerX500Principal}")
            if (anchor in built) break
            built += anchor
        }
        return built
    }
}

/** A TLS server on the loopback that serves [leaf] and [chain] and counts the request bytes it reads. */
class LocalTlsServer(leaf: HeldCertificate, chain: List<X509Certificate>) : AutoCloseable {
    private val socket = HandshakeCertificates.Builder()
        .heldCertificate(leaf, *chain.toTypedArray())
        .build()
        .sslContext()
        .serverSocketFactory
        .createServerSocket(0, 4, InetAddress.getLoopbackAddress()) as SSLServerSocket

    val port: Int = socket.localPort

    /** Bytes of request a client sent once its handshake was through. */
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
                        it.outputStream.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                        it.outputStream.flush()
                    }
                }
            }
        }
    }

    override fun close() = socket.close()
}
