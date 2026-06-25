package com.example.campus.ui.admin

import androidx.lifecycle.viewModelScope
import com.example.campus.core.base.BaseViewModel
import com.example.campus.core.common.Resource
import com.example.campus.data.remote.dto.AdminPendingReviewDto
import com.example.campus.data.repository.AdminRepository
import com.example.campus.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 管理员审核 ViewModel，管理待审核列表的加载与审核操作。
 *
 * 状态流：
 * - [items] 待审核内容列表
 * - [status] 列表刷新状态
 * - [actionStatus] 单次审核操作结果
 */
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    private val userRepository: UserRepository
) : BaseViewModel() {

    private val _adminId = MutableStateFlow<String?>(null)
    private val _items = MutableStateFlow<List<AdminPendingReviewDto>>(emptyList())
    val items: StateFlow<List<AdminPendingReviewDto>> = _items.asStateFlow()

    private val _status = MutableStateFlow<Resource<Unit>>(Resource.Loading())
    val status: StateFlow<Resource<Unit>> = _status.asStateFlow()

    private val _actionStatus = MutableStateFlow<Resource<String>?>(null)
    val actionStatus: StateFlow<Resource<String>?> = _actionStatus.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.getUser().collect { user ->
                _adminId.value = user?.id
            }
        }
    }

    fun refresh() {
        val adminId = _adminId.value ?: return
        launch {
            _status.value = Resource.Loading()
            when (val result = adminRepository.getPendingReviews(adminId)) {
                is Resource.Success -> {
                    _items.value = result.data.orEmpty()
                    _status.value = Resource.Success(Unit)
                }
                is Resource.Error -> {
                    _status.value = Resource.Error(result.message ?: "加载失败")
                }
                else -> Unit
            }
        }
    }

    fun review(itemType: String, itemId: String, action: String) {
        val adminId = _adminId.value ?: return
        launch {
            _actionStatus.value = Resource.Loading()
            _actionStatus.value = adminRepository.reviewItem(adminId, itemType, itemId, action)
            refresh()
        }
    }

    fun clearActionStatus() {
        _actionStatus.value = null
    }
}
