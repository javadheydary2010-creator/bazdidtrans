package com.bazdid.trans.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_PROJECT_NAME = "project_name"
    }

    private lateinit var binding: ActivityMapBinding
    private val viewModel: MapViewModel by viewModels()
    private var projectId: Long = -1
    private var projectName: String = ""

    private var addMode: AddMode = AddMode.NONE
    private var myLocationOverlay: MyLocationNewOverlay? = null

    private val poleMarkers = mutableMapOf<Long, Marker>()
    private val transformerMarkers = mutableMapOf<Long, Marker>()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            enableMyLocation()
        }
    }

    enum class AddMode { NONE, POLE, TRANSFORMER }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OsmDroid config
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectId = intent.getLongExtra(EXTRA_PROJECT_ID, -1)
        projectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: "پروژه"

        if (projectId == -1L) {
            finish()
            return
        }

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

            // Default center (Iran - can be changed)
            controller.setCenter(GeoPoint(35.6892, 51.3890))
        }

        // Tap listener for adding objects
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
                    AddMode.NONE -> { /* do nothing */ }
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
        binding.btnMyLocation.setOnClickListener {
            myLocationOverlay?.myLocation?.let {
                binding.mapView.controller.animateTo(it)
            } ?: Toast.makeText(this, "موقعیت در دسترس نیست", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setAddMode(mode: AddMode) {
        addMode = mode
        when (mode) {
            AddMode.POLE -> {
                binding.tvMode.visibility = View.VISIBLE
                binding.tvMode.text = "روی نقشه ضربه بزنید تا پایه اضافه شود"
                binding.btnAddPole.setBackgroundColor(ContextCompat.getColor(this, R.color.pole_color))
                binding.btnAddTransformer.setBackgroundColor(Color.TRANSPARENT)
            }
            AddMode.TRANSFORMER -> {
                binding.tvMode.visibility = View.VISIBLE
                binding.tvMode.text = "روی نقشه ضربه بزنید تا ترانس اضافه شود"
                binding.btnAddTransformer.setBackgroundColor(ContextCompat.getColor(this, R.color.transformer_color))
                binding.btnAddPole.setBackgroundColor(Color.TRANSPARENT)
            }
            AddMode.NONE -> {
                binding.tvMode.visibility = View.GONE
                binding.btnAddPole.setBackgroundColor(Color.TRANSPARENT)
                binding.btnAddTransformer.setBackgroundColor(Color.TRANSPARENT)
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
    }

    private fun updatePoleMarkers(poles: List<Pole>) {
        // Remove old markers that no longer exist
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
                    icon = ContextCompat.getDrawable(this@MapActivity, android.R.drawable.ic_menu_compass)
                    // Simple color via relatedObject or just use default
                    relatedObject = pole
                    setOnMarkerClickListener { m, _ ->
                        showPoleOptions(m.relatedObject as Pole)
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
                    snippet = "کلیک برای جزئیات"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    relatedObject = tr
                    setOnMarkerClickListener { m, _ ->
                        openTransformerDetail(m.relatedObject as Transformer)
                        true
                    }
                }
                binding.mapView.overlays.add(marker)
                transformerMarkers[tr.id] = marker

                // Center map on first transformer if no location yet
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
            enableFollowLocation()
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
