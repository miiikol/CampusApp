package com.example.campus.ui.auth

import com.example.campus.core.base.BaseViewModel
import com.example.campus.core.common.Resource
import com.example.campus.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

/**
 * 忘记密码页面的 ViewModel，负责密码重置的业务编排与输入校验。
 *
 * 校验规则：学号、姓名、身份证号（18位）、新密码均不可为空，
 * 且两次密码输入必须一致。校验通过后调用 [UserRepository.resetPassword]。
 */
@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val repository: UserRepository
) : BaseViewModel() {

    // 初始为 null 表示「空闲」，避免热流初始 Loading 值导致按钮一开始就进入重置中状态
    private val _resetState = MutableStateFlow<Resource<String>?>(null)
    val resetState: StateFlow<Resource<String>?> = _resetState.asStateFlow()

    /**
     * 提交密码重置请求。
     *
     * 依次校验学号、姓名、身份证号（18 位）、新密码（至少 8 位）及两次密码一致性，
     * 校验通过后调用 [UserRepository.resetPassword] 并收集结果更新 [_resetState]。
     */
    fun resetPassword(
        studentId: String,
        fullName: String,
        idCardNo: String,
        newPassword: String,
        confirmPassword: String
    ) {
        if (studentId.isBlank()) {
            _resetState.value = Resource.Error("请输入学号")
            return
        }
        if (fullName.isBlank()) {
            _resetState.value = Resource.Error("请输入姓名")
            return
        }
        if (idCardNo.isBlank()) {
            _resetState.value = Resource.Error("请输入身份证号")
            return
        }
        if (idCardNo.length != 18) {
            _resetState.value = Resource.Error("身份证号应为18位")
            return
        }
        if (newPassword.isBlank()) {
            _resetState.value = Resource.Error("请输入新密码")
            return
        }
        if (newPassword.length < 8) {
            _resetState.value = Resource.Error("密码长度至少 8 位")
            return
        }
        if (confirmPassword.isBlank()) {
            _resetState.value = Resource.Error("请确认新密码")
            return
        }
        if (newPassword != confirmPassword) {
            _resetState.value = Resource.Error("两次密码不一致")
            return
        }

        launch {
            repository.resetPassword(studentId, fullName, idCardNo, newPassword)
                .collectLatest {
                    _resetState.value = it
                }
        }
    }
}
