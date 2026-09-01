package com.example.campus.ui.market

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.example.campus.R
import com.example.campus.core.common.Resource
import com.example.campus.core.ui.SnackType
import com.example.campus.core.ui.showSnack
import com.example.campus.databinding.FragmentMarketBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 二手市场列表页面 Fragment。
 *
 * 以网格布局展示二手商品，支持下拉刷新与发布新品。
 * 点击商品进入详情页，Fab 按钮弹出发布对话框。
 */
@AndroidEntryPoint
class MarketFragment : Fragment() {
    
    private var _binding: FragmentMarketBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MarketViewModel by viewModels()
    private lateinit var marketAdapter: MarketAdapter

    private var pendingImageUrlInput: EditText? = null
    private var pendingImagePreview: ImageView? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }

        pendingImageUrlInput?.setText(uri.toString())
        pendingImagePreview?.let { iv ->
            Glide.with(iv)
                .load(uri)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(iv)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMarketBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 初始化列表、注册监听并收集 ViewModel 状态
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        marketAdapter = MarketAdapter { item ->
            findNavController().navigate(
                R.id.marketDetailFragment,
                bundleOf("marketId" to item.id)
            )
        }
        binding.rvMarket.apply {
            adapter = marketAdapter
            // 使用两列网格布局展示商品
            layoutManager = GridLayoutManager(context, 2)
        }
    }

    private fun setupListeners() {
        binding.fabAdd.setOnClickListener {
            showPublishDialog()
        }
    }

    private fun showPublishDialog() {
        // 动态构建发布表单对话框：标题/描述/价格/图片（URL 或相册选择）
        val context = requireContext()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val ivPreview = ImageView(context).apply {
            val h = (160 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                h
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_launcher_foreground)
        }

        val etTitle = EditText(context).apply {
            hint = "标题"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val etDesc = EditText(context).apply {
            hint = "描述"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }
        val etPrice = EditText(context).apply {
            hint = "价格（元）"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etImageUrl = EditText(context).apply {
            hint = "图片（可选：URL 或 从相册选择）"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }

        val btnPick = com.google.android.material.button.MaterialButton(context).apply {
            text = "从相册选择图片"
            setOnClickListener {
                pendingImageUrlInput = etImageUrl
                pendingImagePreview = ivPreview
                pickImage.launch(arrayOf("image/*"))
            }
        }

        container.addView(etTitle)
        container.addView(etDesc)
        container.addView(etPrice)
        container.addView(btnPick)
        container.addView(ivPreview)
        container.addView(etImageUrl)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("发布二手商品")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("发布", null)
            .create()

        dialog.setOnDismissListener {
            pendingImageUrlInput = null
            pendingImagePreview = null
        }

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = etTitle.text?.toString().orEmpty().trim()
                val desc = etDesc.text?.toString().orEmpty().trim()
                val priceText = etPrice.text?.toString().orEmpty().trim()
                val price = priceText.toDoubleOrNull()

                if (title.isBlank() || desc.isBlank() || price == null || price < 0.0) {
                    showSnack("请填写完整的标题、描述与合法价格", type = SnackType.ERROR)
                    return@setOnClickListener
                }

                viewModel.clearPublishStatus()
                viewModel.publish(
                    title = title,
                    description = desc,
                    price = price,
                    imageUrl = etImageUrl.text?.toString()
                )
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.items.collect { items ->
                        marketAdapter.submitList(items)
                    }
                }

                launch {
                    viewModel.status.collect { status ->
                        when(status) {
                            is Resource.Loading -> {
                                // Show loading
                            }
                            is Resource.Error -> {
                                showSnack(status.message ?: "加载失败", type = SnackType.ERROR)
                            }
                            is Resource.Success -> {
                                // Success
                            }
                        }
                    }
                }

                launch {
                    viewModel.publishStatus.collect { status ->
                        when (status) {
                            null -> Unit
                            is Resource.Loading -> Unit
                            is Resource.Error -> {
                                // 后端将“已提交审核”也走 Error 通道，按文案区分提示类型
                                val message = status.message ?: "发布失败"
                                val snackType = if (message.contains("已提交审核")) SnackType.SUCCESS else SnackType.ERROR
                                showSnack(message, type = snackType)
                                viewModel.clearPublishStatus()
                            }
                            is Resource.Success -> {
                                showSnack("发布成功", type = SnackType.SUCCESS)
                                viewModel.clearPublishStatus()
                            }
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
