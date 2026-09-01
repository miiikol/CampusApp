package com.example.campus.data.repository

import android.content.Context
import com.example.campus.core.common.Resource
import com.example.campus.data.local.dao.UserDao
import com.example.campus.data.local.entity.NewsEntity
import com.example.campus.data.remote.ApiService
import com.example.campus.data.remote.dto.NewsDto
import com.example.campus.data.remote.dto.PagedResponse
import com.example.campus.testing.FakeNewsDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 针对 [NewsRepository] 刷新逻辑的单元测试：
 * 验证成功刷新覆盖缓存、异常时保留缓存与收藏状态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewsRepositoryTest {

    @Test
    fun refreshNews_success_overwritesCacheAndReturnsSuccess() = runTest {
        val cached = listOf(
            NewsEntity(
                id = "old",
                title = "Old",
                summary = "Old",
                content = "Old",
                imageUrl = null,
                publishDate = 1L,
                type = "NEWS"
            )
        )

        val dao = FakeNewsDao(cached)
        val api = mockk<ApiService>(relaxed = true)
        val userDao = mockk<UserDao>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        coEvery { userDao.getCurrentUser() } returns null
        coEvery { api.getNews(null) } returns PagedResponse(
            data = listOf(
                NewsDto(
                    id = "n1",
                    title = "T1",
                    summary = "S1",
                    content = "C1",
                    imageUrl = null,
                    publishDate = 10L,
                    type = "NEWS"
                ),
                NewsDto(
                    id = "n2",
                    title = "T2",
                    summary = "S2",
                    content = "C2",
                    imageUrl = null,
                    publishDate = 20L,
                    type = "NOTICE"
                )
            ),
            page = 1,
            pageSize = 20,
            total = 2
        )

        val repo = NewsRepository(api = api, dao = dao, userDao = userDao, context = context)
        val result = repo.refreshNews()

        assertTrue(result is Resource.Success)
        // 刷新成功后缓存被两条远程数据覆盖，并按 publishDate 倒序排列
        val after = dao.snapshot()
        assertEquals(listOf("n2", "n1"), after.map { it.id })
    }

    @Test
    fun refreshNews_ioException_keepsCacheAndReturnsError() = runTest {
        val cached = listOf(
            NewsEntity(
                id = "old",
                title = "Old",
                summary = "Old",
                content = "Old",
                imageUrl = null,
                publishDate = 1L,
                type = "NEWS",
                isFavorite = true
            )
        )

        val dao = FakeNewsDao(cached)
        val api = mockk<ApiService>(relaxed = true)
        val userDao = mockk<UserDao>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        coEvery { userDao.getCurrentUser() } returns null
        coEvery { api.getNews(null) } throws IOException("boom")

        val repo = NewsRepository(api = api, dao = dao, userDao = userDao, context = context)
        val result = repo.refreshNews()

        assertTrue(result is Resource.Error)
        // 网络异常时缓存保留，且原有收藏状态不丢失
        val after = dao.snapshot()
        assertEquals("old", after.single().id)
        assertTrue(after.single().isFavorite)
    }
}
