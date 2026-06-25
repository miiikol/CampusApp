package com.example.campus.core.common

/**
 * 通用资源封装类，用于表示网络请求或数据加载的三种状态：
 * - [Success] 成功，携带数据
 * - [Error] 失败，携带错误信息与可选的缓存数据
 * - [Loading] 加载中，可携带上次缓存数据用于占位展示
 */
sealed class Resource<T>(val data: T? = null, val message: String? = null) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
    class Loading<T>(data: T? = null) : Resource<T>(data)
}
