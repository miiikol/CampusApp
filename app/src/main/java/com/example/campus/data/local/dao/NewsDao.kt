package com.example.campus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.campus.data.local.entity.NewsEntity
import kotlinx.coroutines.flow.Flow

/**
 * 资讯表（news）的数据访问接口。
 *
 * 说明：
 * - 默认按发布时间倒序返回，适用于列表页面
 * - 收藏状态为本地字段更新，不依赖远端接口
 */
@Dao
interface NewsDao {
    /**
     * 获取全部资讯（按发布时间倒序）。
     */
    @Query("SELECT * FROM news ORDER BY publishDate DESC")
    fun getAllNews(): Flow<List<NewsEntity>>

    @Query("SELECT * FROM news WHERE id = :id LIMIT 1")
    fun getNewsById(id: String): Flow<NewsEntity?>

    /**
     * 获取指定类型的资讯（按发布时间倒序）。
     */
    @Query("SELECT * FROM news WHERE type = :type COLLATE NOCASE ORDER BY publishDate DESC")
    fun getNewsByType(type: String): Flow<List<NewsEntity>>

    /**
     * 批量插入/更新资讯列表。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNews(news: List<NewsEntity>)

    /**
     * 更新单条资讯的收藏状态（本地）。
     */
    @Query("UPDATE news SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    /**
     * 清空资讯表，一般用于刷新前清理旧缓存。
     */
    @Query("DELETE FROM news")
    suspend fun clearNews()
}
