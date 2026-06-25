package com.example.campus.ui.lostfound

import androidx.lifecycle.viewModelScope
import com.example.campus.core.base.BaseViewModel
import com.example.campus.core.common.Resource
import com.example.campus.data.local.entity.LostFoundEntity
import com.example.campus.data.remote.dto.LostFoundDto
import com.example.campus.data.repository.LostFoundRepository
import com.example.campus.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * 失物招领列表 ViewModel，管理失物招领记录的数据加载、刷新与发布。
 *
 * 发布流程：
 * 1. 生成 UUID 作为记录 ID
 * 2. 若含本地图片，由 Repository 层上传并替换为远端 URL
 * 3. 提交后远端审核，通过才在列表可见
 */
@HiltViewModel
class LostFoundViewModel @Inject constructor(
    private val repository: LostFoundRepository,
    private val userRepository: UserRepository
) : BaseViewModel() {

    private val _items = MutableStateFlow<List<LostFoundEntity>>(emptyList())
    val items: StateFlow<List<LostFoundEntity>> = _items.asStateFlow()

    private val _status = MutableStateFlow<Resource<Unit>>(Resource.Loading())
    val status: StateFlow<Resource<Unit>> = _status.asStateFlow()

    private val _publishStatus = MutableStateFlow<Resource<LostFoundEntity>?>(null)
    val publishStatus: StateFlow<Resource<LostFoundEntity>?> = _publishStatus.asStateFlow()

    private val _currentUserId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            repository.lostFoundItems.collect {
                _items.value = it
            }
        }
        viewModelScope.launch {
            userRepository.getUser().collect { user ->
                _currentUserId.value = user?.id
            }
        }
        refresh()
    }

    fun refresh() {
        launch {
            _status.value = Resource.Loading()
            _status.value = repository.refreshItems()
        }
    }

    fun publish(
        title: String,
        description: String,
        location: String,
        type: String,
        contactInfo: String?,
        latitude: Double?,
        longitude: Double?,
        imageUrl: String?
    ) {
        val ownerId = _currentUserId.value ?: "anonymous"
        val dto = LostFoundDto(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            location = location.trim(),
            type = type,
            imageUrl = imageUrl?.trim()?.takeIf { it.isNotBlank() },
            contactInfo = contactInfo?.trim()?.takeIf { it.isNotBlank() } ?: ownerId,
            ownerId = ownerId,
            publishTime = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude
        )
        launch {
            _publishStatus.value = Resource.Loading()
            _publishStatus.value = repository.publishItem(dto)
        }
    }

    fun clearPublishStatus() {
        _publishStatus.value = null
    }
}
