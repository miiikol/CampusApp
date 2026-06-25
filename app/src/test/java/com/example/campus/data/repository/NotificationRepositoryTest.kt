package com.example.campus.data.repository

import com.example.campus.core.common.Resource
import com.example.campus.data.local.entity.NotificationEntity
import com.example.campus.data.remote.ApiService
import com.example.campus.data.remote.dto.NotificationDto
import com.example.campus.data.remote.dto.ReadAllNotificationsRequest
import com.example.campus.data.remote.dto.SimpleMessageResponse
import com.example.campus.testing.FakeNotificationDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationRepositoryTest {

    @Test
    fun sync_addsNewNotificationsOnly() = runTest {
        val dao = FakeNotificationDao()
        val api = mockk<ApiService>(relaxed = true)

        coEvery { api.getNotifications("u1", 0L, 200) } returns listOf(
            NotificationDto(
                id = "101", userId = "u1", type = "REVIEW", title = "T1",
                content = "C1", isRead = false, createdAt = 1000L
            ),
            NotificationDto(
                id = "102", userId = "u1", type = "REVIEW", title = "T2",
                content = "C2", isRead = false, createdAt = 2000L
            )
        )
        val repo = NotificationRepository(api, dao)

        val result = repo.sync("u1")

        assertTrue(result is Resource.Success)
        val notifications = dao.snapshot().filter { it.userId == "u1" }
        assertEquals(2, notifications.size)
        assertEquals(listOf(101L, 102L), notifications.map { it.id }.sorted())
    }

    @Test
    fun markAllRead_updatesAllUnreadToRead() = runTest {
        val dao = FakeNotificationDao()
        val api = mockk<ApiService>(relaxed = true)
        coEvery { api.readAllNotifications(ReadAllNotificationsRequest("u1")) } returns
            SimpleMessageResponse("ok")
        dao.insertAll(
            listOf(
                NotificationEntity(
                    id = 1, userId = "u1", type = "REVIEW", title = "T1",
                    content = "C1", isRead = false, createdAt = 1000L
                ),
                NotificationEntity(
                    id = 2, userId = "u1", type = "REVIEW", title = "T2",
                    content = "C2", isRead = false, createdAt = 2000L
                )
            )
        )
        val repo = NotificationRepository(api, dao)

        val result = repo.readAll("u1")

        assertTrue(result is Resource.Success)
        val unreadCount = dao.getUnreadCountNow("u1")
        assertEquals(0, unreadCount)
    }
}
