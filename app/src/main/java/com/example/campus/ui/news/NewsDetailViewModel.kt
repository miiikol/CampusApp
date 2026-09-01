package com.example.campus.ui.news

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.campus.core.base.BaseViewModel
import com.example.campus.core.common.Resource
import com.example.campus.core.common.UserRole
import com.example.campus.core.common.normalizeRole
import com.example.campus.data.local.entity.NewsEntity
import com.example.campus.data.remote.dto.CommentDto
import com.example.campus.data.remote.dto.NewsLikeStatusDto
import com.example.campus.data.repository.NewsRepository
import com.example.campus.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 资讯详情 ViewModel，管理单条资讯的展示、评论互动与点赞状态。
 *
 * 功能：
 * - 加载资讯详情内容
 * - 支持评论的增删查以及二级回复（通过 [replyTarget] 控制）
 * - 支持资讯点赞/取消、评论点赞/取消
 * - 管理员身份可屏蔽违规评论
 */
@HiltViewModel
class NewsDetailViewModel @Inject constructor(
    private val repository: NewsRepository,
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    private val _news = MutableStateFlow<NewsEntity?>(null)
    val news: StateFlow<NewsEntity?> = _news.asStateFlow()
    private val _comments = MutableStateFlow<List<CommentDto>>(emptyList())
    val comments: StateFlow<List<CommentDto>> = _comments.asStateFlow()
    private val _status = MutableStateFlow<Resource<Unit>>(Resource.Loading())
    val status: StateFlow<Resource<Unit>> = _status.asStateFlow()
    // 评论提交/点赞/屏蔽等一次性操作结果，消费后由 clearActionStatus 重置
    private val _commentActionStatus = MutableStateFlow<Resource<String>?>(null)
    val commentActionStatus: StateFlow<Resource<String>?> = _commentActionStatus.asStateFlow()
    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()
    // 当前回复对象：非空表示正在回复某条评论，控制二级回复
    private val _replyTarget = MutableStateFlow<CommentDto?>(null)
    val replyTarget: StateFlow<CommentDto?> = _replyTarget.asStateFlow()
    // 资讯点赞状态（点赞数 + 是否已赞）
    private val _newsLikeStatus = MutableStateFlow<NewsLikeStatusDto?>(null)
    val newsLikeStatus: StateFlow<NewsLikeStatusDto?> = _newsLikeStatus.asStateFlow()
    private val _userId = MutableStateFlow<String?>(null)
    private val _username = MutableStateFlow<String?>(null)
    private var newsId: String = ""

    init {
        newsId = savedStateHandle.get<String>("newsId").orEmpty()
        if (newsId.isNotBlank()) {
            viewModelScope.launch {
                repository.getNewsById(newsId).collect { _news.value = it }
            }
            // 评论与点赞状态依赖 userId，统一在下方用户流就绪后触发，避免重复请求
        }

        viewModelScope.launch {
            userRepository.getUser().collect { user ->
                _userId.value = user?.id
                _username.value = user?.username
                _isAdmin.value = normalizeRole(user?.role) == UserRole.ADMIN
                refreshComments()
                refreshNewsLikes()
            }
        }
    }

    /** 重新拉取评论列表，并更新加载状态。 */
    fun refreshComments() {
        if (newsId.isBlank()) return
        launch {
            when (val result = repository.getComments(newsId, _userId.value)) {
                is Resource.Success -> {
                    _comments.value = result.data.orEmpty()
                    _status.value = Resource.Success(Unit)
                }
                is Resource.Error -> _status.value = Resource.Error(result.message ?: "加载评论失败")
                else -> Unit
            }
        }
    }

    fun setReplyTarget(comment: CommentDto?) {
        _replyTarget.value = comment
    }

    fun submitComment(content: String) {
        val uid = _userId.value ?: return
        val username = _username.value ?: return
        if (content.isBlank() || newsId.isBlank()) return
        val reply = _replyTarget.value
        launch {
            _commentActionStatus.value = Resource.Loading()
            when (
                val result = repository.createComment(
                    newsId = newsId,
                    userId = uid,
                    username = username,
                    content = content.trim(),
                    parentCommentId = reply?.id,
                    replyToUserId = reply?.userId,
                    replyToUsername = reply?.username
                )
            ) {
                is Resource.Success -> {
                    _commentActionStatus.value = Resource.Success("评论已发布")
                    _replyTarget.value = null
                    refreshComments()
                }
                is Resource.Error -> _commentActionStatus.value = Resource.Error(result.message ?: "评论失败")
                else -> Unit
            }
        }
    }

    /** 拉取当前资讯的点赞状态。 */
    fun refreshNewsLikes() {
        if (newsId.isBlank()) return
        launch {
            when (val result = repository.getNewsLikes(newsId, _userId.value)) {
                is Resource.Success -> _newsLikeStatus.value = result.data
                else -> Unit
            }
        }
    }

    fun toggleNewsLike() {
        val uid = _userId.value ?: return
        if (newsId.isBlank()) return
        launch {
            when (val result = repository.toggleNewsLike(newsId, uid)) {
                is Resource.Success -> {
                    val resp = result.data ?: return@launch
                    val current = _newsLikeStatus.value
                    _newsLikeStatus.value = NewsLikeStatusDto(
                        newsId = resp.newsId ?: newsId,
                        likeCount = resp.likeCount,
                        likedByMe = resp.liked
                    )
                    // 尚未拿到点赞状态时，回拉一次确保计数准确
                    if (current == null) refreshNewsLikes()
                    _commentActionStatus.value = Resource.Success(if (resp.liked) "已点赞" else "已取消点赞")
                }
                is Resource.Error -> _commentActionStatus.value = Resource.Error(result.message ?: "点赞失败")
                else -> Unit
            }
        }
    }

    fun toggleCommentLike(commentId: String) {
        val uid = _userId.value ?: return
        if (commentId.isBlank()) return
        launch {
            when (val r = repository.toggleCommentLike(commentId, uid)) {
                is Resource.Success -> {
                    refreshComments()
                    _commentActionStatus.value = Resource.Success(if (r.data?.liked == true) "已点赞评论" else "已取消点赞")
                }
                is Resource.Error -> _commentActionStatus.value = Resource.Error(r.message ?: "点赞失败")
                else -> Unit
            }
        }
    }

    fun banComment(commentId: String) {
        val adminId = _userId.value ?: return
        launch {
            _commentActionStatus.value = Resource.Loading()
            when (val result = repository.banComment(commentId, adminId)) {
                is Resource.Success -> {
                    _commentActionStatus.value = Resource.Success(result.data ?: "已封禁评论")
                    refreshComments()
                }
                is Resource.Error -> _commentActionStatus.value = Resource.Error(result.message ?: "封禁失败")
                else -> Unit
            }
        }
    }

    /** 重置一次性操作状态，避免重复消费提示。 */
    fun clearActionStatus() {
        _commentActionStatus.value = null
    }
}
