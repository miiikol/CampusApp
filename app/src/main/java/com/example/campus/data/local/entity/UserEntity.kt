package com.example.campus.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.campus.core.common.UserRole

/**
 * 用户实体，对应本地表 users。
 *
 * 设计约定：
 * - 本项目通常只保留“当前登录用户”的一条记录（配合 UserDao 的 LIMIT 1 查询）
 * - [token] 属于敏感信息，建议仅用于本地鉴权与请求头注入，不应在日志中输出
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val username: String,
    val studentId: String,
    val avatarUrl: String?,
    val token: String?,
    val role: String = UserRole.STUDENT
)
