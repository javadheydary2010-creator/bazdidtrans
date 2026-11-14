package com.bazdid.trans.ui.schematic

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.bazdid.trans.R
import com.bazdid.trans.data.entity.SchematicNode
import com.bazdid.trans.databinding.ActivitySchematicBinding
import com.bazdid.trans.viewmodel.SchematicViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot

/**
 * صفحه کروکی شبکه فشار ضعیف
 * - وقتی نقشه روشن است: نقاط با مختصات جغرافیایی روی نقشه ثبت می‌شوند و با حرکت نقشه جابجا نمی‌شوند
 * - موقعیت مکانی (GPS) حتی بدون اینترنت کار می‌کند
 */
class SchematicActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSFORMER_ID = "transformer_id"
        const val METERS_PER_UNIT = 2.5f
        const val RESULT_LENGTHS = "result_lengths_summary"
        const val RESULT_TOTAL_M = "result_total_meters"
        const val RESULT_SINGLE = "result_single"
        const val RESULT_THREE = "result_three"
        const val RESULT_KW = "result_kw"
        const val RESULT_FEEDER_STATS = "result_feeder_stats"  // "s1,t1;s2,t2;..."
    }

    private lateinit var binding: ActivitySchematicBinding
    private val viewModel: SchematicViewModel by viewModels()
    private var transformerId: Long = -1
    private var lastActiveNodeId: Long? = null
    private var feederMode = false
    private var isMapVisible = false
    private var myLocationOverlay: MyLocationNewOverlay? = null

    // برای رسم نقاط و خطوط روی نقشه
    private val nodeMarkers = mutableMapOf<Long, Marker>()
    private val connectionPolylines = mutableListOf<Polyline>()
    private var mapEventsOverlay: MapEventsOverlay? = null
    private var currentAddMode: SchematicView.Mode = SchematicView.Mode.NONE

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            enableMyLocation()
        } else {
            Toast.makeText(this, "برای ثبت دقیق موقعیت، دسترسی لوکیشن لازم است", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName
        prepareOfflineTiles()

        binding = ActivitySchematicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        transformerId = intent.getLongExtra(EXTRA_TRANSFORMER_ID, -1)
        if (transformerId == -1L) {
            finish()
            return
        }

        setupToolbar()
        setupMap()
        setupView()
        setupButtons()
        observeData()
        viewModel.load(transformerId)

        // همیشه نقشه را از ابتدا نشان بده (یک صفحه واحد)
        isMapVisible = true
        binding.mapView.visibility = android.view.View.VISIBLE
        binding.schematicView.visibility = android.view.View.GONE
        binding.btnOfflineMap.text = "مخفی کردن نقشه"

        // بارگذاری موقعیت ترانس از ثبت اولیه صفحه اول
        loadTransformerLocation()
    }

    private fun loadTransformerLocation() {
        lifecycleScope.launch {
            val db = com.bazdid.trans.data.db.AppDatabase.getInstance(this@SchematicActivity)
            val tr = db.transformerDao().getById(transformerId)
            if (tr != null) {
                viewModel.ensureTransformerNode(tr.latitude, tr.longitude) { node ->
                    lastActiveNodeId = node.id
                    // نقشه را از ابتدا روشن کن و روی ترانس مرکز کن
                    if (!isMapVisible) {
                        isMapVisible = true
                        binding.mapView.visibility = View.VISIBLE
                        binding.schematicView.visibility = View.GONE
                        binding.btnOfflineMap.text = "مخفی کردن نقشه"
                    }
                    binding.mapView.controller.animateTo(GeoPoint(tr.latitude, tr.longitude))
                    binding.mapView.controller.setZoom(18.0)
                    enableMyLocation()
                    refreshMapOverlays()
                }
            } else {
                // حتی بدون ترانس، نقشه را روشن کن
                if (!isMapVisible) {
                    isMapVisible = true
                    binding.mapView.visibility = View.VISIBLE
                    binding.schematicView.visibility = View.GONE
                    binding.btnOfflineMap.text = "مخفی کردن نقشه"
                    enableMyLocation()
                }
            }
        }
    }

    private fun prepareOfflineTiles() {
        try {
            val base = File(getExternalFilesDir(null), "osmdroid")
            if (!base.exists()) base.mkdirs()
            val mbtiles = File(base, "mehran_area.mbtiles")
            if (!mbtiles.exists()) {
                assets.open("maps/mehran_area.mbtiles").use { input ->
                    FileOutputStream(mbtiles).use { output -> input.copyTo(output) }
                }
            }
            Configuration.getInstance().osmdroidBasePath = base
            Configuration.getInstance().osmdroidTileCache = File(base, "tiles")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            setBuiltInZoomControls(false)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            controller.setZoom(17.0)
            controller.setCenter(GeoPoint(26.95, 54.80))
            setUseDataConnection(true) // اگر اینترنت نباشد از کش استفاده می‌کند
        }

        // رویداد لمس روی نقشه برای قرار دادن پایه
        mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p ?: return false
                if (!isMapVisible) return false
                when (currentAddMode) {
                    SchematicView.Mode.ADD_POLE -> {
                        addGeoNode(p.latitude, p.longitude, "pole")
                        return true
                    }
                    SchematicView.Mode.ADD_OUTPUT -> {
                        addGeoNode(p.latitude, p.longitude, "output")
                        return true
                    }
                    else -> return false
                }
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        binding.mapView.overlays.add(mapEventsOverlay)

        checkLocationPermission()
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
        if (myLocationOverlay == null) {
            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.mapView).apply {
                enableMyLocation()
                enableFollowLocation()
                isDrawAccuracyEnabled = true
            }
            binding.mapView.overlays.add(myLocationOverlay)
        } else {
            myLocationOverlay?.enableMyLocation()
            myLocationOverlay?.enableFollowLocation()
        }
        myLocationOverlay?.myLocation?.let {
            binding.mapView.controller.animateTo(it)
            binding.mapView.controller.setZoom(18.0)
        }
    }

    /**
     * افزودن نود با مختصات جغرافیایی واقعی
     * lat در فیلد x و lon در فیلد y ذخیره می‌شود (برای سازگاری با دیتابیس فعلی)
     */
    private fun addGeoNode(lat: Double, lon: Double, type: String) {
        val x = lat.toFloat()
        val y = lon.toFloat()

        if (type == "pole" && feederMode) {
            viewModel.addNodeAndConnect(x, y, "pole", lastActiveNodeId) { newNode ->
                lastActiveNodeId = newNode.id
                showPhaseDialog(newNode, isNew = true)
                refreshMapOverlays()
            }
        } else {
            viewModel.addNode(x, y, type, isThreePhase = false)
            refreshMapOverlays()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.schematic_title)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupView() {
        // حالت بدون نقشه (صفحه سفید)
        binding.schematicView.onNodeAdded = { x, y, type ->
            if (type == "pole" && feederMode) {
                viewModel.addNodeAndConnect(x, y, "pole", lastActiveNodeId) { newNode ->
                    lastActiveNodeId = newNode.id
                    showPhaseDialog(newNode, isNew = true)
                }
            } else {
                viewModel.addNode(x, y, type, isThreePhase = false)
            }
        }

        binding.schematicView.onConnectionRequested = { fromId, toId ->
            viewModel.addConnection(fromId, toId)
            Toast.makeText(this, "خط اضافه شد", Toast.LENGTH_SHORT).show()
        }

        binding.schematicView.onNodesRepositioned = { updatedNodes ->
            viewModel.updateNodePositions(updatedNodes)
        }

        binding.schematicView.onNodeSelected = { node ->
            showNodeOptions(node)
        }
    }

    private fun setupButtons() {
        binding.btnAddPole.setOnClickListener {
            feederMode = true
            currentAddMode = SchematicView.Mode.ADD_POLE
            if (lastActiveNodeId == null) {
                val nodes = viewModel.nodes.value
                val transformerNode = nodes.find { it.nodeType == "transformer" }
                if (transformerNode == null) {
                    // اگر نقشه روشن است، ترانس را روی موقعیت فعلی GPS بگذار
                    if (isMapVisible) {
                        val loc = myLocationOverlay?.myLocation
                        if (loc != null) {
                            viewModel.addNode(loc.latitude.toFloat(), loc.longitude.toFloat(), "transformer", false)
                            // بعد از اضافه شدن، lastActiveNodeId در observe تنظیم می‌شود
                            Toast.makeText(this, "ترانس روی موقعیت شما قرار گرفت — حالا پایه بعدی را بزنید", Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.addTransformerNode { node ->
                                lastActiveNodeId = node.id
                                Toast.makeText(this, "فیدر شروع شد — روی نقشه ضربه بزنید", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        viewModel.addTransformerNode { node ->
                            lastActiveNodeId = node.id
                            binding.schematicView.setMode(SchematicView.Mode.ADD_POLE)
                            Toast.makeText(this, "فیدر شروع شد — پایه را بزنید", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    lastActiveNodeId = transformerNode.id
                    if (!isMapVisible) {
                        binding.schematicView.setMode(SchematicView.Mode.ADD_POLE)
                    }
                    Toast.makeText(this, if (isMapVisible) "روی نقشه پایه را بزنید" else "پایه بعدی را بزنید", Toast.LENGTH_SHORT).show()
                }
            } else {
                if (!isMapVisible) {
                    binding.schematicView.setMode(SchematicView.Mode.ADD_POLE)
                }
                Toast.makeText(this, if (isMapVisible) "روی نقشه پایه را بزنید" else "پایه بعدی را بزنید", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAddOutput.setOnClickListener {
            feederMode = false
            currentAddMode = SchematicView.Mode.ADD_OUTPUT
            if (!isMapVisible) {
                binding.schematicView.setMode(SchematicView.Mode.ADD_OUTPUT)
            }
            Toast.makeText(this, if (isMapVisible) "روی نقشه خروجی را بزنید" else "خروجی را بزنید", Toast.LENGTH_SHORT).show()
        }

        binding.btnExitMode.setOnClickListener {
            feederMode = false
            lastActiveNodeId = null
            currentAddMode = SchematicView.Mode.NONE
            binding.schematicView.setMode(SchematicView.Mode.NONE)
            Toast.makeText(this, "از حالت فعلی خارج شدید", Toast.LENGTH_SHORT).show()
        }

        binding.btnAutoLayout.setOnClickListener {
            if (isMapVisible) {
                Toast.makeText(this, "در حالت نقشه چیدمان خودکار غیرفعال است", Toast.LENGTH_SHORT).show()
            } else {
                binding.schematicView.autoLayout()
                Toast.makeText(this, "چیدمان انجام شد", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOfflineMap.setOnClickListener {
            toggleMapOnSamePage()
        }

        binding.btnFinish.setOnClickListener {
            finishDesignAndReturn()
        }

        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_schematic)
                .setMessage("تمام کروکی شبکه فشار ضعیف پاک شود؟")
                .setPositiveButton(R.string.yes) { _, _ ->
                    viewModel.clearAll()
                    lastActiveNodeId = null
                    feederMode = false
                    currentAddMode = SchematicView.Mode.NONE
                    clearMapOverlays()
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }

        // دکمه موقعیت من
        binding.btnMyLocation.setOnClickListener {
            if (!isMapVisible) {
                // اول نقشه را روشن کن
                toggleMapOnSamePage()
            }
            enableMyLocation()
            myLocationOverlay?.myLocation?.let {
                binding.mapView.controller.animateTo(it)
                binding.mapView.controller.setZoom(18.0)
            } ?: Toast.makeText(this, "در حال دریافت موقعیت...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleMapOnSamePage() {
        isMapVisible = !isMapVisible
        if (isMapVisible) {
            binding.mapView.visibility = View.VISIBLE
            binding.schematicView.visibility = View.GONE   // کروکی canvas مخفی می‌شود
            binding.btnOfflineMap.text = "مخفی کردن نقشه"
            enableMyLocation()
            refreshMapOverlays()
            Toast.makeText(this, "نقشه فعال شد — نقاط به موقعیت جغرافیایی وصل هستند (حتی آفلاین با GPS)", Toast.LENGTH_LONG).show()
        } else {
            binding.mapView.visibility = View.GONE
            binding.schematicView.visibility = View.VISIBLE
            binding.btnOfflineMap.text = "نقشه آفلاین"
            currentAddMode = SchematicView.Mode.NONE
            Toast.makeText(this, "نقشه مخفی شد", Toast.LENGTH_SHORT).show()
        }
    }

    /** رسم مجدد تمام مارکرها و خطوط روی نقشه */
    private fun refreshMapOverlays() {
        if (!isMapVisible) return

        // پاک کردن مارکرها و خطوط قبلی (به جز myLocation و mapEvents)
        nodeMarkers.values.forEach { binding.mapView.overlays.remove(it) }
        nodeMarkers.clear()
        connectionPolylines.forEach { binding.mapView.overlays.remove(it) }
        connectionPolylines.clear()

        val nodes = viewModel.nodes.value
        val connections = viewModel.connections.value
        val nodeMap = nodes.associateBy { it.id }

        // خطوط
        connections.forEach { conn ->
            val from = nodeMap[conn.fromNodeId]
            val to = nodeMap[conn.toNodeId]
            if (from != null && to != null) {
                // اگر مختصات شبیه lat/lon باشد (محدوده ایران)
                val fromGeo = toGeoPoint(from)
                val toGeo = toGeoPoint(to)
                if (fromGeo != null && toGeo != null) {
                    val line = Polyline(binding.mapView).apply {
                        setPoints(listOf(fromGeo, toGeo))
                        outlinePaint.color = Color.parseColor("#1565C0")
                        outlinePaint.strokeWidth = 8f
                    }
                    binding.mapView.overlays.add(line)
                    connectionPolylines.add(line)
                }
            }
        }

        // نقاط با آیکون سفارشی
        nodes.forEach { node ->
            val geo = toGeoPoint(node) ?: return@forEach
            val marker = Marker(binding.mapView).apply {
                position = geo
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = when (node.nodeType) {
                    "transformer" -> "ترانس"
                    "output" -> if (node.isThreePhase) "خروجی ۳فاز" else "خروجی ۱فاز"
                    else -> {
                        val sp = if (node.singlePhaseCount > 0) "۱ف:${node.singlePhaseCount}" else ""
                        val tp = if (node.threePhaseCount > 0) "۳ف:${node.threePhaseCount}" else ""
                        listOf(sp, tp).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "پایه" }
                    }
                }
                icon = when (node.nodeType) {
                    "transformer" -> createTransformerIcon()
                    "output" -> createOutputIcon()
                    else -> createPoleIcon()
                }
                relatedObject = node
                setOnMarkerClickListener { m, _ ->
                    val n = m.relatedObject as? SchematicNode
                    if (n != null) showNodeOptions(n)
                    true
                }
            }
            binding.mapView.overlays.add(marker)
            nodeMarkers[node.id] = marker
        }

        // myLocation و events را بالاتر نگه داریم
        myLocationOverlay?.let {
            binding.mapView.overlays.remove(it)
            binding.mapView.overlays.add(it)
        }
        mapEventsOverlay?.let {
            binding.mapView.overlays.remove(it)
            binding.mapView.overlays.add(it)
        }

        binding.mapView.invalidate()
    }

    private fun clearMapOverlays() {
        nodeMarkers.values.forEach { binding.mapView.overlays.remove(it) }
        nodeMarkers.clear()
        connectionPolylines.forEach { binding.mapView.overlays.remove(it) }
        connectionPolylines.clear()
        binding.mapView.invalidate()
    }

    /**
     * تشخیص اینکه x/y مختصات جغرافیایی است یا canvas
     * محدوده تقریبی ایران: lat 25..40 , lon 44..64
     */
    private fun toGeoPoint(node: SchematicNode): GeoPoint? {
        val lat = node.x.toDouble()
        val lon = node.y.toDouble()
        // محدوده ایران + حاشیه
        return if (lat in 24.0..42.0 && lon in 43.0..65.0) {
            GeoPoint(lat, lon)
        } else {
            null // مختصات canvas قدیمی — روی نقشه نشان داده نمی‌شود
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.nodes.collectLatest { nodes ->
                binding.schematicView.setData(nodes, viewModel.connections.value)
                // فقط اگر lastActiveNodeId هنوز ست نشده و ترانس وجود دارد، آن را به ترانس وصل کن
                // (دیگر به آخرین پایه وصل نمی‌شود تا فیدر جدید از ترانس شروع شود)
                if (lastActiveNodeId == null) {
                    lastActiveNodeId = nodes.find { it.nodeType == "transformer" }?.id
                }
                refreshMapOverlays()
            }
        }
        lifecycleScope.launch {
            viewModel.connections.collectLatest { connections ->
                binding.schematicView.setData(viewModel.nodes.value, connections)
                refreshMapOverlays()
            }
        }
    }

    private fun showNodeOptions(node: SchematicNode) {
        val options = mutableListOf("ویرایش انشعاب", "حذف آیتم")
        AlertDialog.Builder(this)
            .setTitle(node.label.ifEmpty { node.nodeType })
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showPhaseDialog(node, isNew = false)
                    1 -> {
                        viewModel.deleteNode(node)
                        if (lastActiveNodeId == node.id) lastActiveNodeId = null
                        Toast.makeText(this, "حذف شد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showPhaseDialog(node: SchematicNode, isNew: Boolean) {
        val inputSingle = EditText(this).apply {
            hint = "تعداد تک‌فاز"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (node.singlePhaseCount > 0) node.singlePhaseCount.toString() else "")
        }
        val inputThree = EditText(this).apply {
            hint = "تعداد سه‌فاز"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (node.threePhaseCount > 0) node.threePhaseCount.toString() else "")
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(inputSingle)
            addView(inputThree)
        }

        AlertDialog.Builder(this)
            .setTitle(if (node.nodeType == "pole") "ثبت انشعاب روی پایه" else "ثبت انشعاب")
            .setView(layout)
            .setPositiveButton("ثبت و ادامه") { _, _ ->
                val single = inputSingle.text.toString().toIntOrNull() ?: 0
                val three = inputThree.text.toString().toIntOrNull() ?: 0
                viewModel.updateNodePhases(node, single, three)
                if (feederMode) {
                    currentAddMode = SchematicView.Mode.ADD_POLE
                    if (!isMapVisible) binding.schematicView.setMode(SchematicView.Mode.ADD_POLE)
                    Toast.makeText(this, "ذخیره شد — پایه بعدی را بزنید", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("اتمام فیدر") { _, _ ->
                val single = inputSingle.text.toString().toIntOrNull() ?: 0
                val three = inputThree.text.toString().toIntOrNull() ?: 0
                viewModel.updateNodePhases(node, single, three)
                viewModel.zeroUnsetPhases()
                feederMode = false
                currentAddMode = SchematicView.Mode.NONE
                binding.schematicView.setMode(SchematicView.Mode.NONE)
                // فیدر بعدی از خود ترانس شروع شود (نه از آخرین پایه)
                lastActiveNodeId = viewModel.nodes.value.find { it.nodeType == "transformer" }?.id
                Toast.makeText(this, "فیدر تمام شد — فیدر بعدی از ترانس شروع می‌شود", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun finishDesignAndReturn() {
        viewModel.zeroUnsetPhases()
        val nodes = viewModel.nodes.value
        val connections = viewModel.connections.value
        val nodeMap = nodes.associateBy { it.id }

        // محاسبه طول
        var totalMeters = 0.0
        connections.forEach { conn ->
            val from = nodeMap[conn.fromNodeId]
            val to = nodeMap[conn.toNodeId]
            if (from != null && to != null) {
                val fromGeo = toGeoPoint(from)
                val toGeo = toGeoPoint(to)
                if (fromGeo != null && toGeo != null) {
                    totalMeters += fromGeo.distanceToAsDouble(toGeo)
                } else {
                    val distUnits = hypot(to.x - from.x, to.y - from.y)
                    totalMeters += distUnits * METERS_PER_UNIT
                }
            }
        }

        // گروه‌بندی پایه‌ها بر اساس فیدر (شاخه‌های متصل به ترانس)
        val feederGroups = computeFeederGroups(nodes, connections)
        val feederStats = feederGroups.map { groupNodes ->
            val single = groupNodes.sumOf { it.singlePhaseCount }
            val three = groupNodes.sumOf { it.threePhaseCount }
            single to three
        }

        val totalSingle = feederStats.sumOf { it.first }
        val totalThree = feederStats.sumOf { it.second }
        val loadKw = totalSingle * 6.0 + totalThree * 8.0

        val summary = buildString {
            appendLine("=== خلاصه کروکی شبکه فشار ضعیف ===")
            appendLine("تعداد پایه: ${nodes.count { it.nodeType == "pole" }}")
            appendLine("تعداد فیدر: ${feederStats.size}")
            feederStats.forEachIndexed { i, (s, t) ->
                val kw = s * 6.0 + t * 8.0
                appendLine("فیدر ${i + 1}: تک‌فاز=$s | سه‌فاز=$t | بار=${"%.1f".format(kw)} kW")
            }
            appendLine("تک‌فاز کل: $totalSingle | سه‌فاز کل: $totalThree")
            appendLine("بار تقریبی: ${"%.1f".format(loadKw)} kW")
            appendLine("طول کل شبکه: ${"%.1f".format(totalMeters)} متر")
        }

        // فرمت: "s1,t1;s2,t2;..."
        val statsStr = feederStats.joinToString(";") { "${it.first},${it.second}" }

        val data = Intent().apply {
            putExtra(RESULT_LENGTHS, summary)
            putExtra(RESULT_TOTAL_M, totalMeters.toFloat())
            putExtra(RESULT_SINGLE, totalSingle)
            putExtra(RESULT_THREE, totalThree)
            putExtra(RESULT_KW, loadKw.toFloat())
            putExtra(RESULT_FEEDER_STATS, statsStr)
        }
        setResult(Activity.RESULT_OK, data)
        Toast.makeText(this, "محاسبه شد — ${feederStats.size} فیدر | طول: ${"%.0f".format(totalMeters)} م", Toast.LENGTH_LONG).show()
        finish()
    }

    /** هر شاخه مستقیم از ترانس = یک فیدر */
    private fun computeFeederGroups(
        nodes: List<SchematicNode>,
        connections: List<com.bazdid.trans.data.entity.SchematicConnection>
    ): List<List<SchematicNode>> {
        val adj = mutableMapOf<Long, MutableList<Long>>()
        connections.forEach { c ->
            adj.getOrPut(c.fromNodeId) { mutableListOf() }.add(c.toNodeId)
            adj.getOrPut(c.toNodeId) { mutableListOf() }.add(c.fromNodeId)
        }
        val transformer = nodes.find { it.nodeType == "transformer" } ?: return listOf(nodes.filter { it.nodeType == "pole" })
        val neighbors = adj[transformer.id].orEmpty()
        if (neighbors.isEmpty()) return listOf(nodes.filter { it.nodeType == "pole" })

        val groups = mutableListOf<List<SchematicNode>>()
        val visited = mutableSetOf(transformer.id)
        neighbors.forEach { startId ->
            if (startId in visited) return@forEach
            val groupIds = mutableListOf<Long>()
            val queue = ArrayDeque<Long>()
            queue.add(startId)
            visited.add(startId)
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                groupIds.add(cur)
                for (next in adj[cur].orEmpty()) {
                    if (next !in visited) {
                        visited.add(next)
                        queue.add(next)
                    }
                }
            }
            val groupNodes = groupIds.mapNotNull { id -> nodes.find { it.id == id && it.nodeType == "pole" } }
            if (groupNodes.isNotEmpty()) groups.add(groupNodes)
        }
        return groups.ifEmpty { listOf(nodes.filter { it.nodeType == "pole" }) }
    }


    private fun createPoleIcon(): android.graphics.drawable.BitmapDrawable {
        val size = 48
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#212121")
            style = android.graphics.Paint.Style.FILL
        }
        c.drawCircle(size / 2f, size / 2f, size / 2f - 4f, p)
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    private fun createTransformerIcon(): android.graphics.drawable.BitmapDrawable {
        val size = 64
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#212121")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f
        }
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#212121")
            style = android.graphics.Paint.Style.FILL
        }
        val pad = 6f
        c.drawRect(pad, pad, size - pad, size - pad, stroke)
        c.drawCircle(size / 2f, size / 2f, 14f, fill)
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    private fun createOutputIcon(): android.graphics.drawable.BitmapDrawable {
        val size = 40
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#F9A825")
            style = android.graphics.Paint.Style.FILL
        }
        c.drawCircle(size / 2f, size / 2f, size / 2f - 3f, p)
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        myLocationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        myLocationOverlay?.disableMyLocation()
        binding.mapView.onPause()
    }
}
