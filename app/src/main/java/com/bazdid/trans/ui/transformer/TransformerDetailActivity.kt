package com.bazdid.trans.ui.transformer

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bazdid.trans.R
import com.bazdid.trans.databinding.ActivityTransformerDetailBinding
import com.bazdid.trans.ui.schematic.SchematicActivity
import com.bazdid.trans.util.LoadCalculator
import com.bazdid.trans.viewmodel.TransformerViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TransformerDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSFORMER_ID = "transformer_id"
        const val EXTRA_PROJECT_ID = "project_id"
    }

    private lateinit var binding: ActivityTransformerDetailBinding
    private var designSummary: String = ""
    private var totalNetworkMeters: Float = 0f

    private val schematicLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            designSummary = data?.getStringExtra(SchematicActivity.RESULT_LENGTHS) ?: ""
            totalNetworkMeters = data?.getFloatExtra(SchematicActivity.RESULT_TOTAL_M, 0f) ?: 0f
            val totalSingle = data?.getIntExtra(SchematicActivity.RESULT_SINGLE, 0) ?: 0
            val totalThree = data?.getIntExtra(SchematicActivity.RESULT_THREE, 0) ?: 0
            val loadKw = data?.getFloatExtra(SchematicActivity.RESULT_KW, 0f) ?: 0f

            // اعمال محاسبات هر فیدر به صورت جداگانه
            val statsStr = data?.getStringExtra(SchematicActivity.RESULT_FEEDER_STATS) ?: ""
            if (statsStr.isNotEmpty()) {
                val feederStats = statsStr.split(";")
                    .mapNotNull { part ->
                        val bits = part.split(",")
                        if (bits.size == 2) {
                            val s = bits[0].toIntOrNull() ?: 0
                            val t = bits[1].toIntOrNull() ?: 0
                            s to t
                        } else null
                    }
                if (feederStats.isNotEmpty()) {
                    viewModel.applySchematicFeederStats(feederStats)
                } else if (totalSingle > 0 || totalThree > 0) {
                    viewModel.applySchematicTotals(totalSingle, totalThree)
                }
            } else if (totalSingle > 0 || totalThree > 0) {
                viewModel.applySchematicTotals(totalSingle, totalThree)
            }

            // به‌روزرسانی یادداشت (بدون ریز پایه)
            if (designSummary.isNotEmpty()) {
                val current = binding.etNote.text?.toString().orEmpty()
                val cleaned = current
                    .lines()
                    .filterNot { it.startsWith("===") || it.startsWith("تعداد پایه") || it.startsWith("تک‌فاز") || it.startsWith("بار تقریبی") || it.startsWith("طول کل") || it.startsWith("---") }
                    .joinToString("\n")
                    .trim()
                binding.etNote.setText((cleaned + "\n\n" + designSummary).trim())
            }

            // ذخیره خودکار یادداشت و اطلاعات
            saveTransformer()

            Toast.makeText(
                this,
                "محاسبات اعمال شد — طول: ${"%.0f".format(totalNetworkMeters)} م | بار: ${"%.1f".format(loadKw)} kW",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val viewModel: TransformerViewModel by viewModels()
    private lateinit var feederAdapter: FeederAdapter

    private var transformerId: Long = -1
    private var projectId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransformerDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        transformerId = intent.getLongExtra(EXTRA_TRANSFORMER_ID, -1)
        projectId = intent.getLongExtra(EXTRA_PROJECT_ID, -1)

        if (transformerId == -1L) {
            finish()
            return
        }

        setupToolbar()
        setupRecycler()
        setupButtons()
        observeData()

        viewModel.load(transformerId)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.transformer_detail)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecycler() {
        feederAdapter = FeederAdapter(
            onCountChanged = { feeder, single, three ->
                viewModel.updateFeederCounts(feeder, single, three)
            },
            onDelete = { feeder ->
                viewModel.deleteFeeder(feeder)
            }
        )
        binding.recyclerFeeders.layoutManager = LinearLayoutManager(this)
        binding.recyclerFeeders.adapter = feederAdapter
    }

    private fun setupButtons() {
        binding.btnAddFeeder.setOnClickListener {
            viewModel.addFeeder()
        }

        binding.btnExport.setOnClickListener { exportReport() }
        binding.btnSave.setOnClickListener {
            saveTransformer()
        }

        binding.btnSchematic.setOnClickListener {
            val intent = Intent(this, SchematicActivity::class.java).apply {
                putExtra(SchematicActivity.EXTRA_TRANSFORMER_ID, transformerId)
            }
            schematicLauncher.launch(intent)
        }

        binding.btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage("ترانس و تمام اطلاعات آن حذف شود؟")
                .setPositiveButton(R.string.yes) { _, _ ->
                    viewModel.deleteTransformer()
                    finish()
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.transformer.collectLatest { tr ->
                tr ?: return@collectLatest
                binding.etName.setText(tr.name)
                binding.etCapacity.setText(tr.capacityKva?.toString() ?: "")
                binding.etNote.setText(tr.note)
                supportActionBar?.title = tr.name.ifEmpty { "ترانس #${tr.id}" }
            }
        }

        lifecycleScope.launch {
            viewModel.feeders.collectLatest { feeders ->
                feederAdapter.submitList(feeders)

                val totalLoad = LoadCalculator.calculateTotalLoad(feeders)
                val capacity = viewModel.transformer.value?.capacityKva
                val percent = LoadCalculator.calculateLoadPercentage(totalLoad, capacity)

                binding.tvTotalLoad.text = LoadCalculator.formatLoadBoth(totalLoad)
                binding.tvLoadPercent.text = LoadCalculator.formatPercentage(percent)

                val totalSingle = feeders.sumOf { it.singlePhaseCount }
                val totalThree = feeders.sumOf { it.threePhaseCount }
                binding.tvPhaseSummary.text =
                    "مجموع: $totalSingle تک‌فاز  |  $totalThree سه‌فاز"
            }
        }
    }

    private fun saveTransformer() {
        val name = binding.etName.text.toString().trim()
        val capacityStr = binding.etCapacity.text.toString().trim()
        val note = binding.etNote.text.toString().trim()
        val capacity = capacityStr.toDoubleOrNull()

        viewModel.updateTransformer(name, capacity, note)
        Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
    }

    private fun exportReport() {
        val tr = viewModel.transformer.value ?: return
        val feeders = viewModel.feeders.value
        val sb = StringBuilder()
        sb.appendLine("========== گزارش بازدید ترانس ==========")
        sb.appendLine("نام: ${tr.name}")
        sb.appendLine("ظرفیت: ${tr.capacityKva} kVA")
        sb.appendLine("موقعیت: ${tr.latitude}, ${tr.longitude}")
        sb.appendLine("--- فیدرها ---")
        var totalKw = 0.0
        feeders.forEach { f ->
            val kw = f.singlePhaseCount * 6.0 + f.threePhaseCount * 8.0
            totalKw += kw
            sb.appendLine("${f.name}: تک‌فاز=${f.singlePhaseCount} | سه‌فاز=${f.threePhaseCount} | ${kw} kW")
        }
        sb.appendLine("جمع بار فیدرها: $totalKw kW (≈ ${"%.1f".format(totalKw / 0.9)} kVA)")
        if (totalNetworkMeters > 0) {
            sb.appendLine("طول کل شبکه فشار ضعیف: ${"%.1f".format(totalNetworkMeters)} متر")
        }
        if (designSummary.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(designSummary)
        }
        sb.appendLine("==========================================")

        val geo = """{"type":"Feature","geometry":{"type":"Point","coordinates":[${tr.longitude},${tr.latitude}]},"properties":{"name":"${tr.name}","capacity_kva":${tr.capacityKva},"load_kw":$totalKw,"network_m":$totalNetworkMeters}}"""

        val options = arrayOf("گزارش پرینت (متن)", "خروجی GIS (GeoJSON)")
        AlertDialog.Builder(this)
            .setTitle("نوع خروجی")
            .setItems(options) { _, which ->
                val text = if (which == 0) sb.toString() else geo
                val subject = if (which == 0) "گزارش ${tr.name}" else "GIS ${tr.name}"
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(send, "اشتراک‌گذاری"))
            }
            .show()
    }
}
