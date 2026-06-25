package com.example.campus.testing

import com.example.campus.data.local.dao.NewsDao
import com.example.campus.data.local.entity.NewsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * NewsDao 的测试替身（Fake），基于内存 [MutableStateFlow] 模拟数据库操作。
 *
 * 用于单元测试中替代 Room DAO，避免 Android 设备依赖。
 */
class FakeNewsDao(
    initial: List<NewsEntity> = emptyList()
) : NewsDao {
    private val state = MutableStateFlow(initial.sortedByDescending { it.publishDate })

    fun snapshot(): List<NewsEntity> = state.value

    override fun getAllNews(): Flow<List<NewsEntity>> = state

    override fun getNewsById(id: String): Flow<NewsEntity?> {
        return state.map { list -> list.firstOrNull { it.id == id } }
    }

    override fun getNewsByType(type: String): Flow<List<NewsEntity>> {
        return state.map { list ->
            list.filter { it.type.equals(type, ignoreCase = true) }
                .sortedByDescending { it.publishDate }
        }
    }

    override suspend fun insertNews(news: List<NewsEntity>) {
        state.value = news.sortedByDescending { it.publishDate }
    }

    override suspend fun updateFavorite(id: String, isFavorite: Boolean) {
        state.value = state.value.map { item ->
            if (item.id == id) item.copy(isFavorite = isFavorite) else item
        }
    }

    override suspend fun clearNews() {
        state.value = emptyList()
    }
}
