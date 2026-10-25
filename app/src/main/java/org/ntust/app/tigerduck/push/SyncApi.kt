package org.ntust.app.tigerduck.push

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** The backend's sync snapshot and change feed, relative to its base (`…/v3`), for [SyncApiClient]. */
internal interface SyncApi {

    @GET("sync/revision")
    fun revision(@Header("Authorization") auth: String): Call<ResponseBody>

    @GET("sync")
    fun changes(
        @Header("Authorization") auth: String,
        @Query("since_revision", encoded = true) sinceRevision: Long,
    ): Call<ResponseBody>

    @GET("sync/full")
    fun full(@Header("Authorization") auth: String): Call<ResponseBody>
}

/** The backend's per-namespace settings documents, relative to its base (`…/v3`), for [SettingsDocumentApiClient]. */
@JvmSuppressWildcards
internal interface SettingsDocumentApi {

    @GET("settings/{namespace}")
    fun document(
        @Header("Authorization") auth: String,
        @Path("namespace", encoded = true) namespace: String,
    ): Call<ResponseBody>

    /** [payload] is `schema_version`, `document` and `base_revision`, left out when null. */
    @PUT("settings/{namespace}")
    fun putDocument(
        @Header("Authorization") auth: String,
        @Path("namespace", encoded = true) namespace: String,
        @Body payload: Map<String, Any?>,
    ): Call<ResponseBody>
}
