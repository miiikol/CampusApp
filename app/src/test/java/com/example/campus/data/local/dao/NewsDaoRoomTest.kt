package com.example.campus.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.campus.data.local.AppDatabase
import com.example.campus.data.local.entity.NewsEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NewsDaoRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NewsDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.newsDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insert_query_orderAndFavoriteUpdate_workCorrectly() = runTest {
        val items = listOf(
            NewsEntity(
                id = "n1",
                title = "T1",
                summary = "S1",
                content = "C1",
                imageUrl = null,
                publishDate = 10L,
                type = "NEWS"
            ),
            NewsEntity(
                id = "n2",
                title = "T2",
                summary = "S2",
                content = "C2",
                imageUrl = null,
                publishDate = 30L,
                type = "NOTICE"
            ),
            NewsEntity(
                id = "n3",
                title = "T3",
                summary = "S3",
                content = "C3",
                imageUrl = null,
                publishDate = 20L,
                type = "NEWS"
            )
        )

        dao.insertNews(items)

        val all = dao.getAllNews().first()
        assertEquals(listOf("n2", "n3", "n1"), all.map { it.id })

        dao.updateFavorite(id = "n3", isFavorite = true)
        val n3 = dao.getNewsById("n3").first()
        assertTrue(n3?.isFavorite == true)
    }
}
