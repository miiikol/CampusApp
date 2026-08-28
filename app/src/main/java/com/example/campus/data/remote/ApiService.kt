package com.example.campus.data.remote

import com.example.campus.data.remote.dto.*
import okhttp3.MultipartBody
import retrofit2.http.*

/**
 * 后端 REST API 定义（v1 版本化路径）。
 *
 * 说明：
 * - 所有接口均为挂起函数，需在协程中调用
 * - 默认以 JSON 进行请求/响应序列化（GsonConverterFactory）
 * - API 路径使用 /v1/ 前缀，支持版本迭代
 */
interface ApiService {

    /**
     * 用户登录
     */
    @POST("auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): UserDto

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): SimpleMessageResponse

    @POST("users/{userId}/profile")
    suspend fun updateUserProfile(
        @Path("userId") userId: String,
        @Body request: UpdateProfileRequest
    ): UserDto

    @Multipart
    @POST("upload/image")
    suspend fun uploadImage(@Part file: MultipartBody.Part): ImageUploadResponse

    /**
     * 拉取课程列表
     */
    @GET("courses")
    suspend fun getCourses(
        @Query("userId") userId: String? = null,
        @Query("studentId") studentId: String? = null
    ): List<CourseDto>

    @POST("courses/customize")
    suspend fun customizeCourse(
        @Body request: CustomizeCourseRequest
    ): SimpleMessageResponse

    /**
     * 拉取资讯列表
     */
    @GET("news")
    suspend fun getNews(
        @Query("userId") userId: String? = null
    ): PagedResponse<NewsDto>

    /**
     * 管理员发布资讯
     */
    @POST("news")
    suspend fun publishNews(@Body request: CreateNewsRequest): NewsDto

    @POST("news/{newsId}/favorites/toggle")
    suspend fun toggleNewsFavorite(
        @Path("newsId") newsId: String,
        @Body request: FavoriteToggleRequest
    ): FavoriteToggleResponse

    @GET("news/{newsId}/comments")
    suspend fun getNewsComments(
        @Path("newsId") newsId: String,
        @Query("userId") userId: String? = null,
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null
    ): CommentListResponse

    @POST("news/{newsId}/comments")
    suspend fun createNewsComment(
        @Path("newsId") newsId: String,
        @Body request: CreateCommentRequest
    ): CommentDto

    @GET("news/{newsId}/likes")
    suspend fun getNewsLikes(
        @Path("newsId") newsId: String,
        @Query("userId") userId: String? = null
    ): NewsLikeStatusDto

    @POST("news/{newsId}/likes/toggle")
    suspend fun toggleNewsLike(
        @Path("newsId") newsId: String,
        @Body request: LikeToggleRequest
    ): LikeToggleResponse

    @POST("news/comments/{commentId}/likes/toggle")
    suspend fun toggleCommentLike(
        @Path("commentId") commentId: String,
        @Body request: LikeToggleRequest
    ): LikeToggleResponse

    @GET("notifications")
    suspend fun getNotifications(
        @Query("userId") userId: String,
        @Query("sinceId") sinceId: Long? = null,
        @Query("limit") limit: Int? = null
    ): List<NotificationDto>

    @POST("notifications/read-all")
    suspend fun readAllNotifications(@Body request: ReadAllNotificationsRequest): SimpleMessageResponse

    /**
     * 拉取二手商品列表
     */
    @GET("market")
    suspend fun getMarketItems(
        @Query("userId") userId: String? = null
    ): PagedResponse<MarketDto>

    @POST("market/{itemId}/favorites/toggle")
    suspend fun toggleMarketFavorite(
        @Path("itemId") itemId: String,
        @Body request: FavoriteToggleRequest
    ): FavoriteToggleResponse

    /**
     * 发布二手商品（当前为 JSON 形式；若包含图片改为 Multipart）
     */
    @POST("market")
    suspend fun publishMarketItem(@Body item: MarketDto): MarketDto

    /**
     * 拉取失物招领列表
     */
    @GET("lostfound")
    suspend fun getLostFoundItems(): PagedResponse<LostFoundDto>

    /**
     * 发布失物招领（当前为 JSON 形式；若包含图片改为 Multipart）
     */
    @POST("lostfound")
    suspend fun publishLostFoundItem(@Body item: LostFoundDto): LostFoundDto

    @GET("admin/reviews/pending")
    suspend fun getPendingReviews(@Query("adminId") adminId: String): List<AdminPendingReviewDto>

    @POST("admin/reviews/action")
    suspend fun reviewPendingItem(@Body request: AdminReviewActionRequest): SimpleMessageResponse

    @POST("admin/comments/{commentId}/ban")
    suspend fun banComment(
        @Path("commentId") commentId: String,
        @Body request: BanCommentRequest
    ): SimpleMessageResponse
}
