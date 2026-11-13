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

            // اعمال محاسبات فیدر و خلاصه بار
            if (totalSingle > 0 || totalThree > 0) {
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
   
... 
