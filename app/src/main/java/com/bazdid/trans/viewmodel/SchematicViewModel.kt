package com.bazdid.trans.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bazdid.trans.data.db.AppDatabase
import com.bazdid.trans.data.entity.SchematicConnection
import com.bazdid.trans.data.entity.SchematicNode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
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
                    x = x,
                    y = y,
                    nodeType = type,
                    label = label,
                    isThreePhase = isThreePhase,
                    singlePhaseCount = if (!isThreePhase && type == "output") 1 else 0,
                    threePhaseCount = if (isThreePhase && type == "output") 1 else 0
                )
            )
        }
    }

    fun addConnection(fromId: Long, toId: Long) {
        viewModelScope.launch {
            val id = _transformerId.value
            if (id == -1L) return@launch
            schematicDao.insertConnection(
                SchematicConnection(
                    transformerId = id,
                    fromNodeId = fromId,
                    toNodeId = toId
                )
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

    fun updateNodePositions(updatedNodes: List<com.bazdid.trans.data.entity.SchematicNode>) {
        viewModelScope.launch {
            updatedNodes.forEach { node ->
                schematicDao.updateNode(node)
            }
        }
    }
}
