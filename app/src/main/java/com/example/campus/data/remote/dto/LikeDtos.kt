package com.example.campus.data.remote.dto

/**
 * 点赞/收藏切换请求体。
 * 通用结构，用于资讯点赞、评论点赞、资讯收藏、二手收藏等接口。
 */
data class LikeToggleRequest(
    val userId: String
)

data class FavoriteToggleRequest(
    val userId: String
)

/**
 * 收藏切换响应。
 */
data class FavoriteToggleResponse(
    val newsId: String? = null,
    val itemId: String? = null,
    val favorited: Boolean
)

/**
 * 点赞切换响应，返回当前点赞状态与点赞总数。
 */
data class LikeToggleResponse(
    val newsId: String? = null,
    val commentId: String? = null,
    val liked: Boolean,
    val likeCount: Int
)

/**
 * 资讯点赞状态 DTO，用于加载页面时获取点赞数及当前用户是否已点赞。
 */
data class NewsLikeStatusDto(
    val newsId: String,
    val likeCount: Int,
    val likedByMe: Boolean
)
