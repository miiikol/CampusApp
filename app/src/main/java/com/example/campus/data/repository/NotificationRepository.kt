package com.example.campus.data.repository

import com.example.campus.core.common.Resource
import com.example.campus.data.local.dao.NotificationDao
import com.example.campus.data.local.entity.NotificationEntity
import com.example.campus.data.remote.ApiService
import com.example.campus.data.remote.dto.ReadAllNotificationsRequest
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * 通知数据仓库，管理通知的本地缓存与远端增量和全量同步。
 *
 * 设计要点：
 * - 采用增量同步策略（通过本地 MAX(id) 作为 sinceId），减少数据传输
 * - [unreadCount] 以 Flow 形式暴露未读数，方便 UI 层实时绑定红点/角标
 * - [readAll] 先本地标记已读，再异步通知远端（失败不影响本地状态）
 */
class NotificationRepository @Inject constructor(
    private val api: ApiService,
    private val dao: NotificationDao
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

    fun notifications(userId: String): Flow<List<NotificationEntity>> = dao.getNotifications(userId)

    fun unreadCount(userId: String): Flow<Int> = dao.getUnreadCount(userId)

    suspend fun unreadCountNow(userId: String): Int = dao.getUnreadCountNow(userId)

    /**
     * 增量同步通知：以本地最大通知 ID 作为 sinceId，仅拉取新增通知。
     *
     * 成功时将新通知追加到本地缓存；失败时返回 [Resource.Error]，
     * 不影响已有本地缓存。
     */
    suspend fun sync(userId: String): Resource<Unit> {
        return try {
            val sinceId = dao.getMaxId(userId) ?: 0L
            val remote = api.getNotifications(userId = userId, sinceId = sinceId, limit = 200)
            if (remote.isNotEmpty()) {
                val mapped = remote.mapNotNull { n ->
                    // 过滤掉 id 无法转为数字的脏数据，避免破坏本地主键
                    val idLong = n.id.toLongOrNull() ?: return@mapNotNull null
                    NotificationEntity(
                        id = idLong,
                        userId = n.userId,
                        type = n.type,
                        title = n.title,
                        content = n.content,
                        relatedType = n.relatedType,
                        relatedId = n.relatedId,
                        isRead = n.isRead,
                        createdAt = n.createdAt
                    )
                }
                if (mapped.isNotEmpty()) dao.insertAll(mapped)
            }
            Resource.Success(Unit)
        } catch (e: HttpException) {
            Resource.Error(toHttpErrorMessage(e))
        } catch (e: IOException) {
            Resource.Error("无法连接服务器，请确认后端已启动")
        } catch (e: Exception) {
            Resource.Error("同步通知失败")
        }
    }

    /**
     * 一键标记全部已读。
     *
     * 先本地标记已读保证 UI 立即生效，再异步通知远端；
     * 远端同步失败不影响本地已读状态。
     */
    suspend fun readAll(userId: String): Resource<Unit> {
        return try {
            val unread = dao.getUnreadCountNow(userId)
            if (unread <= 0) return Resource.Success(Unit)

            dao.markAllRead(userId)
            try {
                api.readAllNotifications(ReadAllNotificationsRequest(userId))
            } catch (_: Exception) {
                // 远端同步失败忽略，保留本地已读状态
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("标记已读失败")
        }
    }
}
