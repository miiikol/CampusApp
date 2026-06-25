package com.example.campus.ui.map

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.example.campus.core.ui.SnackType
import com.example.campus.core.ui.showSnack
import com.example.campus.databinding.FragmentMapViewerBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 地图查看页面 Fragment。
 *
 * 接收失物招领等模块传入的坐标，在高德地图上标记展示位置。
 * 同时支持搜索 POI 并在地图上标注搜索结果。
 */
class MapViewerFragment : Fragment() {

    private var _binding: FragmentMapViewerBinding? = null
    private val binding get() = _binding!!

    private var mapView: MapView? = null
    private var aMap: AMap? = null
    private var selectedMarker: Marker? = null
    private var poiSearch: PoiSearch? = null
    private var tipsJob: Job? = null
    private val tipsAdapter = TipsAdapter { tip ->
        onTipSelected(tip)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapViewerBinding.inflate(inflater, container, false)
        initAmapPrivacy()
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.rvTips.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTips.adapter = tipsAdapter
        binding.etKeyword.doOnTextChanged { text, _, _, _ ->
            val keyword = text?.toString()?.trim().orEmpty()
            binding.btnSearch.isEnabled = keyword.isNotEmpty()
            requestInputTips(keyword)
        }
        binding.btnSearch.setOnClickListener {
            val keyword = binding.etKeyword.text?.toString()?.trim().orEmpty()
            if (keyword.isNotEmpty()) {
                binding.rvTips.visibility = View.GONE
                searchPoiBySdk(keyword)
            }
        }
        try {
            mapView = binding.mapView
            mapView?.onCreate(savedInstanceState)
            aMap = mapView?.map
            setupMap()
        } catch (t: Throwable) {
            showSnack("地图初始化失败：${t.message ?: "未知错误"}", type = SnackType.ERROR)
        }
        return binding.root
    }

    private fun initAmapPrivacy() {
        val ctx = requireContext().applicationContext
        try {
            MapsInitializer.updatePrivacyShow(ctx, true, true)
            MapsInitializer.updatePrivacyAgree(ctx, true)
        } catch (_: Throwable) {
        }
    }

    private fun setupMap() {
        val args = requireArguments()
        val latitude = args.getString("latitude")?.toDoubleOrNull() ?: 0.0
        val longitude = args.getString("longitude")?.toDoubleOrNull() ?: 0.0
        val title = args.getString("title") ?: "位置"
        val address = args.getString("address")
        val latLng = LatLng(latitude, longitude)
        aMap?.uiSettings?.isZoomControlsEnabled = true
        selectedMarker = aMap?.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(title)
                .snippet(address)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
        if (!address.isNullOrBlank()) {
            binding.tvAddress.text = address
        }
        aMap?.setOnMarkerClickListener { marker ->
            val snippet = marker.snippet?.takeIf { it.isNotBlank() }
            binding.tvAddress.text = snippet ?: marker.title.orEmpty()
            true
        }
        aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
    }

    private fun requestInputTips(keyword: String) {
        tipsJob?.cancel()
        if (keyword.isBlank()) {
            tipsAdapter.submit(emptyList())
            binding.rvTips.visibility = View.GONE
            return
        }

        tipsJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(250)
            if (!isAdded || _binding == null) return@launch
            if (!ensureAmapKeyConfigured()) return@launch
            val query = InputtipsQuery(keyword, null)
            query.cityLimit = false
            val inputtips = Inputtips(requireContext().applicationContext, query)
            inputtips.setInputtipsListener { tips, rCode ->
                if (!isAdded || _binding == null) return@setInputtipsListener
                if (rCode != 1000) {
                    tipsAdapter.submit(emptyList())
                    binding.rvTips.visibility = View.GONE
                    return@setInputtipsListener
                }
                val list = tips.orEmpty()
                    .filter { it.name?.isNotBlank() == true }
                    .take(8)
                tipsAdapter.submit(list)
                binding.rvTips.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
            inputtips.requestInputtipsAsyn()
        }
    }

    private fun onTipSelected(tip: Tip) {
        binding.rvTips.visibility = View.GONE
        val name = tip.name?.takeIf { it.isNotBlank() }.orEmpty()
        if (name.isNotBlank()) {
            binding.etKeyword.setText(name)
            binding.etKeyword.setSelection(name.length)
        }

        val point = tip.point
        if (point != null) {
            val latLng = LatLng(point.latitude, point.longitude)
            selectedMarker?.remove()
            selectedMarker = aMap?.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(name.ifBlank { "位置" })
                    .snippet(tip.address?.takeIf { it.isNotBlank() })
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
            aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        }

        val display = buildString {
            if (name.isNotBlank()) append(name)
            val district = tip.district?.takeIf { it.isNotBlank() }
            val address = tip.address?.takeIf { it.isNotBlank() }
            if (district != null) {
                if (isNotEmpty()) append(" · ")
                append(district)
            }
            if (address != null) {
                if (isNotEmpty()) append(" · ")
                append(address)
            }
        }
        if (display.isNotBlank()) {
            binding.tvAddress.text = display
        }
    }

    private fun searchPoiBySdk(keyword: String) {
        if (!ensureAmapKeyConfigured()) return
        val center = aMap?.cameraPosition?.target
        if (center == null) {
            showSnack("地图尚未就绪，无法搜索", type = SnackType.INFO)
            return
        }

        val query = PoiSearch.Query(keyword, null, null).apply {
            pageSize = 10
            pageNum = 1
        }
        val search = PoiSearch(requireContext().applicationContext, query).also { s ->
            s.bound = PoiSearch.SearchBound(
                LatLonPoint(center.latitude, center.longitude),
                5000,
                true
            )
            s.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                override fun onPoiSearched(result: PoiResult?, rCode: Int) {
                    if (!isAdded || _binding == null) return
                    if (rCode != 1000) {
                        showSnack("搜索失败：$rCode", type = SnackType.ERROR)
                        return
                    }
                    val pois = result?.pois.orEmpty()
                    showPoiMarkers(pois, keyword)
                }

                override fun onPoiItemSearched(item: PoiItem?, rCode: Int) = Unit
            })
        }
        poiSearch = search
        search.searchPOIAsyn()
    }

    private fun showPoiMarkers(pois: List<PoiItem>, keyword: String) {
        if (pois.isEmpty()) {
            showSnack("未找到相关地点，请换个关键词试试", type = SnackType.INFO)
            return
        }

        aMap?.clear()
        selectedMarker = null

        val first = pois.firstOrNull()
        val firstPoint = first?.latLonPoint
        if (first != null && firstPoint != null) {
            val firstLatLng = LatLng(firstPoint.latitude, firstPoint.longitude)
            selectedMarker = aMap?.addMarker(
                MarkerOptions()
                    .position(firstLatLng)
                    .title(first.title.takeIf { it.isNotBlank() } ?: keyword)
                    .snippet(first.snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
            binding.tvAddress.text = first.snippet.orEmpty()
            aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(firstLatLng, 16f))
        }

        pois.take(5).forEach { poi ->
            val point = poi.latLonPoint ?: return@forEach
            val latLng = LatLng(point.latitude, point.longitude)
            aMap?.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(poi.title.takeIf { it.isNotBlank() } ?: keyword)
                    .snippet(poi.snippet?.takeIf { it.isNotBlank() })
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        }
    }

    private fun ensureAmapKeyConfigured(): Boolean {
        val key = try {
            val appInfo = requireContext().packageManager.getApplicationInfo(
                requireContext().packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.amap.api.v2.apikey")?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
        val ok = !key.isNullOrBlank() && key != "REPLACE_WITH_YOUR_AMAP_KEY"
        if (!ok) {
            showSnack("请先配置高德 Key（AMAP_API_KEY）", type = SnackType.ERROR)
        }
        return ok
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tipsJob?.cancel()
        mapView?.onDestroy()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    private class TipsAdapter(
        private val onClick: (Tip) -> Unit
    ) : RecyclerView.Adapter<TipsAdapter.VH>() {

        private val items = ArrayList<Tip>()

        fun submit(newItems: List<Tip>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(view, onClick)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class VH(
            itemView: View,
            private val onClick: (Tip) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val title = itemView.findViewById<android.widget.TextView>(android.R.id.text1)
            private val sub = itemView.findViewById<android.widget.TextView>(android.R.id.text2)
            private var current: Tip? = null

            init {
                itemView.setOnClickListener {
                    current?.let(onClick)
                }
            }

            fun bind(tip: Tip) {
                current = tip
                title.text = tip.name.orEmpty()
                sub.text = buildString {
                    val district = tip.district?.takeIf { it.isNotBlank() }
                    val address = tip.address?.takeIf { it.isNotBlank() }
                    if (district != null) append(district)
                    if (address != null) {
                        if (isNotEmpty()) append(" ")
                        append(address)
                    }
                }
            }
        }
    }
}
