package com.example.campus.data.remote.dto

/**
 * 通知网络 DTO。
 * [relatedType]/[relatedId] 用于关联跳转至具体内容页面。
 */
data class NotificationDto(
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val content: String,
    val relatedType: String? = null,
    val relatedId: String? = null,
    val isRead: Boolean,
    val createdAt: Long
)

/**
 * 全部已读请求体。
 */
data class ReadAllNotificationsRequest(
    val userId: String
)
