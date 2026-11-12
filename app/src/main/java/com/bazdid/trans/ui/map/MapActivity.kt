package com.bazdid.trans.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.bazdid.trans.R
import com.bazdid.trans.data.entity.Pole
import com.bazdid.trans.data.entity.PowerLine
import com.bazdid.trans.data.entity.Transformer
import com.bazdid.trans.databinding.ActivityMapBinding
import com.bazdid.trans.ui.transformer.TransformerDetailActivity
import com.bazdid.trans.viewmodel.MapViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_PROJECT_NAME = "project_name"

        // رنگ‌های مختلف برای فیدرها
        val FEEDER_COLORS = listOf(
            Color.parseColor("#1565C0"),
            Color.parseColor("#2E7D32"),
            Color.parseColor("#E65100"),
            Color.parseColor("#6A1B9A"),
            Color.parseColor("#00838F"),
            Color.parseColor("#C62828"),
            Color.parseColor("#F9A825"),
            Color.parseColor("#4527A0")
        )
    }

    private lateinit var binding: ActivityMapBinding
    private val viewModel: MapViewModel by viewModels()
    private var projectId: Long = -1
    private var projectName: String = ""

    private var addMode: AddMode = AddMode.NONE
    private var myLocationOverlay: MyLocationNewOverlay? = null

    private val poleMarkers = mutableMapOf<Long, Marker>()
    private val transformerMarkers = mutableMapOf<Long, Marker>()
    private val lineOverlays = mutableListOf<Polyline>()

    // برای حالت کشیدن خط
    private var lineStartType: String? = null  // "pole" or "transformer"
    private var lineStartId: Long? = null

    private var poleIcon: Bitmap? = null
    private var transformerIcon: Bitmap? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            enableMyLocation()
        }
    }

    enum class AddMode { NONE, POLE, TRANSFORMER, LINE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectId = intent.getLongExtra(EXTRA_PROJECT_ID, -1)
        projectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: "پروژه"

        if (projectId == -1L) {
            finish()
            return
        }

        // بارگذاری آیکون‌های سفارشی
        poleIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_pole)
        transformerIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_transformer)

        setupToolbar()
        setupMap()
        setupButtons()
        observeData()

        viewModel.loadProject(projectId)
        checkLocationPermission()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = projectName
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(26.95, 54.80)) // نزدیک بندرلنگه / مهران
        }

        val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p ?: return false
                when (addMode) {
                    AddMode.POLE -> {
                        viewModel.addPole(projectId, p.latitude, p.longitude)
                        setAddMode(AddMode.NONE)
                        Toast.makeText(this@MapActivity, "پایه اضافه شد", Toast.LENGTH_SHORT).show()
                    }
                    AddMode.TRANSFORMER -> {
                        viewModel.addTransformer(projectId, p.latitude, p.longitude)
                        setAddMode(AddMode.NONE)
                        Toast.makeText(this@MapActivity, "ترانس اضافه شد", Toast.LENGTH_SHORT).show()
                    }
                    AddMode.LINE -> {
                        // در حالت خط، کلیک روی نقشه کاری نمی‌کند؛ باید روی مارکر کلیک شود
                    }
                    AddMode.NONE -> { }
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        binding.mapView.overlays.add(0, mapEventsOverlay)
    }

    private fun setupButtons() {
        binding.btnAddPole.setOnClickListener {
            setAddMode(if (addMode == AddMode.POLE) AddMode.NONE else AddMode.POLE)
        }
        binding.btnAddTransformer.setOnClickListener {
            setAddMode(if (addMode == AddMode.TRANSFORMER) AddMode.NONE else AddMode.TRANSFORMER)
        }
        binding.btnAddLine.setOnClickListener {
            setAddMode(if (addMode == AddMode.LINE) AddMode.NONE else AddMode.LINE)
            lineStartType = null
            lineStartId = null
        }
        binding.btnMyLocation.setOnClickListener {
            myLocationOverlay?.myLocation?.let {
                binding.mapView.controller.animateTo(it)
            } ?: Toast.makeText(this, "موقعیت در دسترس نیست", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setAddMode(mode: AddMode) {
        addMode = mode
        lineStartType = null
        lineStartId = null
        when (mode) {
            AddMode.POLE -> {
                binding.tvMode.visibility = View.VISIBLE
                binding.tvMode.text = "روی نقشه ضربه بزنید تا پایه اضافه شود"
            }
            AddMode.TRANSFORMER -> {
                binding.tvMode.visibility = View.VISIBLE
                binding.tvMode.text = "روی نقشه ضربه بزنید تا ترانس اضافه شود"
            }
            AddMode.LINE -> {
                binding.tvMode.visibility = View.VISIBLE
                binding.tvMode.text = "اول روی یک پایه یا ترانس، بعد روی نقطه دوم بزنید"
            }
            AddMode.NONE -> {
                binding.tvMode.visibility = View.GONE
            }
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.poles.collectLatest { poles ->
                updatePoleMarkers(poles)
            }
        }
        lifecycleScope.launch {
            viewModel.transformers.collectLatest { transformers ->
                updateTransformerMarkers(transformers)
            }
        }
        lifecycleScope.launch {
            viewModel.lines.collectLatest { lines ->
                updateLines(lines)
            }
        }
    }

    private fun updatePoleMarkers(poles: List<Pole>) {
        val currentIds = poles.map { it.id }.toSet()
        poleMarkers.keys.filter { it !in currentIds }.forEach { id ->
            binding.mapView.overlays.remove(poleMarkers.remove(id))
        }

        poles.forEach { pole ->
            val existing = poleMarkers[pole.id]
            if (existing == null) {
                val marker = Marker(binding.mapView).apply {
                    position = GeoPoint(pole.latitude, pole.longitude)
                    title = pole.name.ifEmpty { "پایه #${pole.id}" }
                    snippet = "پایه برق"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    poleIcon?.let { icon = it }
                    relatedObject = pole
                    setOnMarkerClickListener { m, _ ->
                        onPoleOrTransformerClicked("pole", (m.relatedObject as Pole).id, m.position)
                        true
                    }
                }
                binding.mapView.overlays.add(marker)
                poleMarkers[pole.id] = marker
            } else {
                existing.position = GeoPoint(pole.latitude, pole.longitude)
            }
        }
        binding.mapView.invalidate()
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
                    snippet = "کلیک برای جزئیات / اتصال خط"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    transformerIcon?.let { icon = it }
                    relatedObject = tr
                    setOnMarkerClickListener { m, _ ->
                        val t = m.relatedObject as Transformer
                        if (addMode == AddMode.LINE) {
                            onPoleOrTransformerClicked("transformer", t.id, m.position)
                        } else {
                            openTransformerDetail(t)
                        }
                        true
                    }
                }
                binding.mapView.overlays.add(marker)
                transformerMarkers[tr.id] = marker

                if (transformerMarkers.size == 1 && myLocationOverlay?.myLocation == null) {
                    binding.mapView.controller.animateTo(marker.position)
                }
            } else {
                existing.position = GeoPoint(tr.latitude, tr.longitude)
                existing.title = tr.name.ifEmpty { "ترانس #${tr.id}" }
            }
        }
        binding.mapView.invalidate()
    }

    private fun updateLines(lines: List<PowerLine>) {
        // حذف خطوط قبلی
        lineOverlays.forEach { binding.mapView.overlays.remove(it) }
        lineOverlays.clear()

        lines.forEachIndexed { index, line ->
            val fromPos = getPosition(line.fromType, line.fromId)
            val toPos = getPosition(line.toType, line.toId)
            if (fromPos != null && toPos != null) {
                val color = FEEDER_COLORS[index % FEEDER_COLORS.size]
                val poly = Polyline(binding.mapView).apply {
                    setPoints(listOf(fromPos, toPos))
                    outlinePaint.color = color
                    outlinePaint.strokeWidth = 8f
                }
                binding.mapView.overlays.add(poly)
                lineOverlays.add(poly)
            }
        }
        binding.mapView.invalidate()
    }

    private fun getPosition(type: String, id: Long): GeoPoint? {
        return when (type) {
            "pole" -> poleMarkers[id]?.position
            "transformer" -> transformerMarkers[id]?.position
            else -> null
        }
    }

    private fun onPoleOrTransformerClicked(type: String, id: Long, position: GeoPoint) {
        if (addMode != AddMode.LINE) {
            if (type == "pole") {
                val pole = poleMarkers[id]?.relatedObject as? Pole
                pole?.let { showPoleOptions(it) }
            }
            return
        }

        // حالت کشیدن خط
        if (lineStartType == null || lineStartId == null) {
            lineStartType = type
            lineStartId = id
            binding.tvMode.text = "حالا نقطه دوم (پایه یا ترانس) را انتخاب کنید"
            Toast.makeText(this, "نقطه اول انتخاب شد", Toast.LENGTH_SHORT).show()
        } else {
            if (lineStartType == type && lineStartId == id) {
                Toast.makeText(this, "نقطه تکراری است", Toast.LENGTH_SHORT).show()
                return
            }
            viewModel.addLine(
                projectId,
                lineStartType!!, lineStartId!!,
                type, id
            )
            Toast.makeText(this, "خط اضافه شد", Toast.LENGTH_SHORT).show()
            lineStartType = null
            lineStartId = null
            binding.tvMode.text = "اول روی یک پایه یا ترانس، بعد روی نقطه دوم بزنید"
        }
    }

    private fun showPoleOptions(pole: Pole) {
        AlertDialog.Builder(this)
            .setTitle(pole.name.ifEmpty { "پایه #${pole.id}" })
            .setItems(arrayOf("حذف پایه")) { _, which ->
                if (which == 0) {
                    viewModel.deletePole(pole)
                    Toast.makeText(this, "پایه حذف شد", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun openTransformerDetail(transformer: Transformer) {
        val intent = Intent(this, TransformerDetailActivity::class.java).apply {
            putExtra(TransformerDetailActivity.EXTRA_TRANSFORMER_ID, transformer.id)
            putExtra(TransformerDetailActivity.EXTRA_PROJECT_ID, projectId)
        }
        startActivity(intent)
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
            // نشانگر موقعیت سیاه
            try {
                val blackIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_my_location_black)
                setPersonIcon(blackIcon)
                setDirectionIcon(blackIcon)
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
