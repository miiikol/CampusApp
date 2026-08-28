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
import com.example.campus.core.common.Resource
import com.example.campus.core.ui.SnackType
import com.example.campus.core.ui.showSnack
import com.example.campus.databinding.FragmentForgotPasswordBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * 忘记密码页面 Fragment。
 *
 * 收集用户身份信息（学号、姓名、身份证号）与新密码，
 * 通过 [ForgotPasswordViewModel] 提交重置请求，
 * 成功后自动返回登录页。
 */
@AndroidEntryPoint
class ForgotPasswordFragment : Fragment() {

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ForgotPasswordViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etStudentId.doAfterTextChanged { binding.tilStudentId.error = null }
        binding.etFullName.doAfterTextChanged { binding.tilFullName.error = null }
        binding.etIdCardNo.doAfterTextChanged { binding.tilIdCardNo.error = null }
        binding.etNewPassword.doAfterTextChanged { binding.tilNewPassword.error = null }
        binding.etConfirmPassword.doAfterTextChanged { binding.tilConfirmPassword.error = null }

        binding.btnResetPassword.setOnClickListener {
            val studentId = binding.etStudentId.text?.toString().orEmpty().trim()
            val fullName = binding.etFullName.text?.toString().orEmpty().trim()
            val idCardNo = binding.etIdCardNo.text?.toString().orEmpty().trim().uppercase()
            val newPassword = binding.etNewPassword.text?.toString().orEmpty()
            val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()
            viewModel.resetPassword(studentId, fullName, idCardNo, newPassword, confirmPassword)
        }

        binding.btnBackToLogin.setOnClickListener {
            findNavController().popBackStack()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.resetState.collect { state ->
                    when (state) {
                        null -> Unit
                        is Resource.Loading -> {
                            binding.btnResetPassword.isEnabled = false
                            binding.btnResetPassword.text = getString(R.string.forgot_password_resetting)
                            binding.tilStudentId.isEnabled = false
                            binding.tilFullName.isEnabled = false
                            binding.tilIdCardNo.isEnabled = false
                            binding.tilNewPassword.isEnabled = false
                            binding.tilConfirmPassword.isEnabled = false
                        }
                        is Resource.Success -> {
                            binding.btnResetPassword.isEnabled = true
                            binding.btnResetPassword.text = getString(R.string.forgot_password_reset_button)
                            binding.tilStudentId.isEnabled = true
                            binding.tilFullName.isEnabled = true
                            binding.tilIdCardNo.isEnabled = true
                            binding.tilNewPassword.isEnabled = true
                            binding.tilConfirmPassword.isEnabled = true
                            showSnack(state.data ?: getString(R.string.forgot_password_reset_success), type = SnackType.SUCCESS)
                            findNavController().popBackStack()
                        }
                        is Resource.Error -> {
                            binding.btnResetPassword.isEnabled = true
                            binding.btnResetPassword.text = getString(R.string.forgot_password_reset_button)
                            binding.tilStudentId.isEnabled = true
                            binding.tilFullName.isEnabled = true
                            binding.tilIdCardNo.isEnabled = true
                            binding.tilNewPassword.isEnabled = true
                            binding.tilConfirmPassword.isEnabled = true
                            showSnack(state.message ?: getString(R.string.forgot_password_reset_failed), type = SnackType.ERROR)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
