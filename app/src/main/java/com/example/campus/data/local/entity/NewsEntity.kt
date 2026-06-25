package com.example.campus.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 资讯实体，对应本地表 news。
 *
 * 用途：
 * - 缓存资讯列表以提升弱网体验
 * - 维护本地收藏状态 [isFavorite]
 *
 * 字段约定：
 * - [publishDate] 使用时间戳（毫秒）
 * - [type] 用于区分资讯类别，例如 "NEWS" / "NOTICE"
 */
@Entity(tableName = "news")
data class NewsEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val summary: String,
    val content: String,
    val imageUrl: String?,
    val publishDate: Long,
    val type: String, // "NEWS" or "NOTICE"
    val isFavorite: Boolean = false
)
