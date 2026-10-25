package org.ntust.app.tigerduck.network

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

/**
 * TigerDuck's backend as a Retrofit service [T], one per base URL. The base is
 * read on every call, since the endpoint setting can move it at any time, and
 * a Retrofit holds one base; so each base gets its own, made on first use.
 *
 * Every service sends through [calls], the client the call went through
 * before Retrofit, with its pins, interceptors and timeouts: a Retrofit given
 * no client would make one of its own, unpinned.
 */
internal class BackendServices<T : Any>(
    private val service: Class<T>,
    private val calls: okhttp3.Call.Factory,
) {
    private val byBase = ConcurrentHashMap<String, T>()

    /** The service at [base], a URL without a trailing slash, as `resolveAnnouncementEndpoint` gives it. */
    fun at(base: String): T = byBase.getOrPut(base) {
        Retrofit.Builder()
            // Retrofit resolves the services' relative paths against a base that
            // ends in a slash; without one it would drop the base's last segment.
            .baseUrl("${base.trimEnd('/')}/")
            .callFactory(calls)
            .addConverterFactory(JsonBodies)
            .build()
            .create(service)
    }
}

/**
 * Request bodies as the clients made them before Retrofit: plain [Gson]
 * (nulls left out), as `application/json; charset=utf-8`. A [RequestBody]
 * parameter goes as it is, through Retrofit's own converter. Answers are
 * read as they come ([ResponseBody], or nothing for [Unit]) and parsed by
 * the clients themselves, with the messages and status codes callers read.
 */
internal object JsonBodies : Converter.Factory() {
    private val gson = Gson()
    private val jsonType = "application/json".toMediaType()

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, RequestBody> = Converter<Any, RequestBody> { gson.toJson(it).toRequestBody(jsonType) }
}

/**
 * The body a DELETE carries: none to speak of, but a body all the same (length
 * 0, no content type), as OkHttp's `Request.Builder.delete()` sent; Retrofit's
 * plain `@DELETE` would send none, and so no `Content-Length` either.
 */
internal val EmptyBody: RequestBody = ByteArray(0).toRequestBody()

/** The answer's body as text, a success's or an error's alike, as OkHttp's `response.body.string()` read it. */
internal fun Response<ResponseBody>.text(): String = (if (isSuccessful) body() else errorBody())?.string().orEmpty()
