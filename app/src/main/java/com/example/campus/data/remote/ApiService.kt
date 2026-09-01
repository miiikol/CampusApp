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

    /** 重置密码 */
    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): SimpleMessageResponse

    /** 更新用户资料 */
    @POST("users/{userId}/profile")
    suspend fun updateUserProfile(
        @Path("userId") userId: String,
        @Body request: UpdateProfileRequest
    ): UserDto

    /** 上传图片（multipart/form-data），返回图片访问地址 */
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

    /** 自定义课程（覆盖整学期或指定周） */
    @POST("courses/customize")
    suspend fun customizeCourse(
        @Body request: CustomizeCourseRequest
    ): SimpleMessageResponse

    /**
     * 拉取资讯列表
     */
    @GET("news")
    suspend fun getNews(
        @Query("userId") userId: String? = null,
        @Query("pageSize") pageSize: Int = 200
    ): PagedResponse<NewsDto>

    /**
     * 管理员发布资讯
     */
    @POST("news")
    suspend fun publishNews(@Body request: CreateNewsRequest): NewsDto

    /** 切换资讯收藏状态 */
    @POST("news/{newsId}/favorites/toggle")
    suspend fun toggleNewsFavorite(
        @Path("newsId") newsId: String,
        @Body request: FavoriteToggleRequest
    ): FavoriteToggleResponse

    /** 分页拉取某条资讯的评论列表 */
    @GET("news/{newsId}/comments")
    suspend fun getNewsComments(
        @Path("newsId") newsId: String,
        @Query("userId") userId: String? = null,
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null
    ): CommentListResponse

    /** 发表资讯评论（支持二级回复） */
    @POST("news/{newsId}/comments")
    suspend fun createNewsComment(
        @Path("newsId") newsId: String,
        @Body request: CreateCommentRequest
    ): CommentDto

    /** 获取资讯点赞数与当前用户点赞状态 */
    @GET("news/{newsId}/likes")
    suspend fun getNewsLikes(
        @Path("newsId") newsId: String,
        @Query("userId") userId: String? = null
    ): NewsLikeStatusDto

    /** 切换资讯点赞状态 */
    @POST("news/{newsId}/likes/toggle")
    suspend fun toggleNewsLike(
        @Path("newsId") newsId: String,
        @Body request: LikeToggleRequest
    ): LikeToggleResponse

    /** 切换评论点赞状态 */
    @POST("news/comments/{commentId}/likes/toggle")
    suspend fun toggleCommentLike(
        @Path("commentId") commentId: String,
        @Body request: LikeToggleRequest
    ): LikeToggleResponse

    /** 拉取用户通知列表（支持增量拉取与数量限制） */
    @GET("notifications")
    suspend fun getNotifications(
        @Query("userId") userId: String,
        @Query("sinceId") sinceId: Long? = null,
        @Query("limit") limit: Int? = null
    ): List<NotificationDto>

    /** 将用户全部通知标记为已读 */
    @POST("notifications/read-all")
    suspend fun readAllNotifications(@Body request: ReadAllNotificationsRequest): SimpleMessageResponse

    /**
     * 拉取二手商品列表
     */
    @GET("market")
    suspend fun getMarketItems(
        @Query("userId") userId: String? = null,
        @Query("pageSize") pageSize: Int = 200
    ): PagedResponse<MarketDto>

    /** 切换二手商品收藏状态 */
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
    suspend fun getLostFoundItems(
        @Query("pageSize") pageSize: Int = 200
    ): PagedResponse<LostFoundDto>

    /**
     * 发布失物招领（当前为 JSON 形式；若包含图片改为 Multipart）
     */
    @POST("lostfound")
    suspend fun publishLostFoundItem(@Body item: LostFoundDto): LostFoundDto

    /** 拉取待审核内容列表 */
    @GET("admin/reviews/pending")
    suspend fun getPendingReviews(@Query("adminId") adminId: String): List<AdminPendingReviewDto>

    /** 对待审核内容执行通过/驳回操作 */
    @POST("admin/reviews/action")
    suspend fun reviewPendingItem(@Body request: AdminReviewActionRequest): SimpleMessageResponse

    /** 管理员屏蔽评论 */
    @POST("admin/comments/{commentId}/ban")
    suspend fun banComment(
        @Path("commentId") commentId: String,
        @Body request: BanCommentRequest
    ): SimpleMessageResponse
}
