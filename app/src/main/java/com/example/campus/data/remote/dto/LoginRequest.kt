package com.example.campus.data.remote.dto

/**
 * 登录请求体，使用学号+密码进行身份认证。
 */
data class LoginRequest(
    val studentId: String,
    val password: String
)
