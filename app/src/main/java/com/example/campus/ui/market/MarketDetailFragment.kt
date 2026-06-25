package com.example.campus.ui.market

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.campus.R
import com.example.campus.databinding.FragmentMarketDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 二手商品详情页面 Fragment。
 *
 * 展示商品完整信息（标题、价格、卖家、发布时间、描述、主图），
 * 并支持收藏/取消收藏操作。
 */
@AndroidEntryPoint
class MarketDetailFragment : Fragment() {

    private var _binding: FragmentMarketDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MarketDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMarketDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.btnFavorite.setOnClickListener {
            viewModel.toggleFavorite()
        }
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.item.collect { item ->
                    if (item == null) {
                        binding.tvTitle.text = "未找到商品"
                        binding.tvPrice.text = ""
                        binding.tvSeller.text = ""
                        binding.tvTime.text = ""
                        binding.tvDescription.text = ""
                        binding.ivCover.setImageResource(R.drawable.ic_launcher_foreground)
                        return@collect
                    }

                    binding.tvTitle.text = item.title
                    binding.tvPrice.text = NumberFormat.getCurrencyInstance(Locale.CHINA).format(item.price)
                    binding.tvSeller.text = "卖家: ${item.sellerId}"
                    binding.tvTime.text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date(item.publishTime))
                    binding.tvDescription.text = item.description
                    binding.btnFavorite.text = if (item.isFavorite) "取消收藏" else "收藏"

                    if (!item.imageUrl.isNullOrBlank()) {
                        Glide.with(binding.root.context)
                            .load(item.imageUrl)
                            .placeholder(R.drawable.ic_launcher_foreground)
                            .error(R.drawable.ic_launcher_foreground)
                            .into(binding.ivCover)
                    } else {
                        binding.ivCover.setImageResource(R.drawable.ic_launcher_foreground)
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
