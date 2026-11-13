package com.bazdid.trans.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bazdid.trans.data.db.AppDatabase
import com.bazdid.trans.data.entity.Project
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

    val transformers = _projectId.flatMapLatest { id ->
        if (id == -1L) flowOf(emptyList())
        else db.transformerDao().getTransformersByProject(id)
    }

    fun loadProject(projectId: Long) {
        _projectId.value = projectId
    }

    /** اگر پروژه‌ای نباشد، یکی پیش‌فرض می‌سازد */
    fun ensureDefaultProject(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val existing = db.projectDao().getAllProjectsOnce()
            val id = if (existing.isNotEmpty()) {
                existing.first().id
            } else {
                db.projectDao().insert(
                    Project(name = "بازدید پیش‌فرض", description = "")
                )
            }
            _projectId.value = id
            onReady(id)
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
}
