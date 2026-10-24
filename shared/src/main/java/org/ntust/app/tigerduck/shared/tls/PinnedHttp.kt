package org.ntust.app.tigerduck.shared.tls

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

/**
 * The one way TigerDuck starts an OkHttp client (A9): with [tls]'s pinned trust, and a call
 * that failed on a pin mismatch tried once more when its [PinMismatchResolver] allows. Clients
 * made from it with `newBuilder()` keep both. AllHttpsClientsPinnedTest fails on an
 * `OkHttpClient.Builder()` anywhere else.
 *
 * [tls] is null only in tests, for the platform's trust.
 */
fun pinnedClientBuilder(tls: PinnedTls?): OkHttpClient.Builder {
    val builder = OkHttpClient.Builder()
    if (tls != null) {
        builder.sslSocketFactory(tls.socketFactory, tls.trustManager)
        // First, so a retry passes through every interceptor the client adds after it.
        builder.addInterceptor(PinMismatchRetry(tls.resolver))
        (tls.trustManager as? PinnedTrustManager)?.let { builder.addNetworkInterceptor(ReusedConnectionPins(it)) }
    }
    return builder
}

/**
 * OkHttp may carry a request over an HTTP/2 connection made for another host, without a new
 * handshake: a pinned host reached that way gets its own pins checked against the connection's
 * chain, before a byte of the request is written.
 */
class ReusedConnectionPins(private val trust: PinnedTrustManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val connection = chain.connection()
        if (connection != null) {
            trust.checkReused(
                host = chain.request().url.host,
                connectedTo = connection.route().address.url.host,
                peerCertificates = connection.handshake()?.peerCertificates.orEmpty(),
            )
        }
        return chain.proceed(chain.request())
    }
}

/**
 * Tries a call once more after it failed on a [PinMismatch], when the resolver says to: a newer
 * pin document allows the chain now, or the student chose Continue. The handshake failed, so
 * nothing of the first attempt went out.
 */
class PinMismatchRetry(private val resolver: PinMismatchResolver) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return try {
            chain.proceed(request)
        } catch (e: IOException) {
            val mismatch = e.pinMismatch() ?: throw e
            if (!resolver.mayRetry(mismatch) { chain.call().isCanceled() }) throw e
            chain.proceed(request)
        }
    }
}
