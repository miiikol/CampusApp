package com.example.campus.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import com.example.campus.R
import com.example.campus.core.common.UserRole
import com.example.campus.core.common.normalizeRole
import com.example.campus.core.common.Resource
import com.example.campus.core.ui.SnackType
import com.example.campus.core.ui.showSnack
import com.example.campus.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * 登录页面 Fragment。
 *
 * 职责：
 * - 负责收集用户输入（学号、密码）并触发 ViewModel 的登录逻辑
 * - 订阅登录状态（Loading/Success/Error），更新按钮状态与进行导航反馈
 * - 使用 ViewBinding 管理视图生命周期，避免空指针与内存泄漏
 *
 * 架构关系：
 * - 依赖 [LoginViewModel] 完成业务编排
 * - 通过 Navigation 进行页面跳转
 */
@AndroidEntryPoint
class LoginFragment : Fragment() {
    
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 通过 ViewBinding 构建并返回根视图
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 输入内容变化时清除对应输入框的错误提示
        binding.etStudentId.doAfterTextChanged {
            binding.tilStudentId.error = null
        }
        binding.etPassword.doAfterTextChanged {
            binding.tilPassword.error = null
        }

        // 点击登录按钮后从输入框读取学号与密码，并调用 ViewModel 触发登录
        binding.btnLogin.setOnClickListener {
            val studentId = binding.etStudentId.text?.toString().orEmpty().trim()
            val password = binding.etPassword.text?.toString().orEmpty()

            var hasError = false
            if (studentId.isBlank()) {
                binding.tilStudentId.error = "请输入学号"
                hasError = true
            }
            if (password.isBlank()) {
                binding.tilPassword.error = "请输入密码"
                hasError = true
            }
            if (hasError) {
                showSnack("请先填写完整信息", type = SnackType.ERROR)
                return@setOnClickListener
            }

            viewModel.login(studentId, password)
        }

        binding.tvForgotPassword.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_forgotPasswordFragment)
        }

        // 观察登录状态流并进行 UI 响应：
        // - Loading：禁用按钮并显示进度文案
        // - Success：恢复按钮、提示成功并返回上一页面
        // - Error：恢复按钮并展示错误消息
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loginState.collect { resource ->
                    when (resource) {
                        null -> Unit
                        is Resource.Loading -> {
                            binding.btnLogin.isEnabled = false
                            binding.btnLogin.text = getString(R.string.logging_in)
                            binding.tilStudentId.isEnabled = false
                            binding.tilPassword.isEnabled = false
                        }
                        is Resource.Success -> {
                            binding.btnLogin.isEnabled = true
                            binding.btnLogin.text = getString(R.string.login_button)
                            binding.tilStudentId.isEnabled = true
                            binding.tilPassword.isEnabled = true
                            showSnack(getString(R.string.login_success), type = SnackType.SUCCESS)

                            val targetDestination = if (normalizeRole(resource.data?.role) == UserRole.ADMIN) {
                                R.id.nav_admin
                            } else {
                                R.id.nav_course
                            }

                            // 登录成功后按角色跳转主页，并移除登录页的返回栈
                            findNavController().navigate(
                                targetDestination,
                                null,
                                androidx.navigation.NavOptions.Builder()
                                    .setPopUpTo(R.id.loginFragment, true)
                                    .build()
                            )
                            // 导航后重置状态，避免热流 Success 值重复触发导航
                            viewModel.resetState()
                        }
                        is Resource.Error -> {
                            binding.btnLogin.isEnabled = true
                            binding.btnLogin.text = getString(R.string.login_button)
                            binding.tilStudentId.isEnabled = true
                            binding.tilPassword.isEnabled = true
                            binding.tilPassword.error = resource.message
                            showSnack(resource.message ?: "登录失败", type = SnackType.ERROR)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 释放对 ViewBinding 的引用以避免持有无效的视图引用
        _binding = null
    }
}
