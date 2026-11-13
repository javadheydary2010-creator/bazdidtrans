package com.bazdid.trans.ui.schematic

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

/**
 * طراحی شبکه فشار ضعیف:
 * - شروع فیدر از ترانس
 * - با هر پایه، خط خودکار به تجهیز قبلی وصل می‌شود
 * - بلافاصله دیالوگ تعداد تک‌فاز/سه‌فاز باز می‌شود
 * - امکان جابجایی و ویرایش
 */
class SchematicActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSFORMER_ID = "transformer_id"
    }

    private lateinit var binding: ActivitySchematicBinding
    private val viewModel: SchematicViewModel by viewModels()
    private var transformerId: Long = -1

    /** آخرین گره فعال برای اتصال خودکار خط */
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
        // وقتی پایه جدید اضافه شد
        binding.schematicView.onNodeAdded = { x, y, type ->
            if (type == "pole" && feederMode) {
                // افزودن پایه و اتصال خودکار
                viewModel.addNodeAndConnect(x, y, "pole", lastActiveNodeId) { newNode ->
                    lastActiveNodeId = newNode.id
                    showPhaseDialog(newNode)
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
            if (node.nodeType == "pole" || node.nodeType == "output") {
                showPhaseDialog(node)
            }
        }
    }

    private fun setupButtons() {
        // دکمه پایه = شروع/ادامه فیدر
        binding.btnAddPole.setOnClickListener {
            feederMode = true
            // اگر هنوز گرهی نیست، ترانس را به عنوان مرکز در نظر بگیر
            if (lastActiveNodeId == null) {
                val nodes = viewModel.nodes.value
                val transformerNode = nodes.find { it.nodeType == "transformer" }
                if (transformerNode == null) {
                    // یک گره ترانس در مرکز بساز
                    viewModel.addTransformerNode { node ->
                        lastActiveNodeId = node.id
                        binding.schematicView.setMode(SchematicView.Mode.ADD_POLE)
                        Toast.makeText(this, "فیدر شروع شد — پایه بعدی را روی نقشه بزنید", Toast.LENGTH_LONG).show()
                    }
                } else {
                    lastActiveNodeId = transformerNode.id
                    binding.schematicView.setMode(SchematicView.Mode.ADD_POLE)
                    Toast.makeText(this, "پایه بعدی را روی نقشه بزنید", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.schematicView.setMode(SchematicView.Mode.ADD_POLE)
                Toast.makeText(this, "پایه بعدی را روی نقشه بزنید", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAddOutput.setOnClickListener {
            feederMode = false
            binding.schematicView.setMode(SchematicView.Mode.ADD_OUTPUT)
            Toast.makeText(this, "خروجی را روی نقشه بزنید", Toast.LENGTH_SHORT).show()
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
                val connections = viewModel.connections.value
                binding.schematicView.setData(nodes, connections)
                // آخرین پایه را به عنوان فعال نگه دار
                if (lastActiveNodeId == null) {
                    lastActiveNodeId = nodes.lastOrNull { it.nodeType == "pole" || it.nodeType == "transformer" }?.id
                }
            }
        }
        lifecycleScope.launch {
            viewModel.connections.collectLatest { connections ->
                val nodes = viewModel.nodes.value
                binding.schematicView.setData(nodes, connections)
            }
        }
    }

    private fun showPhaseDialog(node: SchematicNode) {
        val inputSingle = EditText(this).apply {
            hint = "تعداد تک‌فاز"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(node.singlePhaseCount.toString())
        }
        val inputThree = EditText(this).apply {
            hint = "تعداد سه‌فاز"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(node.threePhaseCount.toString())
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(inputSingle)
            addView(inputThree)
        }

        AlertDialog.Builder(this)
            .setTitle(if (node.nodeType == "pole") "ثبت انشعاب روی پایه" else "ثبت انشعاب خروجی")
            .setView(layout)
            .setPositiveButton("ثبت و ادامه") { _, _ ->
                val single = inputSingle.text.toString().toIntOrNull() ?: 0
                val three = inputThree.text.toString().toIntOrNull() ?: 0
                viewModel.updateNodePhases(node, single, three)
                Toast.makeText(this, "ذخیره شد — پایه بعدی را بزنید", Toast.LENGTH_SHORT).show()
                // آماده‌باش برای پایه بعدی
                if (feederMode) {
                    binding.schematicView.setMode(SchematicView.Mode.ADD_POLE)
                }
            }
            .setNeutralButton("پایان فیدر") { _, _ ->
                val single = inputSingle.text.toString().toIntOrNull() ?: 0
                val three = inputThree.text.toString().toIntOrNull() ?: 0
                viewModel.updateNodePhases(node, single, three)
                feederMode = false
                lastActiveNodeId = null
                binding.schematicView.setMode(SchematicView.Mode.NONE)
                Toast.makeText(this, "فیدر تمام شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}
