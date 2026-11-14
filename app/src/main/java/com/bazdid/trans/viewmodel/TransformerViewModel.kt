package com.bazdid.trans.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bazdid.trans.data.db.AppDatabase
import com.bazdid.trans.data.entity.Feeder
import com.bazdid.trans.data.entity.Transformer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TransformerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)

    private val _transformerId = MutableStateFlow(-1L)

    val transformer: StateFlow<Transformer?> = _transformerId
        .flatMapLatest { id ->
            if (id == -1L) flowOf<Transformer?>(null)
            else flow {
                emit(db.transformerDao().getById(id))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val feeders: StateFlow<List<Feeder>> = _transformerId
        .flatMapLatest { id ->
            if (id == -1L) flowOf(emptyList())
            else db.feederDao().getFeedersByTransformer(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun load(transformerId: Long) {
        _transformerId.value = transformerId
    }

    fun updateTransformer(name: String, capacityKva: Double?, note: String) {
        viewModelScope.launch {
            val current = transformer.value ?: return@launch
            db.transformerDao().update(
                current.copy(
                    name = name,
                    capacityKva = capacityKva,
                    note = note
                )
            )
            // Refresh
            _transformerId.value = current.id
        }
    }

    fun addFeeder() {
        viewModelScope.launch {
            val id = _transformerId.value
            if (id == -1L) return@launch
            val count = feeders.value.size + 1
            db.feederDao().insert(
                Feeder(
                    transformerId = id,
                    name = "فیدر $count",
                    singlePhaseCount = 0,
                    threePhaseCount = 0
                )
            )
        }
    }

    /**
     * اعمال نتایج محاسبه‌شده از کروکی روی فیدرها
     * اگر فیدری وجود نداشته باشد یکی می‌سازد، در غیر این صورت اولی را به‌روز می‌کند
     * و در صورت نیاز فیدرهای خالی اضافی را پاک نمی‌کند تا داده‌های دستی حفظ شوند.
     */
    /**
     * اعمال نتایج کروکی به صورت جداگانه برای هر فیدر
     * @param feederStats لیست جفت‌های (تک‌فاز، سه‌فاز) به ترتیب فیدر ۱، ۲، ...
     */
    fun applySchematicFeederStats(feederStats: List<Pair<Int, Int>>) {
        viewModelScope.launch {
            val id = _transformerId.value
            if (id == -1L) return@launch
            if (feederStats.isEmpty()) return@launch

            val currentFeeders = feeders.value.toMutableList()

            feederStats.forEachIndexed { index, (single, three) ->
                if (index < currentFeeders.size) {
                    val f = currentFeeders[index]
                    db.feederDao().update(
                        f.copy(
                            name = "فیدر ${index + 1} (کروکی)",
                            singlePhaseCount = single.coerceAtLeast(0),
                            threePhaseCount = three.coerceAtLeast(0)
                        )
                    )
                } else {
                    db.feederDao().insert(
                        Feeder(
                            transformerId = id,
                            name = "فیدر ${index + 1} (کروکی)",
                            singlePhaseCount = single.coerceAtLeast(0),
                            threePhaseCount = three.coerceAtLeast(0)
                        )
                    )
                }
            }
            // فیدرهای اضافی قبلی را صفر کن (نه حذف، تا دستی‌ها حفظ شوند)
            if (currentFeeders.size > feederStats.size) {
                currentFeeders.drop(feederStats.size).forEach { f ->
                    db.feederDao().update(
                        f.copy(singlePhaseCount = 0, threePhaseCount = 0, name = f.name.replace(" (کروکی)", ""))
                    )
                }
            }
        }
    }

    fun applySchematicTotals(single: Int, three: Int) {
        // سازگاری با نسخه قبلی: همه در یک فیدر
        applySchematicFeederStats(listOf(single to three))
    }

    fun updateFeederCounts(feeder: Feeder, single: Int, three: Int) {
        viewModelScope.launch {
            if (feeder.singlePhaseCount != single || feeder.threePhaseCount != three) {
                db.feederDao().update(
                    feeder.copy(
                        singlePhaseCount = single.coerceAtLeast(0),
                        threePhaseCount = three.coerceAtLeast(0)
                    )
                )
            }
        }
    }

    fun deleteFeeder(feeder: Feeder) {
        viewModelScope.launch {
            db.feederDao().delete(feeder)
        }
    }

    fun deleteTransformer() {
        viewModelScope.launch {
            val current = transformer.value ?: return@launch
            db.transformerDao().delete(current)
        }
    }
}
