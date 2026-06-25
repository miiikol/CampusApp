package com.example.campus.data.remote.dto

/**
 * 管理员待审核项 DTO。
 * [itemType] 标识内容类别（如 news/market/lostfound），[itemId] 为待审核内容 ID。
 */
data class AdminPendingReviewDto(
    val itemType: String,
    val itemId: String,
    val title: String,
    val summary: String,
    val submitterId: String,
    val publishTime: Long
)

/**
 * 管理员审核操作请求体。
 * [action] 取值为 "approve"（通过）或 "reject"（驳回）。
 */
data class AdminReviewActionRequest(
    val adminId: String,
    val itemType: String,
    val itemId: String,
    val action: String
)
