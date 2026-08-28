package com.example.campus.data.remote.dto

/**
 * 分页列表响应的通用包装。
 *
 * 后端列表接口（资讯 / 二手 / 失物招领等）统一返回如下结构，
 * 泛型 [T] 表示列表条目类型：
 * { "data": [...], "page": 1, "pageSize": 20, "total": 100 }
 */
data class PagedResponse<T>(
    val data: List<T>,
    val page: Int,
    val pageSize: Int,
    val total: Int
)
