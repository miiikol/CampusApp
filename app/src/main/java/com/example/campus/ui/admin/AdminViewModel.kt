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

    /** 标记一次因管理员 ID 尚未就绪而被搁置的刷新请求，待用户流就绪后自动补触发 */
    private var pendingRefresh = false

    init {
        // 读取当前登录用户作为管理员 ID，供审核接口使用
        viewModelScope.launch {
            userRepository.getUser().collect { user ->
                _adminId.value = user?.id
                // 用户信息就绪后，若之前有被搁置的刷新请求则立即补触发
                if (pendingRefresh && !user?.id.isNullOrBlank()) {
                    pendingRefresh = false
                    refresh()
                }
            }
        }
    }

    /**
     * 拉取待审核列表。
     *
     * 以管理员 ID 请求后台接口，根据结果更新 [items] 与 [status]。
     * 若管理员 ID 尚未就绪（异步用户流未返回），则暂存本次请求，待就绪后自动执行。
     */
    fun refresh() {
        val adminId = _adminId.value
        if (adminId.isNullOrBlank()) {
            pendingRefresh = true
            return
        }
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

    /**
     * 对指定审核项执行通过/驳回操作。
     *
     * @param itemType 内容类型（二手商品/失物招领）
     * @param itemId   内容 ID
     * @param action   审核动作（APPROVE/REJECT）
     */
    fun review(itemType: String, itemId: String, action: String) {
        val adminId = _adminId.value ?: return
        launch {
            _actionStatus.value = Resource.Loading()
            _actionStatus.value = adminRepository.reviewItem(adminId, itemType, itemId, action)
            // 审核完成后刷新列表以移除已处理项
            refresh()
        }
    }

    /**
     * 重置单次审核操作的状态，避免提示被重复消费。
     */
    fun clearActionStatus() {
        _actionStatus.value = null
    }
}
