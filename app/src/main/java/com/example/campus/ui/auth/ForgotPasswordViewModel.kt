package com.example.campus.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.campus.core.base.BaseViewModel
import com.example.campus.core.common.Resource
import com.example.campus.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

    private val _resetState = MutableLiveData<Resource<String>>()
    val resetState: LiveData<Resource<String>> = _resetState

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
        if (newPassword != confirmPassword) {
            _resetState.value = Resource.Error("两次输入的密码不一致")
            return
        }

        launch {
            repository.resetPassword(studentId, fullName, idCardNo, newPassword).collectLatest {
                _resetState.value = it
            }
        }
    }
}
