package com.bazdid.trans.ui.schematic

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.bazdid.trans.data.entity.SchematicConnection
import com.bazdid.trans.data.entity.SchematicNode
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class SchematicView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val nodes = mutableListOf<SchematicNode>()
    private val connections = mutableListOf<SchematicConnection>()

    // Zoom & Pan
    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private val minScale = 0.4f
    private val maxScale = 3.0f

    // رنگ‌های متفاوت برای فیدرها
    private val feederColors = intArrayOf(
        Color.parseColor("#1565C0"), // آبی
        Color.parseColor("#2E7D32"), // سبز
        Color.parseColor("#E65100"), // نارنجی
        Color.parseColor("#6A1B9A"), // بنفش
        Color.parseColor("#C62828"), // قرمز
        Color.parseColor("#00838F"), // فیروزه‌ای
        Color.parseColor("#F9A825"), // زرد
        Color.parseColor("#AD1457"), // صورتی تیره
        Color.parseColor("#37474F"), // خاکستری
        Color.parseColor("#558B2F")  // سبز روشن
    )

    private val polePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        style = Paint.Style.FILL
    }
    private val transformerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C62828")
        style = Paint.Style.FILL
    }
    private val transformerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B71C1C")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val outputPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F9A825")
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C62828")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var selectedNodeId: Long? = null
    private var mode: Mode = Mode.NONE
    private var connectFromId: Long? = null

    /** نقشه nodeId -> شماره فیدر (برای رنگ‌بندی) */
    private var nodeFeederIndex = mapOf<Long, Int>()

    var onNodeAdded: ((Float, Float, String) -> Unit)? = null
    var onConnectionRequested: ((Long, Long) -> Unit)? = null
    var onNodesRepositioned: ((List<SchematicNode>) -> Unit)? = null
    var onNodeSelected: ((SchematicNode) -> Unit)? = null

    /** وقتی true باشد، تاچ‌ها را به MapView زیرین پاس می‌دهد */
    var passThroughTouches: Boolean = false

    enum class Mode { NONE, ADD_POLE, ADD_OUTPUT, CONNECT }

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)
                invalidate()
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float
            ): Boolean {
                if (mode == Mode.NONE && !scaleDetector.isInProgress) {
                    offsetX -= distanceX
                    offsetY -= distanceY
                    invalidate()
                    return true
                }
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val (x, y) = screenToWorld(e.x, e.y)
                handleTap(x, y)
                return true
            }
        })

    fun setData(nodeList: List<SchematicNode>, connList: List<SchematicConnection>) {
        nodes.clear()
        nodes.addAll(nodeList)
        connections.clear()
        connections.addAll(connList)
        computeFeederColors()
        invalidate()
    }

    fun setMode(newMode: Mode) {
        mode = newMode
        connectFromId = null
        selectedNodeId = null
        invalidate()
    }

    /**
     * از نود ترانس شروع می‌کند و هر شاخه مستقیم را یک فیدر جدا با رنگ جدا در نظر می‌گیرد.
     */
    private fun computeFeederColors() {
        val adj = mutableMapOf<Long, MutableList<Long>>()
        connections.forEach { c ->
            adj.getOrPut(c.fromNodeId) { mutableListOf() }.add(c.toNodeId)
            adj.getOrPut(c.toNodeId) { mutableListOf() }.add(c.fromNodeId)
        }

        val transformer = nodes.find { it.nodeType == "transformer" }
        val result = mutableMapOf<Long, Int>()

        if (transformer == null) {
            nodes.forEach { result[it.id] = 0 }
            nodeFeederIndex = result
            return
        }

        result[transformer.id] = -1
        val neighbors = adj[transformer.id].orEmpty()
        neighbors.forEachIndexed { feederIdx, startId ->
            val queue = ArrayDeque<Long>()
            queue.add(startId)
            result[startId] = feederIdx
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                for (next in adj[cur].orEmpty()) {
                    if (next !in result) {
                        result[next] = feederIdx
                        queue.add(next)
                    }
                }
            }
        }
        nodes.forEach { n ->
            if (n.id !in result) result[n.id] = 0
        }
        nodeFeederIndex = result
    }

    /** چیدمان خودکار مرتب برای جلوگیری از شلوغی */
    fun autoLayout() {
        if (nodes.isEmpty()) return

        val poles = nodes.filter { it.nodeType == "pole" }.toMutableList()
        val outputs = nodes.filter { it.nodeType == "output" }.toMutableList()

        val startX = 120f
        val startY = 140f
        val poleGapY = 160f
        val outputGapX = 130f
        val outputGapY = 90f

        poles.forEachIndexed { index, pole ->
            val idx = nodes.indexOfFirst { it.id == pole.id }
            if (idx >= 0) {
                nodes[idx] = pole.copy(x = startX, y = startY + index * poleGapY)
            }
        }

        if (poles.isNotEmpty()) {
            var outIndex = 0
            poles.forEachIndexed { _, pole ->
                val currentPole = nodes.find { it.id == pole.id } ?: return@forEachIndexed
                val countNearThis = max(1, outputs.size / poles.size)
                repeat(countNearThis) {
                    if (outIndex >= outputs.size) return@repeat
                    val out = outputs[outIndex]
                    val idx = nodes.indexOfFirst { it.id == out.id }
                    if (idx >= 0) {
                        val ox = currentPole.x + 180f + (outIndex % 3) * outputGapX
                        val oy = currentPole.y + ((outIndex % 3) - 1) * outputGapY
                        nodes[idx] = out.copy(x = ox, y = oy)
                    }
                    outIndex++
                }
            }
            while (outIndex < outputs.size) {
                val out = outputs[outIndex]
                val idx = nodes.indexOfFirst { it.id == out.id }
                if (idx >= 0) {
                    val lastPole = nodes.find { it.id == poles.last().id }
                    val ox = (lastPole?.x ?: startX) + 180f + (outIndex % 4) * outputGapX
                    val oy = (lastPole?.y ?: startY) + 120f + (outIndex / 4) * outputGapY
                    nodes[idx] = out.copy(x = ox, y = oy)
                }
                outIndex++
            }
        } else {
            outputs.forEachIndexed { i, out ->
                val idx = nodes.indexOfFirst { it.id == out.id }
                if (idx >= 0) {
                    nodes[idx] = out.copy(
                        x = startX + (i % 4) * outputGapX,
                        y = startY + (i / 4) * outputGapY
                    )
                }
            }
        }

        scaleFactor = 1f
        offsetX = 0f
        offsetY = 0f
        onNodesRepositioned?.invoke(nodes.toList())
        computeFeederColors()
        invalidate()
    }

    private fun screenToWorld(sx: Float, sy: Float): Pair<Float, Float> {
        return (sx - offsetX) / scaleFactor to (sy - offsetY) / scaleFactor
    }

    private fun handleTap(x: Float, y: Float) {
        when (mode) {
            Mode.ADD_POLE -> onNodeAdded?.invoke(x, y, "pole")
            Mode.ADD_OUTPUT -> onNodeAdded?.invoke(x, y, "output")
            Mode.CONNECT -> {
                val tapped = findNodeAt(x, y)
                if (tapped != null) {
                    if (connectFromId == null) {
                        connectFromId = tapped.id
                        selectedNodeId = tapped.id
                        invalidate()
                    } else if (connectFromId != tapped.id) {
                        onConnectionRequested?.invoke(connectFromId!!, tapped.id)
                        connectFromId = null
                        selectedNodeId = null
                        mode = Mode.NONE
                        invalidate()
                    }
                }
            }
            Mode.NONE -> {
                val tapped = findNodeAt(x, y)
                if (tapped != null) {
                    selectedNodeId = tapped.id
                    onNodeSelected?.invoke(tapped)
                    invalidate()
                } else {
                    selectedNodeId = null
                    invalidate()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scaleFactor, scaleFactor)

        // خطوط با رنگ فیدر
        connections.forEach { conn ->
            val from = nodes.find { it.id == conn.fromNodeId }
            val to = nodes.find { it.id == conn.toNodeId }
            if (from != null && to != null) {
                val feederIdx = when {
                    nodeFeederIndex[from.id] != null && nodeFeederIndex[from.id]!! >= 0 ->
                        nodeFeederIndex[from.id]!!
                    nodeFeederIndex[to.id] != null && nodeFeederIndex[to.id]!! >= 0 ->
                        nodeFeederIndex[to.id]!!
                    else -> 0
                }
                linePaint.color = feederColors[feederIdx % feederColors.size]
                canvas.drawLine(from.x, from.y, to.x, to.y, linePaint)
            }
        }

        // نودها
        nodes.forEach { node ->
            when (node.nodeType) {
                "transformer" -> {
                    val w = 70f
                    val h = 50f
                    canvas.drawRect(node.x - w / 2, node.y - h / 2, node.x + w / 2, node.y + h / 2, transformerPaint)
                    canvas.drawCircle(node.x, node.y, 22f, transformerStroke)
                }
                "output" -> {
                    canvas.drawCircle(node.x, node.y, 26f, outputPaint)
                }
                else -> {
                    val fIdx = nodeFeederIndex[node.id] ?: 0
                    if (fIdx >= 0) {
                        polePaint.color = feederColors[fIdx % feederColors.size]
                    } else {
                        polePaint.color = Color.parseColor("#2E7D32")
                    }
                    canvas.drawCircle(node.x, node.y, 34f, polePaint)
                    canvas.drawLine(node.x - 20f, node.y - 20f, node.x + 20f, node.y + 20f, transformerStroke)
                }
            }
            val radius = when (node.nodeType) {
                "output" -> 26f
                "transformer" -> 50f
                else -> 34f
            }

            if (node.id == selectedNodeId || node.id == connectFromId) {
                canvas.drawCircle(node.x, node.y, radius + 7f, selectedPaint)
            }

            val label = when {
                node.nodeType == "output" -> if (node.isThreePhase) "۳فاز" else "۱فاز"
                node.nodeType == "transformer" -> "ترانس"
                else -> {
                    val sp = if (node.singlePhaseCount > 0) "۱ف:${node.singlePhaseCount}" else ""
                    val tp = if (node.threePhaseCount > 0) "۳ف:${node.threePhaseCount}" else ""
                    val phases = listOf(sp, tp).filter { it.isNotEmpty() }.joinToString(" ")
                    if (phases.isNotEmpty()) phases else node.label.ifEmpty { "پایه" }
                }
            }
            canvas.drawText(label, node.x, node.y + radius + 28f, textPaint)
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // اگر در حالت عادی باشیم و passThrough فعال باشد → اجازه بده نقشه حرکت کند
        if (passThroughTouches && mode == Mode.NONE) {
            return false
        }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun findNodeAt(x: Float, y: Float): SchematicNode? {
        return nodes.find { node ->
            val r = when (node.nodeType) { "output" -> 38f; "transformer" -> 60f; else -> 48f }
            hypot(node.x - x, node.y - y) <= r
        }
    }
}
