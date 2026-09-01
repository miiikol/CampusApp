package com.example.campus.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campus.core.common.Resource
import com.example.campus.core.ui.SnackType
import com.example.campus.core.ui.showSnack
import com.example.campus.databinding.FragmentAdminBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 管理员审核页面 Fragment。
 *
 * 展示待审核内容列表（二手商品/失物招领），
 * 管理员可逐条执行通过(APPROVE)或驳回(REJECT)操作。
 */
@AndroidEntryPoint
class AdminFragment : Fragment() {

    private var _binding: FragmentAdminBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by viewModels()
    private lateinit var adapter: AdminReviewAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 通过/驳回点击均回调 ViewModel，传入对应审核动作
        adapter = AdminReviewAdapter(
            onApprove = { viewModel.review(it.itemType, it.itemId, "APPROVE") },
            onReject = { viewModel.review(it.itemType, it.itemId, "REJECT") }
        )
        binding.rvPendingReviews.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPendingReviews.adapter = adapter
        binding.btnRefresh.setOnClickListener { viewModel.refresh() }
        observe()
        viewModel.refresh()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 观察待审核列表：刷新数据并控制空态提示
                launch {
                    viewModel.items.collect {
                        adapter.submitList(it)
                        binding.tvEmpty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                // 观察列表加载状态，仅在加载失败时提示
                launch {
                    viewModel.status.collect { status ->
                        if (status is Resource.Error) {
                            showSnack(status.message ?: "加载失败", type = SnackType.ERROR)
                        }
                    }
                }
                // 观察单次审核操作结果，成功后/失败后都重置状态避免重复提示
                launch {
                    viewModel.actionStatus.collect { status ->
                        when (status) {
                            is Resource.Error -> {
                                showSnack(status.message ?: "操作失败", type = SnackType.ERROR)
                                viewModel.clearActionStatus()
                            }
                            is Resource.Success -> {
                                showSnack(status.data ?: "操作成功", type = SnackType.SUCCESS)
                                viewModel.clearActionStatus()
                            }
                            else -> Unit
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
