package com.example.campus.testing

import com.example.campus.data.local.dao.NotificationDao
import com.example.campus.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * NotificationDao 的测试替身（Fake），
 * 模拟通知数据存储与增量插入（去重基于 ID）。
 */
class FakeNotificationDao : NotificationDao {
    private val state = MutableStateFlow<List<NotificationEntity>>(emptyList())

    fun snapshot(): List<NotificationEntity> = state.value

    override fun getNotifications(userId: String): Flow<List<NotificationEntity>> {
        return state.map { it.filter { n -> n.userId == userId } }
    }

    override fun getUnreadCount(userId: String): Flow<Int> {
        return state.map { it.count { n -> n.userId == userId && !n.isRead } }
    }

    override suspend fun getUnreadCountNow(userId: String): Int {
        return state.value.count { it.userId == userId && !it.isRead }
    }

    override suspend fun getMaxId(userId: String): Long? {
        return state.value.filter { it.userId == userId }.maxOfOrNull { it.id }
    }

    override suspend fun insertAll(items: List<NotificationEntity>) {
        val existingIds = state.value.map { it.id }.toSet()
        val newItems = items.filter { it.id !in existingIds }
        state.value = state.value + newItems
    }

    override suspend fun markAllRead(userId: String) {
        state.value = state.value.map {
            if (it.userId == userId && !it.isRead) it.copy(isRead = true) else it
        }
    }

    override suspend fun clearUserNotifications(userId: String) {
        state.value = state.value.filter { it.userId != userId }
    }
}
