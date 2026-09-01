package com.example.campus.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 失物招领实体，对应本地表 lost_found_items。
 *
 * 用途：
 * - 缓存失物招领列表，提升弱网与离线浏览体验
 * - 可选保存地理坐标 [latitude]/[longitude] 以支持地图展示/位置选择
 *
 * 字段约定：
 * - [type] 用于区分记录类型，例如 "LOST" / "FOUND"
 * - [publishTime] 使用时间戳（毫秒）
 * - [contactInfo] 为敏感信息，建议仅在用户主动触发时展示
 */
@Entity(tableName = "lost_found_items")
data class LostFoundEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val location: String,
    val type: String, // "LOST"（丢失）或 "FOUND"（拾到）
    val imageUrl: String?,
    val contactInfo: String?,
    val publishTime: Long,
    val latitude: Double? = null,
    val longitude: Double? = null
)
