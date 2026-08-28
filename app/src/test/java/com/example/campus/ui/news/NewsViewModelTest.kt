package com.example.campus.ui.news

import android.content.Context
import com.example.campus.core.common.Resource
import com.example.campus.data.local.dao.UserDao
import com.example.campus.data.local.entity.NewsEntity
import com.example.campus.data.remote.ApiService
import com.example.campus.data.remote.dto.NewsDto
import com.example.campus.data.remote.dto.PagedResponse
import com.example.campus.data.repository.NewsRepository
import com.example.campus.testing.FakeNewsDao
import com.example.campus.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NewsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_success_refreshesAndUpdatesNewsAndStatus() = runTest {
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
                )
            ),
            page = 1,
            pageSize = 20,
            total = 1
        )
        val repo = NewsRepository(api = api, dao = dao, userDao = userDao, context = context)

        val vm = NewsViewModel(repository = repo)

        advanceUntilIdle()

        assertEquals(listOf("n1"), vm.news.value.map { it.id })
        assertTrue(vm.status.value is Resource.Success)
    }

    @Test
    fun init_failure_keepsCacheAndSetsErrorStatus() = runTest {
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
        val userDao2 = mockk<UserDao>(relaxed = true)
        val context2 = mockk<Context>(relaxed = true)
        coEvery { userDao2.getCurrentUser() } returns null
        coEvery { api.getNews(null) } throws IOException("boom")
        val repo = NewsRepository(api = api, dao = dao, userDao = userDao2, context = context2)

        val vm = NewsViewModel(repository = repo)

        advanceUntilIdle()

        assertEquals(listOf("old"), vm.news.value.map { it.id })
        assertTrue(vm.status.value is Resource.Error)
    }
}
