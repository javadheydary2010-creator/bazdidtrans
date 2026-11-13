package com.bazdid.trans.ui.schematic

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bazdid.trans.R
import com.bazdid.trans.data.entity.SchematicNode
import com.bazdid.trans.databinding.ActivitySchematicBinding
import com.bazdid.trans.viewmodel.SchematicViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.hypot

class SchematicActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSFORMER_ID = "transformer_id"
        /** متر به ازای هر واحد مختصات Canvas (قابل تنظیم) */
        const val METERS_PER_UNIT = 2.5f
        const val RESULT_LENGTHS = "result_lengths_summary"
        const val RESULT_TOTAL_M = "result_total_meters"
        const val RESULT_SINGLE = "result_single"
        const val RESULT_THREE = "result_three"
        const val RESULT_KW = "result_kw"
    }

    private lateinit var binding: ActivitySchematicBinding
    private val viewModel: SchematicViewModel by viewModels()
    private var transformerId: Long = -1
    private var lastActiveNodeId: Long? = null
    private var feederMode = false

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
        supportActionBar?.apply {
            title = getString(R.string.schematic_title)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupView() {
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
            if (lastActiveNodeId == null) {
                val nodes = viewModel.nodes.value
                val transformerNode = nodes.find { it.nodeType == "transformer" }
                if (transformerNode == null) {
                    viewModel.addTransformerNode { node ->
                        lastActiveNodeId = node.id
                        binding.schematicView.setMode(SchematicView.Mode.ADD_POLE)
                        Toast.makeText(this, "فیدر شروع شد — پایه را روی صفحه بزنید", Toast.LENGTH_LONG).show()
                    }
                } else {
                    lastActiveNodeId = transformerNode.id
                    binding.schematicView.setMode(SchematicView.Mode.ADD_POLE)
                    Toast.makeText(this, "پایه بعدی را بزنید", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.schematicView.setMode(SchematicView.Mode.ADD_POLE)
                Toast.makeText(this, "پایه بعدی را بزنید", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAddOutput.setOnClickListener {
            feederMode = false
            binding.schematicView.setMode(SchematicView.Mode.ADD_OUTPUT)
            Toast.makeText(this, "خروجی را روی صفحه بزنید", Toast.LENGTH_SHORT).show()
        }

        binding.btnExitMode.setOnClickListener {
            feederMode = false
            lastActiveNodeId = null
            binding.schematicView.setMode(SchematicView.Mode.NONE)
            Toast.makeText(this, "از حالت فعلی خارج شدید", Toast.LENGTH_SHORT).show()
        }

        binding.btnAutoLayout.setOnClickListener {
            binding.schematicView.autoLayout()
            Toast.makeText(this, "چیدمان انجام شد — برای خروج «خروج از حالت» را بزنید", Toast.LENGTH_SHORT).show()
        }

        binding.btnOfflineMap.setOnClickListener {
            startActivity(Intent(this, com.bazdid.trans.ui.offline.OfflineMapActivity::class.java))
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
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.nodes.collectLatest { nodes ->
                binding.schematicView.setData(nodes, viewModel.connections.value)
                if (lastActiveNodeId == null) {
                    lastActiveNodeId = nodes.lastOrNull {
                        it.nodeType == "pole" || it.nodeType == "transformer"
                    }?.id
                }
            }
        }
        lifecycleScope.launch {
            viewModel.connections.collectLatest { connections ->
                binding.schematicView.setData(viewModel.nodes.value, connections)
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

        val builder = AlertDialog.Builder(this)
            .setTitle(if (node.nodeType == "pole") "ثبت انشعاب روی پایه" else "ثبت انشعاب")
            .setView(layout)
            .setPositiveButton("ثبت و ادامه") { _, _ ->
                val single = inputSingle.text.toString().toIntOrNull() ?: 0
                val three = inputThree.text.toString().toIntOrNull() ?: 0
                viewModel.updateNodePhases(node, single, three)
                if (feederMode) {
                    binding.schematicView.setMode(SchematicView.Mode.ADD_POLE)
                    Toast.makeText(this, "ذخیره شد — پایه بعدی را بزنید", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("اتمام فیدر") { _, _ ->
                val single = inputSingle.text.toString().toIntOrNull() ?: 0
                val three = inputThree.text.toString().toIntOrNull() ?: 0
                viewModel.updateNodePhases(node, single, three)
                viewModel.zeroUnsetPhases()
                feederMode = false
                lastActiveNodeId = null
                binding.schematicView.setMode(SchematicView.Mode.NONE)
                Toast.makeText(this, "فیدر تمام شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)

        builder.show()
    }

    /**
     * محاسبه متراژ و بار و برگشت به صفحه ترانس
     * ریز جزئیات پایه‌ها در یادداشت نوشته نمی‌شود
     */
    private fun finishDesignAndReturn() {
        viewModel.zeroUnsetPhases()
        val nodes = viewModel.nodes.value
        val connections = viewModel.connections.value
        val nodeMap = nodes.associateBy { it.id }

        var totalMeters = 0.0
        connections.forEach { conn ->
            val from = nodeMap[conn.fromNodeId]
            val to = nodeMap[conn.toNodeId]
            if (from != null && to != null) {
                val distUnits = hypot(to.x - from.x, to.y - from.y)
                totalMeters += distUnits * METERS_PER_UNIT
            }
        }

        val poles = nodes.filter { it.nodeType == "pole" }
        val totalSingle = poles.sumOf { it.singlePhaseCount }
        val totalThree = poles.sumOf { it.threePhaseCount }
        val loadKw = totalSingle * 6.0 + totalThree * 8.0

        // خلاصه بدون ریز پایه
        val summary = buildString {
            appendLine("=== خلاصه کروکی شبکه فشار ضعیف ===")
            appendLine("تعداد پایه: ${poles.size}")
            appendLine("تک‌فاز کل: $totalSingle | سه‌فاز کل: $totalThree")
            appendLine("بار تقریبی: ${"%.1f".format(loadKw)} kW")
            appendLine("طول کل شبکه: ${"%.1f".format(totalMeters)} متر")
        }

        val data = Intent().apply {
            putExtra(RESULT_LENGTHS, summary)
            putExtra(RESULT_TOTAL_M, totalMeters.toFloat())
            putExtra(RESULT_SINGLE, totalSingle)
            putExtra(RESULT_THREE, totalThree)
            putExtra(RESULT_KW, loadKw.toFloat())
        }
        setResult(Activity.RESULT_OK, data)
        Toast.makeText(this, "محاسبه شد — طول کل: ${"%.0f".format(totalMeters)} متر", Toast.LENGTH_LONG).show()
        finish()
    }
}
