package org.petkitlocal.core.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.petkitlocal.core.network.dto.ActionRequestDto
import org.petkitlocal.core.network.dto.ActionResultDto
import org.petkitlocal.core.network.dto.BleSyncDto
import org.petkitlocal.core.network.dto.ChangePasswordRequestDto
import org.petkitlocal.core.network.dto.ChangePasswordResponseDto
import org.petkitlocal.core.network.dto.DeferredFeedListResponseDto
import org.petkitlocal.core.network.dto.DeferredFeedRequestDto
import org.petkitlocal.core.network.dto.DeferredFeedResultDto
import org.petkitlocal.core.network.dto.DeviceListResponseDto
import org.petkitlocal.core.network.dto.DeviceResponseDto
import org.petkitlocal.core.network.dto.DrinkUploadRequestDto
import org.petkitlocal.core.network.dto.DrinkUploadResultDto
import org.petkitlocal.core.network.dto.EntityWriteRequestDto
import org.petkitlocal.core.network.dto.EntityWriteResultDto
import org.petkitlocal.core.network.dto.EventResponseDto
import org.petkitlocal.core.network.dto.FeedingPlanResponseDto
import org.petkitlocal.core.network.dto.FeedingPlanResultDto
import org.petkitlocal.core.network.dto.FeedingPlanWriteDto
import org.petkitlocal.core.network.dto.InfoDto
import org.petkitlocal.core.network.dto.LoginRequestDto
import org.petkitlocal.core.network.dto.LoginResponseDto
import org.petkitlocal.core.network.dto.NotificationKindsRequestDto
import org.petkitlocal.core.network.dto.NotificationSettingsDto
import org.petkitlocal.core.network.dto.OkDto
import org.petkitlocal.core.network.dto.PetCreateRequestDto
import org.petkitlocal.core.network.dto.PetFaceListDto
import org.petkitlocal.core.network.dto.PetFaceResultDto
import org.petkitlocal.core.network.dto.PetListResponseDto
import org.petkitlocal.core.network.dto.PetResponseDto
import org.petkitlocal.core.network.dto.PetUpdateRequestDto
import org.petkitlocal.core.network.dto.PetWriteResponseDto
import org.petkitlocal.core.network.dto.PetsSourceDto
import org.petkitlocal.core.network.dto.PetsSourceRequestDto
import org.petkitlocal.core.network.dto.TimelinePageDto
import org.petkitlocal.core.network.dto.WriteResultDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * The REST routes of petkit-local's `/app/v1` API (api/openapi.yaml), relative to the API base
 * (`<server>/app/v1/`). The two WebSockets are not here ([LiveUpdatesSocket], and
 * [org.petkitlocal.core.network.video.LiveSocketDataSource] for live video), nor the live HLS
 * files, which Media3 fetches itself ([org.petkitlocal.core.network.video.LiveMediaSources]).
 *
 * Every call throws on failure (an `HttpException` for a non-2xx status, an `IOException` when
 * the server is unreachable); [apiCall] turns both into a [org.petkitlocal.core.domain.DomainError].
 * The contract tests check every method here against the document: path, method, parameters,
 * request body and response body.
 */
@Suppress("TooManyFunctions") // One function per route of the API; splitting it would hide routes.
interface PetkitApi {
    // meta

    /** Public. With a bearer token, `server_version` and `session` are filled in. */
    @GET("info")
    suspend fun getInfo(): InfoDto

    @Streaming
    @GET("openapi.yaml")
    suspend fun getOpenApi(): ResponseBody

    // auth

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): LoginResponseDto

    @POST("auth/logout")
    suspend fun logout(): OkDto

    /** Revokes every other session; this one stays signed in. */
    @POST("auth/password")
    suspend fun changePassword(@Body body: ChangePasswordRequestDto): ChangePasswordResponseDto

    // notifications: the socket itself (`notifications/ws`) is NotificationSocket. UnifiedPush
    // (`/push`) is not used by the app in v1 (ADR 0007).

    /** This session's notification kinds, and whether it holds the notification socket. */
    @GET("notifications")
    suspend fun getNotificationSettings(): NotificationSettingsDto

    /** Replaces this session's kinds, for every channel; answers the settings now held. */
    @PUT("notifications")
    suspend fun setNotificationKinds(@Body body: NotificationKindsRequestDto): NotificationSettingsDto

    // devices and controls

    @GET("devices")
    suspend fun listDevices(): DeviceListResponseDto

    @GET("devices/{id}")
    suspend fun getDevice(@Path("id") id: Long): DeviceResponseDto

    /** A destructive action needs `ActionRequestDto(confirm = true)`; others take no body. */
    @POST("devices/{id}/actions/{action}")
    suspend fun runDeviceAction(
        @Path("id") id: Long,
        @Path("action") action: String,
        @Body body: ActionRequestDto = ActionRequestDto(),
    ): ActionResultDto

    @PUT("devices/{id}/entities/{key}")
    suspend fun setDeviceEntity(
        @Path("id") id: Long,
        @Path("key") key: String,
        @Body body: EntityWriteRequestDto,
    ): EntityWriteResultDto

    // feeding

    @GET("devices/{id}/schedule")
    suspend fun getFeedingPlan(@Path("id") id: Long): FeedingPlanResponseDto

    @PUT("devices/{id}/schedule")
    suspend fun setFeedingPlan(@Path("id") id: Long, @Body body: FeedingPlanWriteDto): FeedingPlanResultDto

    @GET("devices/{id}/deferred-feeds")
    suspend fun listDeferredFeeds(@Path("id") id: Long): DeferredFeedListResponseDto

    @POST("devices/{id}/deferred-feeds")
    suspend fun addDeferredFeed(@Path("id") id: Long, @Body body: DeferredFeedRequestDto): DeferredFeedResultDto

    @DELETE("devices/{id}/deferred-feeds/{feed_id}")
    suspend fun deleteDeferredFeed(@Path("id") id: Long, @Path("feed_id") feedId: String): WriteResultDto

    // accessories: a CTW3's drink history, read by the phone over its own Bluetooth

    /** Answers 409 `secret_unknown` / `secret_invalid` for a CTW3 whose pairing secret is not held. */
    @GET("accessories/{id}/ble-sync")
    suspend fun getAccessoryBleSync(@Path("id") id: Long): BleSyncDto

    @POST("accessories/{id}/drinks")
    suspend fun uploadAccessoryDrinks(@Path("id") id: Long, @Body body: DrinkUploadRequestDto): DrinkUploadResultDto

    // timeline

    /** [day] is `YYYY-MM-DD`; [kind] one of `TimelineFilter.KNOWN`; [cursor] verbatim. */
    @Suppress("LongParameterList") // The route's own query parameters.
    @GET("timeline")
    suspend fun getTimeline(
        @Query("device") device: Long? = null,
        @Query("day") day: String? = null,
        @Query("kind") kind: String? = null,
        @Query("pet") pet: Long? = null,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null,
    ): TimelinePageDto

    @GET("timeline/{event_id}")
    suspend fun getTimelineEvent(@Path("event_id") eventId: Long): EventResponseDto

    // media: [path] is a media-tree path as found in a `Media` object, slashes included.

    @Streaming
    @GET("media/thumb/{path}")
    suspend fun getMediaThumbnail(@Path("path", encoded = true) path: String): ResponseBody

    /** Supports `Range`; the answer is 206 for a range, hence the full [Response]. */
    @Streaming
    @GET("media/{path}")
    suspend fun getMediaFile(
        @Path("path", encoded = true) path: String,
        @Header("Range") range: String? = null,
    ): Response<ResponseBody>

    // pets

    @GET("pets")
    suspend fun listPets(): PetListResponseDto

    @GET("pets/{id}")
    suspend fun getPet(@Path("id") id: Long): PetResponseDto

    /** Answers 201 with the pet as created. */
    @POST("pets")
    suspend fun createPet(@Body body: PetCreateRequestDto): PetWriteResponseDto

    /** Only the fields sent change. */
    @PATCH("pets/{id}")
    suspend fun updatePet(@Path("id") id: Long, @Body body: PetUpdateRequestDto): PetWriteResponseDto

    /** The pet and every face photo it has; its visits stay in the history, unnamed. */
    @DELETE("pets/{id}")
    suspend fun deletePet(@Path("id") id: Long): OkDto

    @Streaming
    @GET("pets/{id}/photo")
    suspend fun getPetPhoto(@Path("id") id: Long): ResponseBody

    @GET("pets/{id}/faces")
    suspend fun listPetFaces(@Path("id") id: Long): PetFaceListDto

    /** [photo] is the raw bytes of one JPEG, sent as `image/jpeg` (not a form). */
    @POST("pets/{id}/faces")
    suspend fun addPetFace(@Path("id") id: Long, @Body photo: RequestBody): PetFaceResultDto

    @DELETE("pets/{id}/faces/{face_id}")
    suspend fun deletePetFace(@Path("id") id: Long, @Path("face_id") faceId: Long): OkDto

    @Streaming
    @GET("pets/{id}/faces/{face_id}/photo")
    suspend fun getPetFacePhoto(@Path("id") id: Long, @Path("face_id") faceId: Long): ResponseBody

    // settings

    /** Whose pets the devices recognise while proxy mode is on. */
    @GET("settings/pets-source")
    suspend fun getPetsSource(): PetsSourceDto

    @PATCH("settings/pets-source")
    suspend fun setPetsSource(@Body body: PetsSourceRequestDto): PetsSourceDto
}
