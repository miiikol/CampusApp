package com.example.campus.ui.news

import androidx.lifecycle.viewModelScope
import com.example.campus.core.base.BaseViewModel
import com.example.campus.core.common.Resource
import com.example.campus.data.local.entity.NewsEntity
import com.example.campus.data.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 资讯列表 ViewModel，管理资讯列表的数据加载、刷新与发布状态。
 *
 * 核心状态：
 * - [news] 本地缓存的资讯列表流，支持按类型过滤
 * - [status] 远端刷新状态（Loading/Success/Error）
 * - [publishStatus] 发布资讯操作状态
 */
@HiltViewModel
class NewsViewModel @Inject constructor(
    private val repository: NewsRepository
) : BaseViewModel() {

    private val _news = MutableStateFlow<List<NewsEntity>>(emptyList())
    val news: StateFlow<List<NewsEntity>> = _news.asStateFlow()

    private val _status = MutableStateFlow<Resource<Unit>>(Resource.Loading())
    val status: StateFlow<Resource<Unit>> = _status.asStateFlow()

    private val _publishStatus = MutableStateFlow<Resource<Unit>?>(null)
    val publishStatus: StateFlow<Resource<Unit>?> = _publishStatus.asStateFlow()

    private var newsJob: Job? = null

    init {
        loadNews()
    }

    fun loadNews(type: String? = null) {
        newsJob?.cancel()
        newsJob = viewModelScope.launch {
            val flow = if (type.isNullOrBlank()) {
                repository.getAllNews()
            } else {
                repository.getNewsByType(type)
            }
            flow.collect { _news.value = it }
        }
        refresh()
    }

    fun refresh() {
        launch {
            _status.value = Resource.Loading()
            _status.value = repository.refreshNews()
        }
    }

    fun publishNews(
        adminId: String,
        title: String,
        summary: String,
        content: String,
        type: String,
        imageUrl: String? = null
    ) {
        launch {
            _publishStatus.value = Resource.Loading()
            val result = repository.publishNews(adminId, title, summary, content, type, imageUrl)
            when (result) {
                is Resource.Success -> {
                    _publishStatus.value = Resource.Success(Unit)
                    refresh()
                }
                is Resource.Error -> {
                    _publishStatus.value = Resource.Error(result.message ?: "发布失败")
                }
                else -> {}
            }
        }
    }

    fun clearPublishStatus() {
        _publishStatus.value = null
    }
}
