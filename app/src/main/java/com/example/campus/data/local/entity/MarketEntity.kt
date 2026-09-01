package com.example.campus.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 二手商品实体，对应本地表 market_items。
 *
 * 用途：
 * - 缓存二手列表以提升滚动与弱网体验
 * - 预留本地收藏状态 [isFavorite]（若后续实现收藏功能）
 *
 * 字段约定：
 * - [publishTime] 使用时间戳（毫秒）
 * - [imageUrl] 为主图 URL（若后续支持多图可扩展为列表/单独表）
 */
@Entity(tableName = "market_items")
data class MarketEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val price: Double,
    val sellerId: String,
    val imageUrl: String?, // 主图 URL
    val publishTime: Long,
    val isFavorite: Boolean = false,
    val status: String? = null // 商品状态（例如上架/已售/下架）
)
