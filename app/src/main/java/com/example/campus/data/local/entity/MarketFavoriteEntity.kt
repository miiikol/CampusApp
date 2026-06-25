package com.example.campus.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 二手收藏实体，对应本地表 market_favorites。
 *
 * 以 [userId] + [itemId] 为联合主键，记录用户对二手商品的收藏关系。
 * [createdAt] 用于按收藏时间排序。
 */
@Entity(
    tableName = "market_favorites",
    indices = [
        Index(value = ["userId", "createdAt"])
    ],
    primaryKeys = ["userId", "itemId"]
)
data class MarketFavoriteEntity(
    val userId: String,
    val itemId: String,
    val createdAt: Long
)
