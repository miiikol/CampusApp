package com.example.campus.testing

import com.example.campus.data.local.dao.LostFoundDao
import com.example.campus.data.local.entity.LostFoundEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * LostFoundDao 的测试替身（Fake），基于内存 [MutableStateFlow] 模拟失物招领数据存储。
 */
class FakeLostFoundDao(
    initial: List<LostFoundEntity> = emptyList()
) : LostFoundDao {
    private val state = MutableStateFlow(initial.sortedByDescending { it.publishTime })

    fun snapshot(): List<LostFoundEntity> = state.value

    override fun getAllItems(): Flow<List<LostFoundEntity>> = state

    override fun getItemsByType(type: String): Flow<List<LostFoundEntity>> {
        return state.map { it.filter { e -> e.type == type } }
    }

    override suspend fun insertItems(items: List<LostFoundEntity>) {
        state.value = items.sortedByDescending { it.publishTime }
    }

    override suspend fun clearItems() {
        state.value = emptyList()
    }
}
