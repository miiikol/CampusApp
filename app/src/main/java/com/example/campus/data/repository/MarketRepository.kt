package com.example.campus.data.repository

import android.content.Context
import com.example.campus.core.common.Resource
import com.example.campus.data.local.dao.MarketDao
import com.example.campus.data.local.dao.MarketFavoriteDao
import com.example.campus.data.local.dao.UserDao
import com.example.campus.data.local.entity.MarketEntity
import com.example.campus.data.local.entity.MarketFavoriteEntity
import com.example.campus.data.remote.ApiService
import com.example.campus.data.remote.dto.FavoriteToggleRequest
import com.example.campus.data.remote.dto.MarketDto
import com.example.campus.data.remote.dto.toEntity
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.io.IOException
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/**
 * 二手市场数据仓库，管理商品列表的本地缓存、远端同步及收藏功能。
 *
 * 设计说明：
 * - 商品列表缓存到本地 Room，支持离线浏览
 * - 收藏关系存储在本地 market_favorites 表，并与远端双向同步
 * - 发布商品时若含本地图片，先上传图片再提交
 * - 审核状态下非 "APPROVED" 的商品不会展示在列表
 */
class MarketRepository @Inject constructor(
    private val api: ApiService,
    private val dao: MarketDao,
    private val favoriteDao: MarketFavoriteDao,
    private val userDao: UserDao,
    @ApplicationContext private val context: Context
) {

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

    fun marketItems(userId: String?): Flow<List<MarketEntity>> {
        return dao.getAllItems(userId.orEmpty())
    }

    fun getMarketItemById(userId: String?, id: String): Flow<MarketEntity?> {
        return dao.getItemById(userId.orEmpty(), id)
    }

    fun favoriteItems(userId: String): Flow<List<MarketEntity>> = dao.getFavoriteItems(userId)

    suspend fun refreshItems(): Resource<Unit> {
        return try {
            val currentUser = userDao.getCurrentUser()
            val currentUserId = currentUser?.id?.takeIf { it.isNotBlank() }
            val remoteItems = api.getMarketItems(currentUserId).data
            val remoteEntities = remoteItems.map { it.toEntity(isFavorite = false) }
            dao.insertItems(remoteEntities)
            if (!currentUserId.isNullOrBlank()) {
                favoriteDao.clearUserFavorites(currentUserId)
                val favorites = remoteItems
                    .filter { it.isFavorite }
                    .map {
                        MarketFavoriteEntity(
                            userId = currentUserId,
                            itemId = it.id,
                            createdAt = System.currentTimeMillis()
                        )
                    }
                if (favorites.isNotEmpty()) {
                    favoriteDao.insertFavorites(favorites)
                }
            }
            Resource.Success(Unit)
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: IOException) {
            Resource.Error("无法连接服务器，请确认 WampServer 已启动，且模拟器可访问 10.0.2.2")
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage?.takeIf { it.isNotBlank() } ?: "加载商品失败")
        }
    }

    suspend fun toggleFavorite(userId: String, itemId: String, isFavorite: Boolean) {
        if (userId.isBlank() || itemId.isBlank()) return
        val response = api.toggleMarketFavorite(itemId, FavoriteToggleRequest(userId))
        if (response.favorited) {
            favoriteDao.insertFavorite(
                MarketFavoriteEntity(
                    userId = userId,
                    itemId = itemId,
                    createdAt = System.currentTimeMillis()
                )
            )
        } else {
            favoriteDao.deleteFavorite(userId, itemId)
            dao.updateStatus(itemId, null)
        }
    }

    suspend fun publishItem(item: MarketDto): Resource<MarketEntity> {
        return try {
            val finalItem = if (item.imageUrl?.startsWith("content://", ignoreCase = true) == true ||
                item.imageUrl?.startsWith("file://", ignoreCase = true) == true
            ) {
                val uploaded = uploadImage(item.imageUrl)
                item.copy(imageUrl = uploaded)
            } else {
                item
            }

            val response = api.publishMarketItem(finalItem)
            if (response.status.equals("APPROVED", ignoreCase = true)) {
                val entity = response.toEntity()
                dao.insertItems(listOf(entity))
                Resource.Success(entity)
            } else {
                Resource.Error("已提交审核，管理员通过后才会在列表展示")
            }
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: IOException) {
            Resource.Error("无法连接服务器，请确认 WampServer 已启动，且模拟器可访问 10.0.2.2")
        }
    }

    private suspend fun uploadImage(uriString: String?): String {
        val uriRaw = uriString?.trim().orEmpty()
        if (uriRaw.isBlank()) return uriRaw

        val uri = android.net.Uri.parse(uriRaw)
        val mimeType = context.contentResolver.getType(uri) ?: "image/*"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("无法读取图片")

        val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val filename = "upload.jpg"
        val part = MultipartBody.Part.createFormData("file", filename, requestBody)
        return api.uploadImage(part).url
    }
}
