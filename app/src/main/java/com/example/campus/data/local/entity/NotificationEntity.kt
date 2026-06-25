package com.example.campus.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 通知实体，对应本地表 notifications_local。
 *
 * 用途：缓存用户通知列表，离线时仍可查看历史通知。
 * [relatedType]/[relatedId] 用于关联跳转到具体内容（如资讯、评论等）。
 */
@Entity(
    tableName = "notifications_local",
    indices = [
        Index(value = ["userId", "id"])
    ]
)
data class NotificationEntity(
    @PrimaryKey
    val id: Long,
    val userId: String,
    val type: String,
    val title: String,
    val content: String,
    val relatedType: String? = null,
    val relatedId: String? = null,
    val isRead: Boolean,
    val createdAt: Long
)
