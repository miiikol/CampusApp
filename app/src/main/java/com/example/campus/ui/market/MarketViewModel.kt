package com.example.campus.ui.market

import androidx.lifecycle.viewModelScope
import com.example.campus.core.base.BaseViewModel
import com.example.campus.core.common.Resource
import com.example.campus.data.local.entity.MarketEntity
import com.example.campus.data.remote.dto.MarketDto
import com.example.campus.data.repository.MarketRepository
import com.example.campus.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * 二手市场列表 ViewModel，管理商品列表的加载、刷新与发布。
 *
 * 核心状态：
 * - [items] 本地缓存的商品列表，自动跟随用户 ID 切换数据源
 * - [status] 远端刷新状态
 * - [publishStatus] 发布商品操作的状态跟踪
 */
@HiltViewModel
class MarketViewModel @Inject constructor(
    private val repository: MarketRepository,
    private val userRepository: UserRepository
) : BaseViewModel() {

    private val _items = MutableStateFlow<List<MarketEntity>>(emptyList())
    val items: StateFlow<List<MarketEntity>> = _items.asStateFlow()

    private val _status = MutableStateFlow<Resource<Unit>>(Resource.Loading())
    val status: StateFlow<Resource<Unit>> = _status.asStateFlow()

    private val _publishStatus = MutableStateFlow<Resource<MarketEntity>?>(null)
    val publishStatus: StateFlow<Resource<MarketEntity>?> = _publishStatus.asStateFlow()

    private val _currentUserId = MutableStateFlow<String?>(null)

    init {
        // 监听当前用户，用户切换时自动切换到其名下的商品数据源
        viewModelScope.launch {
            userRepository.getUser()
                .map { it?.id }
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    _currentUserId.value = uid
                    repository.marketItems(uid)
                }
                .collect { _items.value = it }
        }
        // 启动时立即从远端刷新一次
        refresh()
    }

    /** 从远端刷新商品列表，结果通过 [status] 暴露给 UI。 */
    fun refresh() {
        launch {
            _status.value = Resource.Loading()
            _status.value = repository.refreshItems()
        }
    }

    /** 发布一条二手商品：生成唯一 ID，并以当前用户作为卖家提交远端。 */
    fun publish(
        title: String,
        description: String,
        price: Double,
        imageUrl: String?
    ) {
        val sellerId = _currentUserId.value ?: "anonymous"
        val dto = MarketDto(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            price = price,
            sellerId = sellerId,
            imageUrl = imageUrl?.trim()?.takeIf { it.isNotBlank() },
            publishTime = System.currentTimeMillis()
        )
        launch {
            _publishStatus.value = Resource.Loading()
            _publishStatus.value = repository.publishItem(dto)
        }
    }

    /** 重置发布状态，供 UI 消费完一次发布结果后调用。 */
    fun clearPublishStatus() {
        _publishStatus.value = null
    }
}
