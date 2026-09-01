package com.example.campus.data.remote.dto

import com.example.campus.data.local.entity.MarketEntity

/**
 * 二手商品网络 DTO。
 * 通过 [toEntity] 转换时，可传入本地收藏状态以覆盖远端数据。
 */
data class MarketDto(
    val id: String,
    val title: String,
    val description: String,
    val price: Double,
    val sellerId: String,
    val imageUrl: String?,
    val publishTime: Long,
    val isFavorite: Boolean = false,
    val status: String? = null // 审核状态，如 pending / approved / rejected
)

/** 转换为 Room 实体，[isFavorite] 参数用于合并本地收藏状态 */
fun MarketDto.toEntity(isFavorite: Boolean = false): MarketEntity {
    return MarketEntity(
        id = id,
        title = title,
        description = description,
        price = price,
        sellerId = sellerId,
        imageUrl = imageUrl,
        publishTime = publishTime,
        isFavorite = this.isFavorite || isFavorite,
        status = status
    )
}
