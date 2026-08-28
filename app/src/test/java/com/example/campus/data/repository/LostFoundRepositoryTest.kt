package com.example.campus.data.repository

import android.content.Context
import com.example.campus.core.common.Resource
import com.example.campus.data.local.entity.LostFoundEntity
import com.example.campus.data.remote.ApiService
import com.example.campus.data.remote.dto.LostFoundDto
import com.example.campus.data.remote.dto.PagedResponse
import com.example.campus.testing.FakeLostFoundDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LostFoundRepositoryTest {

    @Test
    fun refreshItems_success_overwritesCacheAndReturnsSuccess() = runTest {
        val cached = listOf(
            LostFoundEntity(
                id = "old", title = "Old", description = "Old", location = "Old",
                type = "LOST", imageUrl = null, contactInfo = null, publishTime = 1L
            )
        )
        val dao = FakeLostFoundDao(cached)
        val api = mockk<ApiService>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        coEvery { api.getLostFoundItems() } returns PagedResponse(
            data = listOf(
                LostFoundDto(
                    id = "lf1", title = "T1", description = "D1", location = "L1",
                    type = "LOST", imageUrl = null, contactInfo = null,
                    publishTime = 10L, latitude = null, longitude = null
                ),
                LostFoundDto(
                    id = "lf2", title = "T2", description = "D2", location = "L2",
                    type = "FOUND", imageUrl = null, contactInfo = null,
                    publishTime = 20L, latitude = null, longitude = null
                )
            ),
            page = 1,
            pageSize = 20,
            total = 2
        )
        val repo = LostFoundRepository(api = api, dao = dao, context = context)

        val result = repo.refreshItems()

        assertTrue(result is Resource.Success)
        val after = dao.snapshot()
        assertEquals(2, after.size)
        assertEquals(listOf("lf2", "lf1"), after.map { it.id })
    }

    @Test
    fun refreshItems_ioException_keepsCacheAndReturnsError() = runTest {
        val cached = listOf(
            LostFoundEntity(
                id = "old", title = "Old", description = "Old", location = "Old",
                type = "LOST", imageUrl = null, contactInfo = null, publishTime = 1L
            )
        )
        val dao = FakeLostFoundDao(cached)
        val api = mockk<ApiService>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        coEvery { api.getLostFoundItems() } throws IOException("network error")
        val repo = LostFoundRepository(api = api, dao = dao, context = context)

        val result = repo.refreshItems()

        assertTrue(result is Resource.Error)
        val after = dao.snapshot()
        assertEquals(1, after.size)
        assertEquals("old", after.single().id)
    }
}
