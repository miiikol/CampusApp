package com.example.campus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.campus.data.local.entity.LostFoundEntity
import kotlinx.coroutines.flow.Flow

/**
 * 失物招领表（lost_found_items）的数据访问接口。
 *
 * 说明：
 * - 按发布时间倒序查询，适用于列表页
 * - 支持按类型（例如 “lost”/“found”）进行过滤
 */
@Dao
interface LostFoundDao {
    /**
     * 获取全部失物招领记录（按发布时间倒序）。
     */
    @Query("SELECT * FROM lost_found_items ORDER BY publishTime DESC")
    fun getAllItems(): Flow<List<LostFoundEntity>>

    /**
     * 按类型获取失物招领记录（按发布时间倒序）。
     */
    @Query("SELECT * FROM lost_found_items WHERE type = :type ORDER BY publishTime DESC")
    fun getItemsByType(type: String): Flow<List<LostFoundEntity>>

    /**
     * 批量插入/更新失物招领记录。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<LostFoundEntity>)

    /**
     * 清空失物招领表，一般用于刷新前清理旧缓存。
     */
    @Query("DELETE FROM lost_found_items")
    suspend fun clearItems()
}
