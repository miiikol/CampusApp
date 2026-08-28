package com.example.campus.data.repository

import android.content.Context
import com.example.campus.core.common.Resource
import com.example.campus.data.local.dao.MarketFavoriteDao
import com.example.campus.data.local.dao.UserDao
import com.example.campus.data.local.entity.MarketEntity
import com.example.campus.data.local.entity.UserEntity
import com.example.campus.data.remote.ApiService
import com.example.campus.data.remote.dto.MarketDto
import com.example.campus.data.remote.dto.PagedResponse
import com.example.campus.testing.FakeMarketDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MarketRepositoryTest {

    @Test
    fun refreshItems_success_overwritesCacheAndReturnsSuccess() = runTest {
        val cached = listOf(
            MarketEntity(
                id = "old", title = "Old", description = "Old", price = 10.0,
                sellerId = "u1", imageUrl = null, publishTime = 1L
            )
        )
        val dao = FakeMarketDao(cached)
        val api = mockk<ApiService>(relaxed = true)
        val userDao = mockk<UserDao>(relaxed = true)
        val favoriteDao = mockk<MarketFavoriteDao>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        coEvery { userDao.getCurrentUser() } returns null
        coEvery { api.getMarketItems(null) } returns PagedResponse(
            data = listOf(
                MarketDto(id = "m1", title = "T1", description = "D1", price = 20.0,
                    sellerId = "s1", imageUrl = null, publishTime = 10L),
                MarketDto(id = "m2", title = "T2", description = "D2", price = 30.0,
                    sellerId = "s2", imageUrl = null, publishTime = 20L)
            ),
            page = 1,
            pageSize = 20,
            total = 2
        )
        val repo = MarketRepository(api, dao, favoriteDao, userDao, context)

        val result = repo.refreshItems()

        assertTrue(result is Resource.Success)
        val after = dao.snapshot()
        assertEquals(2, after.size)
        assertEquals(listOf("m2", "m1"), after.map { it.id })
    }

    @Test
    fun refreshItems_ioException_keepsCacheAndReturnsError() = runTest {
        val cached = listOf(
            MarketEntity(
                id = "old", title = "Old", description = "Old", price = 10.0,
                sellerId = "u1", imageUrl = null, publishTime = 1L
            )
        )
        val dao = FakeMarketDao(cached)
        val api = mockk<ApiService>(relaxed = true)
        val userDao = mockk<UserDao>(relaxed = true)
        val favoriteDao = mockk<MarketFavoriteDao>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        coEvery { userDao.getCurrentUser() } returns null
        coEvery { api.getMarketItems(null) } throws IOException("network error")
        val repo = MarketRepository(api, dao, favoriteDao, userDao, context)

        val result = repo.refreshItems()

        assertTrue(result is Resource.Error)
        val after = dao.snapshot()
        assertEquals(1, after.size)
        assertEquals("old", after.single().id)
    }

    @Test
    fun refreshItems_withFavorites_syncsToLocalTable() = runTest {
        val dao = FakeMarketDao(emptyList())
        val api = mockk<ApiService>(relaxed = true)
        val userDao = mockk<UserDao>(relaxed = true)
        val favoriteDao = mockk<MarketFavoriteDao>(relaxed = true)
        val context = mockk<Context>(relaxed = true)

        coEvery { userDao.getCurrentUser() } returns UserEntity(
            id = "user1", username = "admin", studentId = "admin01",
            avatarUrl = null, token = "tok", role = "student"
        )
        coEvery { api.getMarketItems("user1") } returns PagedResponse(
            data = listOf(
                MarketDto(id = "m1", title = "T1", description = "D1", price = 20.0,
                    sellerId = "s1", imageUrl = null, publishTime = 10L, isFavorite = true),
                MarketDto(id = "m2", title = "T2", description = "D2", price = 30.0,
                    sellerId = "s2", imageUrl = null, publishTime = 20L, isFavorite = false)
            ),
            page = 1,
            pageSize = 20,
            total = 2
        )
        val repo = MarketRepository(api, dao, favoriteDao, userDao, context)
        val result = repo.refreshItems()

        assertTrue(result is Resource.Success)
        assertEquals(2, dao.snapshot().size)
    }

    @Test
    fun publishItem_approved_writesToLocalDb() = runTest {
        val dao = FakeMarketDao(emptyList())
        val api = mockk<ApiService>(relaxed = true)
        val userDao = mockk<UserDao>(relaxed = true)
        val favoriteDao = mockk<MarketFavoriteDao>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val dto = MarketDto(
            id = "new1", title = "New", description = "Desc", price = 50.0,
            sellerId = "u1", imageUrl = null, publishTime = 100L
        )

        val responseDto = MarketDto(
            id = "new1", title = "New", description = "Desc", price = 50.0,
            sellerId = "u1", imageUrl = null, publishTime = 100L, status = "APPROVED"
        )
        coEvery { api.publishMarketItem(dto) } returns responseDto

        val repo = MarketRepository(api, dao, favoriteDao, userDao, context)
        val result = repo.publishItem(dto)

        assertTrue(result is Resource.Success)
        val saved = (result as Resource.Success).data
        assertNotNull(saved)
        assertEquals("new1", saved?.id)
    }
}
