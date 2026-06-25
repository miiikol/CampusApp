package com.example.campus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.campus.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用户表（users）的数据访问接口。
 *
 * 约定：
 * - 本项目将“当前登录用户”作为单条记录存储，因此查询时使用 LIMIT 1
 * - 写入使用 REPLACE 策略覆盖旧数据，配合 [clearUser] 实现会话切换
 * - 查询返回 [Flow]，便于 UI 层/Repository 层订阅用户变化
 */
@Dao
interface UserDao {
    /**
     * 获取当前用户（未登录时可能为 null）。
     */
    @Query("SELECT * FROM users LIMIT 1")
    fun getUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    /**
     * 保存/更新当前用户信息。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("UPDATE users SET username = :username WHERE id = :id")
    suspend fun updateUsername(id: String, username: String)

    @Query("UPDATE users SET avatarUrl = :avatarUrl WHERE id = :id")
    suspend fun updateAvatar(id: String, avatarUrl: String?)

    /**
     * 清空用户表，一般用于登出或重新登录前的清理。
     */
    @Query("DELETE FROM users")
    suspend fun clearUser()
}
