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

    private val polePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        style = Paint.Style.FILL
    }
    private val outputPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F9A825")
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        strokeWidth = 5f
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

    var onNodeAdded: ((Float, Float, String) -> Unit)? = null
    var onConnectionRequested: ((Long, Long) -> Unit)? = null
    var onNodesRepositioned: ((List<SchematicNode>) -> Unit)? = null
    var onNodeSelected: ((SchematicNode) -> Unit)? = null

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
        invalidate()
    }

    fun setMode(newMode: Mode) {
        mode = newMode
        connectFromId = null
        selectedNodeId = null
        invalidate()
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

        // قرار دادن پایه‌ها در سمت چپ به صورت ستونی
        poles.forEachIndexed { index, pole ->
            val idx = nodes.indexOfFirst { it.id == pole.id }
            if (idx >= 0) {
                nodes[idx] = pole.copy(x = startX, y = startY + index * poleGapY)
            }
        }

        // خروجی‌ها را در سمت راست پایه‌ها پخش می‌کنیم
        if (poles.isNotEmpty()) {
            var outIndex = 0
            poles.forEachIndexed { pIndex, pole ->
                val currentPole = nodes.find { it.id == pole.id } ?: return@forEachIndexed
                // چند خروجی نزدیک به این پایه
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
            // باقی‌مانده خروجی‌ها
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
            // فقط خروجی
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

        // ریست زوم و مرکز کردن
        scaleFactor = 1f
        offsetX = 0f
        offsetY = 0f
        onNodesRepositioned?.invoke(nodes.toList())
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
                        invalidate()
                    }
                }
            }
            Mode.NONE -> {
                val tapped = findNodeAt(x, y)
                selectedNodeId = tapped?.id
                tapped?.let { onNodeSelected?.invoke(it) }
                invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scaleFactor, scaleFactor)

        // خطوط
        connections.forEach { conn ->
            val from = nodes.find { it.id == conn.fromNodeId }
            val to = nodes.find { it.id == conn.toNodeId }
            if (from != null && to != null) {
                canvas.drawLine(from.x, from.y, to.x, to.y, linePaint)
            }
        }

        // نودها
        nodes.forEach { node ->
            val paint = if (node.nodeType == "output") outputPaint else polePaint
            val radius = if (node.nodeType == "output") 26f else 34f
            canvas.drawCircle(node.x, node.y, radius, paint)

            if (node.id == selectedNodeId || node.id == connectFromId) {
                canvas.drawCircle(node.x, node.y, radius + 7f, selectedPaint)
            }

            val label = when {
                node.nodeType == "output" -> if (node.isThreePhase) "۳فاز" else "۱فاز"
                else -> node.label.ifEmpty { "پایه" }
            }
            canvas.drawText(label, node.x, node.y + radius + 28f, textPaint)
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun findNodeAt(x: Float, y: Float): SchematicNode? {
        return nodes.find { node ->
            val r = if (node.nodeType == "output") 38f else 48f
            hypot(node.x - x, node.y - y) <= r
        }
    }
}
