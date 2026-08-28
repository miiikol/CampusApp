package com.example.campus.data.repository

import android.content.Context
import com.example.campus.core.common.Resource
import com.example.campus.core.common.Constants
import com.example.campus.data.local.dao.NewsDao
import com.example.campus.data.local.dao.UserDao
import com.example.campus.data.local.entity.NewsEntity
import com.example.campus.data.remote.ApiService
import com.example.campus.data.remote.dto.BanCommentRequest
import com.example.campus.data.remote.dto.CommentDto
import com.example.campus.data.remote.dto.CreateCommentRequest
import com.example.campus.data.remote.dto.CreateNewsRequest
import com.example.campus.data.remote.dto.FavoriteToggleRequest
import com.example.campus.data.remote.dto.LikeToggleRequest
import com.example.campus.data.remote.dto.LikeToggleResponse
import com.example.campus.data.remote.dto.NewsDto
import com.example.campus.data.remote.dto.NewsLikeStatusDto
import com.example.campus.data.remote.dto.toEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * 资讯数据仓库，提供资讯列表的查询与刷新能力。
 *
 * 设计：
 * - UI 通过 [getNewsByType] 订阅指定类型资讯的本地流
 * - 调用 [refreshNews] 后台刷新远端数据并覆盖本地缓存
 */
class NewsRepository @Inject constructor(
    private val api: ApiService,
    private val dao: NewsDao,
    private val userDao: UserDao,
    @ApplicationContext private val context: Context
) {

    private fun toNetworkErrorMessage(e: IOException, apiName: String): String {
        val base = Constants.BASE_URL
        val hint = "请确认后端已启动，且设备可访问后端地址：$base"
        val detail = e.javaClass.simpleName.takeIf { it.isNotBlank() } ?: "IOException"
        return "$apiName：无法连接服务器($detail)。$hint"
    }

    private fun toHttpErrorMessage(e: HttpException): String {
        val url = e.response()?.raw()?.request?.url?.toString()
        val base = when (e.code()) {
            400 -> "请求参数错误(400)"
            401 -> "未登录或登录已过期(401)"
            403 -> "没有权限访问(403)"
            404 -> "接口不存在(404)，请确认后端路由与 BASE_URL"
            in 500..599 -> "服务器错误(${e.code()})"
            else -> "请求失败(${e.code()})"
        }
        return if (url.isNullOrBlank()) base else "$base：$url"
    }

    fun getAllNews(): Flow<List<NewsEntity>> = dao.getAllNews()

    fun getNewsById(id: String): Flow<NewsEntity?> = dao.getNewsById(id)

    fun getNewsByType(type: String): Flow<List<NewsEntity>> = dao.getNewsByType(type)

    /**
     * 拉取远端资讯并刷新本地缓存。
     *
     * 失败时返回 [Resource.Error] 并保留现有缓存。
     */
    suspend fun refreshNews(): Resource<Unit> {
        return try {
            val currentUserId = userDao.getCurrentUser()?.id?.takeIf { it.isNotBlank() }
            val remoteNews = api.getNews(currentUserId).data
            dao.clearNews()
            dao.insertNews(remoteNews.map { it.toEntity() })
            Resource.Success(Unit)
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: IOException) {
            Resource.Error(toNetworkErrorMessage(e, "加载资讯失败"))
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage?.takeIf { it.isNotBlank() } ?: "加载失败")
        }
    }

    /**
     * 管理员发布资讯，写入后端数据库并刷新本地缓存。
     */
    suspend fun publishNews(
        adminId: String,
        title: String,
        summary: String,
        content: String,
        type: String,
        imageUrl: String? = null
    ): Resource<NewsDto> {
        val effectiveAdminId = adminId.takeIf { it.isNotBlank() }
            ?: userDao.getCurrentUser()?.id?.takeIf { it.isNotBlank() }
            ?: return Resource.Error("未登录，无法发布资讯")
        return try {
            val finalImageUrl = if (imageUrl?.startsWith("content://", ignoreCase = true) == true ||
                imageUrl?.startsWith("file://", ignoreCase = true) == true
            ) {
                uploadImage(imageUrl)
            } else {
                imageUrl?.takeIf { it.isNotBlank() }
            }

            val resp = api.publishNews(
                CreateNewsRequest(
                    adminId = effectiveAdminId,
                    title = title,
                    summary = summary,
                    content = content,
                    type = type,
                    imageUrl = finalImageUrl
                )
            )
            Resource.Success(resp)
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: IOException) {
            Resource.Error(toNetworkErrorMessage(e, "发布资讯失败"))
        } catch (e: Exception) {
            Resource.Error("发布资讯失败")
        }
    }

    private suspend fun uploadImage(uriString: String): String {
        val uri = android.net.Uri.parse(uriString)
        val mimeType = context.contentResolver.getType(uri) ?: "image/*"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("无法读取图片")

        if (bytes.isEmpty()) throw IOException("空文件")
        if (bytes.size > 5 * 1024 * 1024) throw IOException("图片不能超过 5MB")

        val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val filename = "news_upload.jpg"
        val part = MultipartBody.Part.createFormData("file", filename, requestBody)
        return api.uploadImage(part).url
    }

    /**
     * 切换资讯收藏状态（服务器 + 本地缓存）。
     */
    suspend fun toggleFavorite(id: String, isFavorite: Boolean) {
        val userId = userDao.getCurrentUser()?.id?.takeIf { it.isNotBlank() } ?: return
        val response = api.toggleNewsFavorite(id, FavoriteToggleRequest(userId))
        dao.updateFavorite(id, response.favorited)
    }

    suspend fun getComments(newsId: String, viewerUserId: String?): Resource<List<CommentDto>> {
        return try {
            Resource.Success(api.getNewsComments(newsId = newsId, userId = viewerUserId).data)
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: IOException) {
            Resource.Error(toNetworkErrorMessage(e, "加载评论失败"))
        } catch (e: Exception) {
            Resource.Error("加载评论失败")
        }
    }

    suspend fun createComment(
        newsId: String,
        userId: String,
        username: String,
        content: String,
        parentCommentId: String? = null,
        replyToUserId: String? = null,
        replyToUsername: String? = null
    ): Resource<CommentDto> {
        return try {
            val resp = api.createNewsComment(
                newsId = newsId,
                request = CreateCommentRequest(
                    userId = userId,
                    username = username,
                    content = content,
                    parentCommentId = parentCommentId,
                    replyToUserId = replyToUserId,
                    replyToUsername = replyToUsername
                )
            )
            Resource.Success(resp)
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: IOException) {
            Resource.Error(toNetworkErrorMessage(e, "发布评论失败"))
        } catch (e: Exception) {
            Resource.Error("发布评论失败")
        }
    }

    suspend fun getNewsLikes(newsId: String, viewerUserId: String?): Resource<NewsLikeStatusDto> {
        return try {
            Resource.Success(api.getNewsLikes(newsId = newsId, userId = viewerUserId))
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: IOException) {
            Resource.Error(toNetworkErrorMessage(e, "加载点赞信息失败"))
        } catch (e: Exception) {
            Resource.Error("加载点赞信息失败")
        }
    }

    suspend fun toggleNewsLike(newsId: String, userId: String): Resource<LikeToggleResponse> {
        return try {
            Resource.Success(api.toggleNewsLike(newsId = newsId, request = LikeToggleRequest(userId)))
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: IOException) {
            Resource.Error(toNetworkErrorMessage(e, "点赞失败"))
        } catch (e: Exception) {
            Resource.Error("点赞失败")
        }
    }

    suspend fun toggleCommentLike(commentId: String, userId: String): Resource<LikeToggleResponse> {
        return try {
            Resource.Success(api.toggleCommentLike(commentId = commentId, request = LikeToggleRequest(userId)))
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: IOException) {
            Resource.Error(toNetworkErrorMessage(e, "点赞失败"))
        } catch (e: Exception) {
            Resource.Error("点赞失败")
        }
    }

    suspend fun banComment(commentId: String, adminId: String): Resource<String> {
        return try {
            val resp = api.banComment(commentId, BanCommentRequest(adminId = adminId, reason = "违规内容"))
            Resource.Success(resp.message)
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: IOException) {
            Resource.Error(toNetworkErrorMessage(e, "封禁评论失败"))
        } catch (e: Exception) {
            Resource.Error("封禁评论失败")
        }
    }
}
