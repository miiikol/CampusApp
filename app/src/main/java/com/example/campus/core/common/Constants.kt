package com.example.campus.core.common

import com.example.campus.BuildConfig

/**
 * 全局常量配置。
 * [BASE_URL] 从 BuildConfig 注入，支持 debug/release 环境区分。
 * [DATABASE_NAME] 为 Room 本地数据库文件名。
 * [API_VERSION] 当前后端 API 版本号，用于向后兼容旧版本。
 */
object Constants {
    val BASE_URL: String = BuildConfig.BASE_URL
    const val DATABASE_NAME = "campus_db"
    const val API_VERSION = 1
}
