package com.example.campus.data.repository

import android.content.Context
import com.example.campus.core.common.Resource
import com.example.campus.data.local.dao.LostFoundDao
import com.example.campus.data.local.entity.LostFoundEntity
import com.example.campus.data.remote.ApiService
import com.example.campus.data.remote.dto.LostFoundDto
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
 * 失物招领数据仓库，管理失物招领记录的本地缓存与远端同步。
 *
 * 设计说明：
 * - 列表缓存到本地 Room，按发布时间排序
 * - 发布时若含本地图片，先上传图片获取 URL 再提交
 * - 发布后需管理员审核（状态为 APPROVED 才可见）
 */
class LostFoundRepository @Inject constructor(
    private val api: ApiService,
    private val dao: LostFoundDao,
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

    val lostFoundItems: Flow<List<LostFoundEntity>> = dao.getAllItems()

    /**
     * 从远端刷新失物招领列表并覆盖本地缓存。
     *
     * 失败时返回 [Resource.Error]，保留现有本地缓存不变。
     */
    suspend fun refreshItems(): Resource<Unit> {
        return try {
            val remoteItems = api.getLostFoundItems().data
            dao.clearItems()
            dao.insertItems(remoteItems.map { it.toEntity() })
            Resource.Success(Unit)
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: IOException) {
            Resource.Error("无法连接服务器，请确认 WampServer 已启动，且模拟器可访问 10.0.2.2")
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage?.takeIf { it.isNotBlank() } ?: "加载失物招领失败")
        }
    }

    /**
     * 发布失物招领信息。
     *
     * 图片为本地路径时先上传换取远端 URL，再提交；
     * 后端返回 APPROVED 才写入本地缓存，否则视为待审核。
     */
    suspend fun publishItem(item: LostFoundDto): Resource<LostFoundEntity> {
        return try {
            // 本地图片（content/file 协议）需先上传，远端 URL 直接复用
            val finalItem = if (item.imageUrl?.startsWith("content://", ignoreCase = true) == true ||
                item.imageUrl?.startsWith("file://", ignoreCase = true) == true
            ) {
                val uploaded = uploadImage(item.imageUrl)
                item.copy(imageUrl = uploaded)
            } else {
                item
            }

            val response = api.publishLostFoundItem(finalItem)
            if (response.status.equals("APPROVED", ignoreCase = true)) {
                // 审核通过才入库；否则仅提示待审核，不污染列表缓存
                dao.insertItems(listOf(response.toEntity()))
                Resource.Success(response.toEntity())
            } else {
                Resource.Error("已提交审核，管理员通过后才会在列表展示")
            }
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: IOException) {
            Resource.Error("无法连接服务器，请确认 WampServer 已启动，且模拟器可访问 10.0.2.2")
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage?.takeIf { it.isNotBlank() } ?: "发布失败")
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
