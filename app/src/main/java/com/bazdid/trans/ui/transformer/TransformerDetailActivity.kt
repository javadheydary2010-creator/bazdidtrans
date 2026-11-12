package com.bazdid.trans.ui.transformer

import android.content.Intent
import android.os.Bundle
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

        binding.btnSave.setOnClickListener {
            saveTransformer()
        }

        binding.btnSchematic.setOnClickListener {
            val intent = Intent(this, SchematicActivity::class.java).apply {
                putExtra(SchematicActivity.EXTRA_TRANSFORMER_ID, transformerId)
            }
            startActivity(intent)
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
}
