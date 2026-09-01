package com.example.campus.testing

import com.example.campus.data.local.dao.MarketFavoriteDao
import com.example.campus.data.local.entity.MarketFavoriteEntity

/**
 * MarketFavoriteDao 的测试替身（Fake），基于内存 Map 模拟收藏关系存储。
 */
class FakeMarketFavoriteDao : MarketFavoriteDao {
    private val data = mutableMapOf<String, MutableSet<String>>()

    fun getFavorites(userId: String): Set<String> = data[userId].orEmpty()

    override suspend fun insertFavorite(favorite: MarketFavoriteEntity) {
        data.getOrPut(favorite.userId) { mutableSetOf() }.add(favorite.itemId)
    }

    override suspend fun insertFavorites(favorites: List<MarketFavoriteEntity>) {
        favorites.forEach { insertFavorite(it) }
    }

    override suspend fun deleteFavorite(userId: String, itemId: String) {
        data[userId]?.remove(itemId)
    }

    override suspend fun clearUserFavorites(userId: String) {
        data.remove(userId)
    }
}
