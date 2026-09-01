package com.example.campus.testing

import com.example.campus.data.local.dao.MarketDao
import com.example.campus.data.local.entity.MarketEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * MarketDao 的测试替身（Fake），
 * 模拟商品数据存储，支持通过 [setFavorites] 预设收藏状态用于测试合并逻辑。
 */
class FakeMarketDao(
    initial: List<MarketEntity> = emptyList()
) : MarketDao {
    private val state = MutableStateFlow(initial.sortedByDescending { it.publishTime })
    private var favorites: Set<String> = emptySet()
    private var userId: String = ""

    fun snapshot(): List<MarketEntity> = state.value

    // 测试辅助：预设某用户的收藏 id 集合，用于验证收藏标记合并逻辑
    fun setFavorites(ids: Set<String>, uid: String) {
        favorites = ids
        userId = uid
    }

    override fun getAllItems(userId: String): Flow<List<MarketEntity>> {
        // 查询时按预设收藏集合为商品打上收藏标记，并保持倒序
        return state.map { list ->
            list.map { item ->
                if (favorites.contains(item.id)) item.copy(isFavorite = true) else item
            }.sortedByDescending { it.publishTime }
        }
    }

    override fun getItemById(userId: String, id: String): Flow<MarketEntity?> {
        return state.map { list ->
            val item = list.firstOrNull { it.id == id }
            item?.let {
                if (favorites.contains(it.id)) it.copy(isFavorite = true) else it
            }
        }
    }

    override fun getFavoriteItems(userId: String): Flow<List<MarketEntity>> {
        return state.map { list ->
            list.filter { favorites.contains(it.id) }
                .map { it.copy(isFavorite = true) }
                .sortedByDescending { it.publishTime }
        }
    }

    override suspend fun insertItems(items: List<MarketEntity>) {
        state.value = items.sortedByDescending { it.publishTime }
    }

    override suspend fun clearItems() {
        state.value = emptyList()
    }
}
