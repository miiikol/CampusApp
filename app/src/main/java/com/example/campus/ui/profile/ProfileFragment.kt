package com.example.campus.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.campus.R
import com.example.campus.core.common.UserRole
import com.example.campus.core.common.normalizeRole
import com.example.campus.core.common.Resource
import com.example.campus.core.ui.SnackType
import com.example.campus.core.ui.showSnack
import com.example.campus.data.repository.MarketRepository
import com.example.campus.data.repository.NotificationRepository
import com.example.campus.data.repository.UserRepository
import com.example.campus.databinding.FragmentProfileBinding
import com.example.campus.ui.market.MarketAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 个人中心页面 Fragment。
 *
 * 展示用户信息（头像、昵称、学号、角色），并集成以下功能模块：
 * - 修改昵称/头像
 * - 二手收藏列表
 * - 通知消息列表（含标记已读）
 * - 退出登录
 */
@AndroidEntryPoint
class ProfileFragment : Fragment() {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var marketRepository: MarketRepository

    @Inject
    lateinit var notificationRepository: NotificationRepository

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var currentUserId: String? = null
    private lateinit var favoriteAdapter: MarketAdapter
    private lateinit var notificationAdapter: NotificationAdapter

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val uid = currentUserId ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = userRepository.updateAvatar(uid, uri.toString())) {
                is Resource.Success -> showSnack("头像已更新", type = SnackType.SUCCESS)
                is Resource.Error -> showSnack(r.message ?: "头像更新失败", type = SnackType.ERROR)
                else -> Unit
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 收藏列表点击跳转到对应商品详情
        favoriteAdapter = MarketAdapter { item ->
            findNavController().navigate(
                R.id.marketDetailFragment,
                bundleOf("marketId" to item.id)
            )
        }
        binding.rvFavorites.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFavorites.adapter = favoriteAdapter

        notificationAdapter = NotificationAdapter()
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotifications.adapter = notificationAdapter

        // 分别订阅用户信息、收藏列表与通知列表
        observeUser()
        observeFavorites()
        observeNotifications()

        binding.btnEditNickname.setOnClickListener { showEditNicknameDialog() }
        binding.btnChangeAvatar.setOnClickListener { pickAvatar.launch(arrayOf("image/*")) }
        binding.btnMarkAllRead.setOnClickListener { markAllRead() }
        binding.btnLogout.setOnClickListener {
            doLogout()
        }
    }

    private fun observeUser() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userRepository.getUser().collect { user ->
                    // 未登录时展示占位信息
                    if (user == null) {
                        currentUserId = null
                        binding.tvUsername.text = getString(R.string.profile_username_empty)
                        binding.tvStudentId.text = getString(R.string.profile_student_id_empty)
                        binding.tvRole.text = getString(R.string.profile_role_empty)
                        binding.ivAvatar.setImageResource(R.drawable.ic_launcher_foreground)
                        return@collect
                    }
                    currentUserId = user.id
                    // 角色归一化后映射为管理员/学生文案
                    val roleText = if (normalizeRole(user.role) == UserRole.ADMIN) {
                        getString(R.string.role_admin)
                    } else {
                        getString(R.string.role_student)
                    }
                    binding.tvUsername.text = getString(R.string.profile_username, user.username)
                    binding.tvStudentId.text = getString(R.string.profile_student_id, user.studentId)
                    binding.tvRole.text = getString(R.string.profile_role, roleText)

                    val avatar = user.avatarUrl
                    if (!avatar.isNullOrBlank()) {
                        Glide.with(binding.root.context)
                            .load(avatar)
                            .placeholder(R.drawable.ic_launcher_foreground)
                            .error(R.drawable.ic_launcher_foreground)
                            .into(binding.ivAvatar)
                    } else {
                        binding.ivAvatar.setImageResource(R.drawable.ic_launcher_foreground)
                    }
                }
            }
        }
    }

    private fun observeFavorites() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userRepository.getUser().collectLatest { user ->
                    val uid = user?.id
                    if (uid.isNullOrBlank()) {
                        favoriteAdapter.submitList(emptyList())
                        return@collectLatest
                    }
                    // 先刷新商品数据，再订阅当前用户的收藏列表
                    marketRepository.refreshItems()
                    marketRepository.favoriteItems(uid).collect { favorites ->
                        favoriteAdapter.submitList(favorites)
                    }
                }
            }
        }
    }

    private fun observeNotifications() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                userRepository.getUser().collectLatest { user ->
                    val uid = user?.id
                    if (uid.isNullOrBlank()) {
                        notificationAdapter.submitList(emptyList())
                        return@collectLatest
                    }
                    // 先同步通知再订阅本地通知列表
                    notificationRepository.sync(uid)
                    notificationRepository.notifications(uid).collect { list ->
                        notificationAdapter.submitList(list)
                    }
                }
            }
        }
    }

    private fun showEditNicknameDialog() {
        val uid = currentUserId ?: return
        val et = EditText(requireContext()).apply {
            hint = "昵称"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("修改昵称")
            .setView(et)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val name = et.text?.toString().orEmpty()
                viewLifecycleOwner.lifecycleScope.launch {
                    when (val r = userRepository.updateNickname(uid, name)) {
                        is Resource.Success -> showSnack("昵称已更新", type = SnackType.SUCCESS)
                        is Resource.Error -> showSnack(r.message ?: "昵称更新失败", type = SnackType.ERROR)
                        else -> Unit
                    }
                }
            }
            .show()
    }

    private fun markAllRead() {
        val uid = currentUserId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            // 无未读消息时直接提示，避免无效请求
            val unread = notificationRepository.unreadCountNow(uid)
            if (unread <= 0) {
                showSnack("暂无未读消息", type = SnackType.SUCCESS)
                return@launch
            }
            when (val r = notificationRepository.readAll(uid)) {
                is Resource.Success -> showSnack("已标记为已读", type = SnackType.SUCCESS)
                is Resource.Error -> showSnack(r.message ?: "操作失败", type = SnackType.ERROR)
                else -> Unit
            }
        }
    }

    private fun doLogout() {
        viewLifecycleOwner.lifecycleScope.launch {
            userRepository.logout()
            showSnack(getString(R.string.profile_logout_success), type = SnackType.SUCCESS)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
