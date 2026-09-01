package com.example.campus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.campus.data.local.entity.MarketEntity
import kotlinx.coroutines.flow.Flow

/**
 * 二手市场表（market_items）的数据访问接口。
 *
 * 说明：
 * - 列表按发布时间倒序，用于首页/列表页展示
 * - 数据通常由远端拉取后写入本地作为缓存
 */
@Dao
interface MarketDao {
    /**
     * 获取全部二手商品（按发布时间倒序）。
     */
    @Query(
        """
        SELECT
          m.id,
          m.title,
          m.description,
          m.price,
          m.sellerId,
          m.imageUrl,
          m.publishTime,
          CASE WHEN f.itemId IS NULL THEN 0 ELSE 1 END AS isFavorite,
          m.status
        FROM market_items m
        LEFT JOIN market_favorites f ON f.itemId = m.id AND f.userId = :userId
        ORDER BY m.publishTime DESC
        """
    )
    fun getAllItems(userId: String): Flow<List<MarketEntity>>

    /**
     * 按 id 查询单条二手商品（含当前用户的收藏状态）。
     */
    @Query(
        """
        SELECT
          m.id,
          m.title,
          m.description,
          m.price,
          m.sellerId,
          m.imageUrl,
          m.publishTime,
          CASE WHEN f.itemId IS NULL THEN 0 ELSE 1 END AS isFavorite,
          m.status
        FROM market_items m
        LEFT JOIN market_favorites f ON f.itemId = m.id AND f.userId = :userId
        WHERE m.id = :id
        LIMIT 1
        """
    )
    fun getItemById(userId: String, id: String): Flow<MarketEntity?>

    /**
     * 获取当前用户收藏的二手商品（按收藏时间倒序）。
     */
    @Query(
        """
        SELECT
          m.id,
          m.title,
          m.description,
          m.price,
          m.sellerId,
          m.imageUrl,
          m.publishTime,
          1 AS isFavorite,
          m.status
        FROM market_items m
        INNER JOIN market_favorites f ON f.itemId = m.id
        WHERE f.userId = :userId
        ORDER BY f.createdAt DESC
        """
    )
    fun getFavoriteItems(userId: String): Flow<List<MarketEntity>>

    /**
     * 批量插入/更新二手商品列表。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<MarketEntity>)

    /**
     * 清空二手商品表，一般用于刷新前清理旧缓存。
     */
    @Query("DELETE FROM market_items")
    suspend fun clearItems()
}
