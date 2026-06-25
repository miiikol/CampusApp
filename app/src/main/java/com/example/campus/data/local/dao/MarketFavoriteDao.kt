package com.example.campus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.campus.data.local.entity.MarketFavoriteEntity

/**
 * 二手收藏表（market_favorites）的数据访问接口。
 *
 * 支持单个/批量插入收藏、删除收藏、清空用户收藏以及获取用户已收藏商品 ID 列表，
 * 用于与远端数据合并时标注收藏状态。
 */
@Dao
interface MarketFavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: MarketFavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorites(favorites: List<MarketFavoriteEntity>)

    @Query("DELETE FROM market_favorites WHERE userId = :userId AND itemId = :itemId")
    suspend fun deleteFavorite(userId: String, itemId: String)

    @Query("DELETE FROM market_favorites WHERE userId = :userId")
    suspend fun clearUserFavorites(userId: String)

    @Query("SELECT itemId FROM market_favorites WHERE userId = :userId")
    suspend fun getFavoriteIds(userId: String): List<String>
}
