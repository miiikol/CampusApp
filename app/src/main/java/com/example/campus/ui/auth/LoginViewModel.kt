package com.example.campus.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.campus.core.base.BaseViewModel
import com.example.campus.core.common.Resource
import com.example.campus.data.local.entity.UserEntity
import com.example.campus.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

/**
 * 登录页面的 ViewModel，负责登录流程的业务编排与状态管理。
 *
 * 职责：
 * - 校验输入的学号与密码
 * - 调用数据层登录接口，并以 [Resource] 形式向 UI 层暴露状态
 * - 继承 [BaseViewModel] 提供协程作用域与通用错误处理能力
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: UserRepository
) : BaseViewModel() {

    private val _loginState = MutableLiveData<Resource<UserEntity>>()
    val loginState: LiveData<Resource<UserEntity>> = _loginState

    /**
     * 触发登录流程。
     *
     * 流程：
     * 1. 本地输入校验（不可为空）
     * 2. 调用 [UserRepository.login] 获取登录结果流
     * 3. 收集并更新 [_loginState]，供 UI 层响应 Loading/Success/Error 三态
     */
    fun login(studentId: String, password: String) {
        if (studentId.isBlank() || password.isBlank()) {
            _loginState.value = Resource.Error("学号和密码不能为空")
            return
        }

        launch {
            repository.login(studentId, password).collectLatest {
                _loginState.value = it
            }
        }
    }
}
