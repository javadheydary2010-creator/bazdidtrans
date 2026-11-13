package com.bazdid.trans.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bazdid.trans.data.db.AppDatabase
import com.bazdid.trans.data.entity.SchematicConnection
import com.bazdid.trans.data.entity.SchematicNode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SchematicViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val schematicDao = db.schematicDao()

    private val _transformerId = MutableStateFlow(-1L)

    val nodes: StateFlow<List<SchematicNode>> = _transformerId
        .flatMapLatest { id ->
            if (id == -1L) flowOf(emptyList())
            else schematicDao.getNodes(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connections: StateFlow<List<SchematicConnection>> = _transformerId
        .flatMapLatest { id ->
            if (id == -1L) flowOf(emptyList())
            else schematicDao.getConnections(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun load(transformerId: Long) {
        _transformerId.value = transformerId
    }

    fun addNode(x: Float, y: Float, type: String, isThreePhase: Boolean) {
        viewModelScope.launch {
            val id = _transformerId.value
            if (id == -1L) return@launch
            val label = if (type == "pole") {
                "پایه ${nodes.value.count { it.nodeType == "pole" } + 1}"
            } else {
                if (isThreePhase) "خروجی ۳فاز" else "خروجی ۱فاز"
            }
            schematicDao.insertNode(
                SchematicNode(
                    transformerId = id,
                    x = x, y = y,
                    nodeType = type,
                    label = label,
                    isThreePhase = isThreePhase,
                    singlePhaseCount = if (!isThreePhase && type == "output") 1 else 0,
                    threePhaseCount = if (isThreePhase && type == "output") 1 else 0
                )
            )
        }
    }

    fun addTransformerNode(onCreated: (SchematicNode) -> Unit) {
        viewModelScope.launch {
            val id = _transformerId.value
            if (id == -1L) return@launch
            val existing = nodes.value.find { it.nodeType == "transformer" }
            if (existing != null) {
                onCreated(existing)
                return@launch
            }
            val nodeId = schematicDao.insertNode(
                SchematicNode(
                    transformerId = id,
                    x = 400f, y = 300f,
                    nodeType = "transformer",
                    label = "ترانس"
                )
            )
            val node = schematicDao.getNodeById(nodeId)
            if (node != null) onCreated(node)
        }
    }

    fun addNodeAndConnect(
        x: Float, y: Float, type: String,
        fromNodeId: Long?,
        onCreated: (SchematicNode) -> Unit
    ) {
        viewModelScope.launch {
            val tid = _transformerId.value
            if (tid == -1L) return@launch
            val label = "پایه ${nodes.value.count { it.nodeType == "pole" } + 1}"
            val nodeId = schematicDao.insertNode(
                SchematicNode(
                    transformerId = tid,
                    x = x, y = y,
                    nodeType = type,
                    label = label
                )
            )
            if (fromNodeId != null) {
                schematicDao.insertConnection(
                    SchematicConnection(
                        transformerId = tid,
                        fromNodeId = fromNodeId,
                        toNodeId = nodeId
                    )
                )
            }
            val node = schematicDao.getNodeById(nodeId)
            if (node != null) onCreated(node)
        }
    }

    fun addConnection(fromId: Long, toId: Long) {
        viewModelScope.launch {
            val id = _transformerId.value
            if (id == -1L) return@launch
            schematicDao.insertConnection(
                SchematicConnection(transformerId = id, fromNodeId = fromId, toNodeId = toId)
            )
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            val id = _transformerId.value
            if (id == -1L) return@launch
            schematicDao.deleteConnectionsByTransformer(id)
            schematicDao.deleteNodesByTransformer(id)
        }
    }

    fun updateNodePositions(updatedNodes: List<SchematicNode>) {
        viewModelScope.launch {
            updatedNodes.forEach { schematicDao.updateNode(it) }
        }
    }

    fun updateNodePhases(node: SchematicNode, single: Int, three: Int) {
        viewModelScope.launch {
            schematicDao.updateNode(
                node.copy(
                    singlePhaseCount = single.coerceAtLeast(0),
                    threePhaseCount = three.coerceAtLeast(0),
                    isThreePhase = three > 0
                )
            )
        }
    }

    fun deleteNode(node: SchematicNode) {
        viewModelScope.launch {
            schematicDao.deleteConnectionsForNode(node.id)
            schematicDao.deleteNode(node)
        }
    }

    fun zeroUnsetPhases() {
        viewModelScope.launch {
            nodes.value.forEach { node ->
                if (node.nodeType == "pole") {
                    // مقادیر فعلی حفظ می‌شوند؛ اگر null نبود همان می‌ماند
                    // این متد برای اطمینان از غیرمنفی بودن است
                    schematicDao.updateNode(
                        node.copy(
                            singlePhaseCount = node.singlePhaseCount.coerceAtLeast(0),
                            threePhaseCount = node.threePhaseCount.coerceAtLeast(0)
                        )
                    )
                }
            }
        }
    }

}
