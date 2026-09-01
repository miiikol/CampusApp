package com.example.campus.data.remote.dto

import com.example.campus.data.local.entity.LostFoundEntity

/**
 * 失物招领网络 DTO。
 * [type] 取值为 "LOST" 或 "FOUND"，[status] 用于审核状态标识。
 */
data class LostFoundDto(
    val id: String,
    val title: String,
    val description: String,
    val location: String,
    val type: String,
    val imageUrl: String?,
    val contactInfo: String?,
    val ownerId: String? = null,
    val publishTime: Long,
    val latitude: Double?,
    val longitude: Double?,
    val status: String? = null // 审核状态，如 pending / approved / rejected
)

/** 将网络 DTO 转换为 Room 实体 */
fun LostFoundDto.toEntity(): LostFoundEntity {
    return LostFoundEntity(
        id = id,
        title = title,
        description = description,
        location = location,
        type = type,
        imageUrl = imageUrl,
        contactInfo = contactInfo,
        publishTime = publishTime,
        latitude = latitude,
        longitude = longitude
    )
}
