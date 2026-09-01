package com.example.campus.ui.news

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.campus.R
import com.example.campus.core.common.Resource
import com.example.campus.core.ui.SnackType
import com.example.campus.core.ui.showSnack
import com.example.campus.databinding.FragmentPublishNewsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 管理员发布资讯页面 Fragment。
 *
 * 支持填写标题、分类、摘要、正文并选择封面图片，
 * 提交后经 [NewsViewModel.publishNews] 发布到远端。
 * 页面同时嵌入资讯列表预览，方便管理员查看已发布内容。
 */
@AndroidEntryPoint
class PublishNewsFragment : Fragment() {

    private var _binding: FragmentPublishNewsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewsViewModel by viewModels()

    private var selectedImageUri: String? = null
    private lateinit var newsAdapter: NewsAdapter

    // 选择封面图：持久化读权限并回显预览
    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        selectedImageUri = uri.toString()
        binding.ivPreview.visibility = View.VISIBLE
        binding.btnRemoveImage.visibility = View.VISIBLE
        Glide.with(binding.ivPreview)
            .load(uri)
            .placeholder(R.drawable.ic_launcher_foreground)
            .error(R.drawable.ic_launcher_foreground)
            .into(binding.ivPreview)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPublishNewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化资讯列表预览
        setupNewsList()

        // 选择/移除封面图与发布按钮监听
        binding.btnPickImage.setOnClickListener {
            pickImage.launch(arrayOf("image/*"))
        }

        binding.btnRemoveImage.setOnClickListener {
            selectedImageUri = null
            binding.ivPreview.setImageResource(R.drawable.ic_launcher_foreground)
            binding.ivPreview.visibility = View.GONE
            binding.btnRemoveImage.visibility = View.GONE
        }

        binding.btnPublish.setOnClickListener {
            doPublish()
        }

        observeStatus()
        observeNews()
    }

    private fun setupNewsList() {
        newsAdapter = NewsAdapter { news ->
            findNavController().navigate(
                R.id.newsDetailFragment,
                bundleOf("newsId" to news.id)
            )
        }
        binding.rvNews.apply {
            adapter = newsAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun observeNews() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 收集资讯列表并处理空态
                viewModel.news.collect { newsList ->
                    newsAdapter.submitList(newsList)
                    binding.tvNewsEmpty.visibility = if (newsList.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun doPublish() {
        // 读取并校验表单，全部通过后提交发布
        val title = binding.etTitle.text?.toString().orEmpty().trim()
        val type = binding.etType.text?.toString().orEmpty().trim()
        val summary = binding.etSummary.text?.toString().orEmpty().trim()
        val content = binding.etContent.text?.toString().orEmpty().trim()

        if (title.isEmpty()) {
            showSnack("标题不能为空", type = SnackType.ERROR)
            return
        }
        if (type.isEmpty()) {
            showSnack("分类不能为空", type = SnackType.ERROR)
            return
        }
        if (content.isEmpty()) {
            showSnack("正文不能为空", type = SnackType.ERROR)
            return
        }
        if (title.length > 100) {
            showSnack("标题不能超过100字", type = SnackType.ERROR)
            return
        }
        if (content.length > 5000) {
            showSnack("正文不能超过5000字", type = SnackType.ERROR)
            return
        }

        viewModel.publishNews(
            adminId = "",
            title = title,
            summary = summary,
            content = content,
            type = type,
            imageUrl = selectedImageUri
        )
    }

    private fun observeStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 发布状态：加载中禁用按钮，成功清空表单，失败仅提示
                viewModel.publishStatus.collect { status ->
                    when (status) {
                        is Resource.Loading -> {
                            binding.btnPublish.isEnabled = false
                            binding.btnPublish.text = "发布中…"
                        }
                        is Resource.Success -> {
                            binding.btnPublish.isEnabled = true
                            binding.btnPublish.text = "发 布"
                            showSnack("资讯发布成功！", type = SnackType.SUCCESS)
                            clearForm()
                            viewModel.clearPublishStatus()
                        }
                        is Resource.Error -> {
                            binding.btnPublish.isEnabled = true
                            binding.btnPublish.text = "发 布"
                            showSnack(status.message ?: "发布失败", type = SnackType.ERROR)
                            viewModel.clearPublishStatus()
                        }
                        else -> {
                            binding.btnPublish.isEnabled = true
                            binding.btnPublish.text = "发 布"
                        }
                    }
                }
            }
        }
    }

    private fun clearForm() {
        binding.etTitle.text?.clear()
        binding.etType.text?.clear()
        binding.etSummary.text?.clear()
        binding.etContent.text?.clear()
        selectedImageUri = null
        binding.ivPreview.setImageResource(R.drawable.ic_launcher_foreground)
        binding.ivPreview.visibility = View.GONE
        binding.btnRemoveImage.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
