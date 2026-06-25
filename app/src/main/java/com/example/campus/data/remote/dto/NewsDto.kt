package com.example.campus.data.remote.dto

import com.example.campus.data.local.entity.NewsEntity

/**
 * 资讯网络 DTO，对应后端资讯接口返回的数据。
 */
data class NewsDto(
    val id: String,
    val title: String,
    val summary: String,
    val content: String,
    val imageUrl: String?,
    val publishDate: Long,
    val type: String,
    val isFavorite: Boolean = false
)

/**
 * 发布资讯请求体，管理员使用。
 */
data class CreateNewsRequest(
    val adminId: String,
    val title: String,
    val summary: String,
    val content: String,
    val type: String,
    val imageUrl: String? = null
)

/** 将网络 DTO 转换为 Room 实体 */
fun NewsDto.toEntity(): NewsEntity {
    return NewsEntity(
        id = id,
        title = title,
        summary = summary,
        content = content,
        imageUrl = imageUrl,
        publishDate = publishDate,
        type = type,
        isFavorite = isFavorite
    )
}
