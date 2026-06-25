package com.example.campus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.campus.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

/**
 * 通知表（notifications_local）的数据访问接口。
 *
 * 负责通知的本地缓存管理：列表查询、未读计数、增量同步（通过 MAX(id) 实现）、
 * 批量标记已读及清空操作。
 */
@Dao
interface NotificationDao {
    /** 按用户查询通知列表，最新在前 */
    @Query("SELECT * FROM notifications_local WHERE userId = :userId ORDER BY id DESC")
    fun getNotifications(userId: String): Flow<List<NotificationEntity>>

    /** 获取未读通知数量，用于底部栏红点/角标展示 */
    @Query("SELECT COUNT(*) FROM notifications_local WHERE userId = :userId AND isRead = 0")
    fun getUnreadCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications_local WHERE userId = :userId AND isRead = 0")
    suspend fun getUnreadCountNow(userId: String): Int

    /** 获取本地已缓存的最大通知 ID，作为增量同步的 sinceId */
    @Query("SELECT MAX(id) FROM notifications_local WHERE userId = :userId")
    suspend fun getMaxId(userId: String): Long?

    /** 批量插入通知，用于远端同步 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<NotificationEntity>)

    /** 标记当前用户所有未读通知为已读 */
    @Query("UPDATE notifications_local SET isRead = 1 WHERE userId = :userId AND isRead = 0")
    suspend fun markAllRead(userId: String)

    /** 清空用户通知缓存 */
    @Query("DELETE FROM notifications_local WHERE userId = :userId")
    suspend fun clearUserNotifications(userId: String)
}
