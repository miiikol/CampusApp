package com.example.campus.ui.market

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.campus.core.base.BaseViewModel
import com.example.campus.data.local.entity.MarketEntity
import com.example.campus.data.repository.MarketRepository
import com.example.campus.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 二手商品详情 ViewModel，管理单件商品的展示与收藏切换。
 *
 * 通过 [SavedStateHandle] 获取商品 ID，结合 [UserRepository] 获取用户 ID，
 * 使用 flatMapLatest 实现用户切换时自动刷新商品数据。
 */
@HiltViewModel
class MarketDetailViewModel @Inject constructor(
    private val repository: MarketRepository,
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    private val _item = MutableStateFlow<MarketEntity?>(null)
    val item: StateFlow<MarketEntity?> = _item.asStateFlow()

    private val _currentUserId = MutableStateFlow<String?>(null)

    init {
        val marketId = savedStateHandle.get<String>("marketId").orEmpty()
        if (marketId.isNotBlank()) {
            viewModelScope.launch {
                userRepository.getUser()
                    .map { it?.id }
                    .distinctUntilChanged()
                    .flatMapLatest { uid ->
                        _currentUserId.value = uid
                        repository.getMarketItemById(uid, marketId)
                    }
                    .collect { _item.value = it }
            }
        }
    }

    /** 切换当前商品的收藏状态：取反后提交远端。 */
    fun toggleFavorite() {
        val current = _item.value ?: return
        launch {
            val uid = _currentUserId.value ?: return@launch
            repository.toggleFavorite(uid, current.id)
        }
    }
}
