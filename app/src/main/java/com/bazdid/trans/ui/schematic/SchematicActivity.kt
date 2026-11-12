package com.bazdid.trans.ui.schematic

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bazdid.trans.R
import com.bazdid.trans.databinding.ActivitySchematicBinding
import com.bazdid.trans.viewmodel.SchematicViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SchematicActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSFORMER_ID = "transformer_id"
    }

    private lateinit var binding: ActivitySchematicBinding
    private val viewModel: SchematicViewModel by viewModels()
    private var transformerId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySchematicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        transformerId = intent.getLongExtra(EXTRA_TRANSFORMER_ID, -1)
        if (transformerId == -1L) {
            finish()
            return
        }

        setupToolbar()
        setupView()
        setupButtons()
        observeData()

        viewModel.load(transformerId)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupView() {
        binding.schematicView.onNodeAdded = { x, y, type ->
            if (type == "output") {
                AlertDialog.Builder(this)
                    .setTitle("نوع خروجی")
                    .setItems(arrayOf("تک‌فاز", "سه‌فاز")) { _, which ->
                        val isThree = which == 1
                        viewModel.addNode(x, y, "output", isThree)
                    }
                    .show()
            } else {
                viewModel.addNode(x, y, "pole", false)
            }
        }

        binding.schematicView.onConnectionRequested = { fromId, toId ->
            viewModel.addConnection(fromId, toId)
            Toast.makeText(this, "خط اضافه شد", Toast.LENGTH_SHORT).show()
        }

        binding.schematicView.onNodesRepositioned = { updatedNodes ->
            viewModel.updateNodePositions(updatedNodes)
        }
    }

    private fun setupButtons() {
        binding.btnAddPole.setOnClickListener {
            binding.schematicView.setMode(SchematicView.Mode.ADD_POLE)
            Toast.makeText(this, "روی صفحه ضربه بزنید تا پایه اضافه شود", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddOutput.setOnClickListener {
            binding.schematicView.setMode(SchematicView.Mode.ADD_OUTPUT)
            Toast.makeText(this, "روی صفحه ضربه بزنید تا خروجی اضافه شود", Toast.LENGTH_SHORT).show()
        }

        binding.btnAutoLayout.setOnClickListener {
            binding.schematicView.autoLayout()
            Toast.makeText(this, "چیدمان خودکار انجام شد", Toast.LENGTH_SHORT).show()
        }

        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_schematic)
                .setMessage("تمام نقشه فشار ضعیف پاک شود؟")
                .setPositiveButton(R.string.yes) { _, _ ->
                    viewModel.clearAll()
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.nodes.collectLatest { nodes ->
                val connections = viewModel.connections.value
                binding.schematicView.setData(nodes, connections)
            }
        }
        lifecycleScope.launch {
            viewModel.connections.collectLatest { connections ->
                val nodes = viewModel.nodes.value
                binding.schematicView.setData(nodes, connections)
            }
        }
    }
}
