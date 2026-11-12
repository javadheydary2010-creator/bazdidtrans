package com.bazdid.trans.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bazdid.trans.data.db.AppDatabase
import com.bazdid.trans.data.entity.Pole
import com.bazdid.trans.data.entity.PowerLine
import com.bazdid.trans.data.entity.Transformer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)

    private val _projectId = MutableStateFlow(-1L)

    val poles = _projectId.flatMapLatest { id ->
        if (id == -1L) flowOf(emptyList())
        else db.poleDao().getPolesByProject(id)
    }

    val transformers = _projectId.flatMapLatest { id ->
        if (id == -1L) flowOf(emptyList())
        else db.transformerDao().getTransformersByProject(id)
    }

    val lines = _projectId.flatMapLatest { id ->
        if (id == -1L) flowOf(emptyList())
        else db.powerLineDao().getLinesByProject(id)
    }

    fun loadProject(projectId: Long) {
        _projectId.value = projectId
    }

    fun addPole(projectId: Long, lat: Double, lng: Double) {
        viewModelScope.launch {
            val count = db.poleDao().getPolesByProjectOnce(projectId).size + 1
            val pole = Pole(
                projectId = projectId,
                name = "پایه $count",
                latitude = lat,
                longitude = lng
            )
            db.poleDao().insert(pole)
        }
    }

    fun addTransformer(projectId: Long, lat: Double, lng: Double) {
        viewModelScope.launch {
            val count = db.transformerDao().getTransformersByProjectOnce(projectId).size + 1
            val tr = Transformer(
                projectId = projectId,
                name = "ترانس $count",
                latitude = lat,
                longitude = lng,
                feederCount = 1
            )
            val id = db.transformerDao().insert(tr)
            db.feederDao().insert(
                com.bazdid.trans.data.entity.Feeder(
                    transformerId = id,
                    name = "فیدر ۱",
                    singlePhaseCount = 0,
                    threePhaseCount = 0
                )
            )
        }
    }

    fun addLine(
        projectId: Long,
        fromType: String, fromId: Long,
        toType: String, toId: Long
    ) {
        viewModelScope.launch {
            db.powerLineDao().insert(
                PowerLine(
                    projectId = projectId,
                    fromType = fromType,
                    fromId = fromId,
                    toType = toType,
                    toId = toId,
                    lineType = "LV"
                )
            )
        }
    }

    fun deletePole(pole: Pole) {
        viewModelScope.launch {
            db.poleDao().delete(pole)
        }
    }
}
