package org.ntust.app.tigerduck.push

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The backend's device and sync endpoints, relative to its base (`…/v3`), for
 * [PushApiClient]. `Authorization` is the Bearer header the client resolves
 * before the call. Paths and queries are given already encoded, as the
 * client wrote its URLs before Retrofit. A DELETE carries an empty body
 * ([org.ntust.app.tigerduck.network.EmptyBody]), as OkHttp's `delete()` did.
 */
@JvmSuppressWildcards
internal interface PushApi {

    @POST("devices/anonymous")
    fun registerAnonymous(@Body request: AnonymousDeviceRequest): Call<Unit>

    @POST("devices/register")
    fun register(@Header("Authorization") auth: String, @Body request: DeviceRegisterRequest): Call<ResponseBody>

    @HTTP(method = "DELETE", path = "devices/{deviceId}", hasBody = true)
    fun deleteDevice(
        @Header("Authorization") auth: String,
        @Path("deviceId", encoded = true) deviceId: String,
        @Body empty: RequestBody,
    ): Call<Unit>

    @PUT("sync/holiday-overrides/{holidayId}")
    fun putHolidayOverride(
        @Header("Authorization") auth: String,
        @Path("holidayId", encoded = true) holidayId: Int,
        @Body request: HolidayOverrideRequest,
    ): Call<Unit>

    @PATCH("devices/{deviceId}/preferences")
    fun updateDevicePreferences(
        @Header("Authorization") auth: String,
        @Path("deviceId", encoded = true) deviceId: String,
        @Body request: UpdateDevicePreferencesRequest,
    ): Call<ResponseBody>

    @PATCH("auth/credentials")
    fun updateCredentials(@Header("Authorization") auth: String, @Body credentials: Map<String, String?>): Call<Unit>

    @PATCH("sync/assignments/{assignmentId}/override")
    fun patchAssignmentOverride(
        @Header("Authorization") auth: String,
        @Path("assignmentId", encoded = true) assignmentId: Int,
        @Body override: Map<String, String>,
    ): Call<Unit>

    @PATCH("sync/courses/{courseId}/override")
    fun patchCourseOverride(
        @Header("Authorization") auth: String,
        @Path("courseId", encoded = true) courseId: String,
        @Body override: Map<String, Any?>,
    ): Call<Unit>

    /** The same override as [patchCourseOverride], whose answer says whether the backend kept the field. */
    @PATCH("sync/courses/{moodleId}/override")
    fun patchCourseIgnore(
        @Header("Authorization") auth: String,
        @Path("moodleId", encoded = true) moodleId: String,
        @Body override: Map<String, Any>,
    ): Call<ResponseBody>

    /** [body] is the JSON already written, which the upload's fingerprint was taken of. */
    @POST("sync/assignments/upload")
    fun uploadAssignments(@Header("Authorization") auth: String, @Body body: RequestBody): Call<Unit>

    @POST("sync/courses/upload")
    fun uploadCourses(@Header("Authorization") auth: String, @Body body: RequestBody): Call<ResponseBody>

    @HTTP(method = "DELETE", path = "sync/courses/{courseKey}", hasBody = true)
    fun deleteCourse(
        @Header("Authorization") auth: String,
        @Path("courseKey", encoded = true) courseKey: String,
        @Body empty: RequestBody,
    ): Call<Unit>

    /** [semester] form-encoded already (`URLEncoder`), or null for every term. */
    @HTTP(method = "DELETE", path = "sync/courses", hasBody = true)
    fun deleteAllCourses(
        @Header("Authorization") auth: String,
        @Query("semester", encoded = true) semester: String?,
        @Body empty: RequestBody,
    ): Call<Unit>
}
