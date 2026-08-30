package com.example.campus.ui.lostfound

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.campus.R
import com.example.campus.core.common.Resource
import com.example.campus.core.ui.SnackType
import com.example.campus.core.ui.showSnack
import com.example.campus.data.local.entity.LostFoundEntity
import com.example.campus.databinding.FragmentLostFoundBinding
import com.example.campus.ui.map.MapLocation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 失物招领列表页面 Fragment。
 *
 * 展示失物与招领信息列表，按类型（寻物/招领）区分显示。
 * 支持发布新记录（含图片选择与地图位置选择），
 * 点击条目可查看详情或在地图上查看位置。
 */
@AndroidEntryPoint
class LostFoundFragment : Fragment() {
    
    private var _binding: FragmentLostFoundBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: LostFoundViewModel by viewModels()
    private lateinit var lostFoundAdapter: LostFoundAdapter
    private var pendingOpenPublishDialog = false
    private var lastPickedLocation: MapLocation? = null

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
        _binding = FragmentLostFoundBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        // 接收 MapPickerFragment 回传的位置结果（用 StateFlow 替代 LiveData，统一状态管理规范）
        val handle = findNavController().currentBackStackEntry?.savedStateHandle
        handle?.let { h ->
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    h.getStateFlow<MapLocation?>("selected_location", null).collect { loc ->
                        if (loc == null) return@collect
                        lastPickedLocation = loc
                        showSnack(
                            "已选择位置: ${loc.address ?: "${loc.latitude},${loc.longitude}"}",
                            type = SnackType.SUCCESS
                        )
                        h.remove<MapLocation>("selected_location")
                        if (pendingOpenPublishDialog) {
                            pendingOpenPublishDialog = false
                            showPublishDialog(loc)
                        }
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        lostFoundAdapter = LostFoundAdapter { item ->
            showItemDetail(item)
        }
        binding.rvLostFound.apply {
            adapter = lostFoundAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun showItemDetail(item: LostFoundEntity) {
        val context = requireContext()
        val typeText = if (item.type == "LOST") "寻物" else "招领"
        val padding = (16 * resources.displayMetrics.density).toInt()
        val imageHeight = (220 * resources.displayMetrics.density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val iv = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                imageHeight
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val tvInfo = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * resources.displayMetrics.density).toInt()
            }
            text = buildString {
                append("类型：").append(typeText).append('\n')
                append("地点：").append(item.location).append('\n')
                append("描述：").append(item.description).append('\n')
                val contact = item.contactInfo?.takeIf { it.isNotBlank() } ?: "未提供"
                append("联系方式：").append(contact)
            }
        }

        val imageUrl = item.imageUrl?.trim()?.takeIf { it.isNotBlank() }
        if (imageUrl != null) {
            container.addView(iv)
            Glide.with(context)
                .load(imageUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(iv)
        }
        container.addView(tvInfo)

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(item.title)
            .setView(container)
            .setPositiveButton("关闭", null)

        if (item.latitude != null && item.longitude != null) {
            builder.setNeutralButton("查看位置") { _, _ ->
                val args = bundleOf(
                    "latitude" to item.latitude.toString(),
                    "longitude" to item.longitude.toString(),
                    "title" to item.title,
                    "address" to item.location
                )
                findNavController().navigate(com.example.campus.R.id.mapViewerFragment, args)
            }
        }

        builder.show()
    }

    private fun setupListeners() {
        binding.fabAdd.setOnClickListener {
            pendingOpenPublishDialog = false
            showPublishDialog(lastPickedLocation)
        }
    }

    private fun showPublishDialog(location: MapLocation?) {
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
        val etLocation = EditText(context).apply {
            hint = "地点"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(location?.address.orEmpty())
        }
        val etContact = EditText(context).apply {
            hint = "联系方式（可选）"
            inputType = InputType.TYPE_CLASS_TEXT
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

        val rgType = RadioGroup(context).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        val rbLost = RadioButton(context).apply {
            text = "寻物"
            id = View.generateViewId()
        }
        val rbFound = RadioButton(context).apply {
            text = "招领"
            id = View.generateViewId()
        }
        rgType.addView(rbLost)
        rgType.addView(rbFound)
        rbLost.isChecked = true

        container.addView(etTitle)
        container.addView(etDesc)
        container.addView(rgType)
        container.addView(etLocation)
        container.addView(etContact)
        container.addView(btnPick)
        container.addView(ivPreview)
        container.addView(etImageUrl)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("发布失物招领")
            .setView(container)
            .setNegativeButton("取消", null)
            .setNeutralButton("地图选点", null)
            .setPositiveButton("发布", null)
            .create()

        dialog.setOnDismissListener {
            pendingImageUrlInput = null
            pendingImagePreview = null
        }

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                pendingOpenPublishDialog = true
                findNavController().navigate(com.example.campus.R.id.mapPickerFragment)
                dialog.dismiss()
            }
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = etTitle.text?.toString().orEmpty().trim()
                val desc = etDesc.text?.toString().orEmpty().trim()
                val locText = etLocation.text?.toString().orEmpty().trim()
                val type = if (rgType.checkedRadioButtonId == rbFound.id) "FOUND" else "LOST"

                if (title.isBlank() || desc.isBlank() || locText.isBlank()) {
                    showSnack("请填写完整的标题、描述与地点", type = SnackType.ERROR)
                    return@setOnClickListener
                }

                viewModel.clearPublishStatus()
                viewModel.publish(
                    title = title,
                    description = desc,
                    location = locText,
                    type = type,
                    contactInfo = etContact.text?.toString(),
                    latitude = location?.latitude,
                    longitude = location?.longitude,
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
                        lostFoundAdapter.submitList(items)
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
