package com.example.campus.data.remote.dto

/**
 * 密码重置请求体，需提供学号、姓名、身份证号和新密码。
 */
data class ResetPasswordRequest(
    val studentId: String,
    val fullName: String,
    val idCardNo: String,
    val newPassword: String
)

/**
 * 通用简单消息响应，后端多数非数据接口返回此结构。
 */
data class SimpleMessageResponse(
    val message: String
)
