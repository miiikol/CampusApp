package com.example.campus.data.remote.dto

/**
 * 评论网络 DTO。
 * 支持二级回复：[parentCommentId] 非空时为子评论，[replyToUserId]/[replyToUsername] 表示回复对象。
 * [status] 取值 "normal"（正常）/ "banned"（已屏蔽）。
 */
data class CommentDto(
    val id: String,
    val newsId: String,
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val content: String,
    val parentCommentId: String? = null,
    val replyToUserId: String? = null,
    val replyToUsername: String? = null,
    val status: String,
    val publishTime: Long,
    val likeCount: Int = 0,
    val likedByMe: Boolean = false
)

/**
 * 创建评论请求体。
 */
data class CreateCommentRequest(
    val userId: String,
    val username: String,
    val content: String,
    val parentCommentId: String? = null,
    val replyToUserId: String? = null,
    val replyToUsername: String? = null
)

/**
 * 管理员屏蔽评论请求体。
 */
data class BanCommentRequest(
    val adminId: String,
    val reason: String
)
