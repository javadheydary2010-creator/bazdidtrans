package com.bazdid.trans.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.bazdid.trans.R
import com.bazdid.trans.data.entity.Transformer
import com.bazdid.trans.databinding.ActivityMapBinding
import com.bazdid.trans.ui.transformer.TransformerDetailActivity
import com.bazdid.trans.viewmodel.MapViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.FileOutputStream

/**
 * صفحه اصلی اپ: نقشه موقعیت ترانس‌ها + جستجو بر اساس کد ترانس
 * فقط نمایش و افزودن ترانس — بدون پایه و خط
 */
class MapActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_PROJECT_NAME = "project_name"
    }

    private lateinit var binding: ActivityMapBinding
    private val viewModel: MapViewModel by viewModels()
    private var projectId: Long = 1L  // پروژه پیش‌فرض داخلی
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private val transformerMarkers = mutableMapOf<Long, Marker>()
    private var allTransformers: List<Transformer> = emptyList()
    private var addTransformerMode = false
    private var transformerIcon: android.graphics.drawable.Drawable? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            enableMyLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName

        // آماده‌سازی نقشه آفلاین از assets
        prepareOfflineMap()

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bmp = BitmapFactory.decodeResource(resources, R.drawable.ic_transformer_pin)
        if (bmp != null) {
            transformerIcon = BitmapDrawable(resources, bmp)
        }

        setupToolbar()
        setupMap()
        setupButtons()
        setupSearch()
        observeData()

        // اطمینان از وجود پروژه پیش‌فرض
        viewModel.ensureDefaultProject { id ->
            projectId = id
            viewModel.loadProject(id)
        }

        checkLocationPermission()
    }

    private fun prepareOfflineMap() {
        try {
            val destDir = File(getExternalFilesDir(null), "osmdroid")
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, "mehran_area.mbtiles")
            if (!destFile.exists()) {
                assets.open("maps/mehran_area.mbtiles").use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            // مسیر فایل‌های آفلاین OsmDroid
            Configuration.getInstance().osmdroidBasePath = destDir
            Configuration.getInstance().osmdroidTileCache = File(destDir, "tiles")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "بازدید ترانس"
        // صفحه اصلی — بدون دکمه برگشت
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true) // اگر آفلاین بود هم سعی می‌کند از کش استفاده کند
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(26.95, 54.80))
        }

        val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p ?: return false
                if (addTransformerMode) {
                    viewModel.addTransformer(projectId, p.latitude, p.longitude)
                    addTransformerMode = false
                    binding.tvMode.visibility = View.GONE
                    Toast.makeText(this@MapActivity, "ترانس اضافه شد", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        binding.mapView.overlays.add(0, mapEventsOverlay)
    }

    private fun setupButtons() {
        // فقط افزودن ترانس و موقعیت من
        binding.btnAddPole.visibility = View.GONE
        binding.btnAddLine.visibility = View.GONE

        binding.btnAddTransformer.text = "افزودن ترانس"
        binding.btnAddTransformer.setOnClickListener {
            addTransformerMode = !addTransformerMode
            if (addTransformerMode) {
                binding.tvMode.visibility = View.VISIBLE
                binding.tvMode.text = "روی نقشه ضربه بزنید تا ترانس ثبت شود"
            } else {
                binding.tvMode.visibility = View.GONE
            }
        }

        binding.btnMyLocation.setOnClickListener {
            myLocationOverlay?.myLocation?.let {
                binding.mapView.controller.animateTo(it)
            } ?: Toast.makeText(this, "موقعیت در دسترس نیست", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearch() {
        // اگر EditText جستجو در layout نباشد، با دیالوگ کار می‌کنیم
        binding.toolbar.setOnMenuItemClickListener { false }

        // دکمه جستجو روی تولبار (با کلیک طولانی روی عنوان یا دکمه جدا)
        binding.toolbar.setOnClickListener {
            showSearchDialog()
        }
        // دکمه جستجو اضافه از طریق منو ساده‌تر است؛ از AlertDialog استفاده می‌کنیم
        supportActionBar?.subtitle = "برای جستجو روی نوار بالا بزنید"
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "کد / نام ترانس"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("جستجوی ترانس")
            .setView(input)
            .setPositiveButton("جستجو") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isEmpty()) {
                    updateTransformerMarkers(allTransformers)
                    return@setPositiveButton
                }
                val filtered = allTransformers.filter {
                    it.name.contains(q, ignoreCase = true) ||
                        it.id.toString() == q
                }
                updateTransformerMarkers(filtered)
                if (filtered.isNotEmpty()) {
                    binding.mapView.controller.animateTo(
                        GeoPoint(filtered.first().latitude, filtered.first().longitude)
                    )
                    Toast.makeText(this, "${filtered.size} مورد پیدا شد", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "موردی یافت نشد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("نمایش همه") { _, _ ->
                updateTransformerMarkers(allTransformers)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.transformers.collectLatest { list ->
                allTransformers = list
                updateTransformerMarkers(list)
            }
        }
    }

    private fun updateTransformerMarkers(transformers: List<Transformer>) {
        val currentIds = transformers.map { it.id }.toSet()
        transformerMarkers.keys.filter { it !in currentIds }.forEach { id ->
            binding.mapView.overlays.remove(transformerMarkers.remove(id))
        }

        transformers.forEach { tr ->
            val existing = transformerMarkers[tr.id]
            if (existing == null) {
                val marker = Marker(binding.mapView).apply {
                    position = GeoPoint(tr.latitude, tr.longitude)
                    title = tr.name.ifEmpty { "ترانس #${tr.id}" }
                    snippet = "کلیک برای جزئیات"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    transformerIcon?.let { icon = it }
                    relatedObject = tr
                    setOnMarkerClickListener { m, _ ->
                        openTransformerDetail(m.relatedObject as Transformer)
                        true
                    }
                }
                binding.mapView.overlays.add(marker)
                transformerMarkers[tr.id] = marker
                if (transformerMarkers.size == 1) {
                    binding.mapView.controller.animateTo(marker.position)
                }
            } else {
                existing.position = GeoPoint(tr.latitude, tr.longitude)
                existing.title = tr.name.ifEmpty { "ترانس #${tr.id}" }
            }
        }
        binding.mapView.invalidate()
    }

    private fun openTransformerDetail(transformer: Transformer) {
        startActivity(Intent(this, TransformerDetailActivity::class.java).apply {
            putExtra(TransformerDetailActivity.EXTRA_TRANSFORMER_ID, transformer.id)
            putExtra(TransformerDetailActivity.EXTRA_PROJECT_ID, projectId)
        })
    }

    private fun checkLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun enableMyLocation() {
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.mapView).apply {
            enableMyLocation()
            try {
                val blackIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_my_location_black)
                if (blackIcon != null) {
                    setPersonIcon(blackIcon)
                    setDirectionIcon(blackIcon)
                }
            } catch (_: Exception) { }
        }
        binding.mapView.overlays.add(myLocationOverlay)
        myLocationOverlay?.runOnFirstFix {
            runOnUiThread {
                myLocationOverlay?.myLocation?.let {
                    binding.mapView.controller.animateTo(it)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }
}
