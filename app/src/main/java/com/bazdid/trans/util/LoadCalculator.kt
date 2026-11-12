package com.bazdid.trans.util

import com.bazdid.trans.data.entity.Feeder

object LoadCalculator {

    const val THREE_PHASE_KW = 8.0
    const val SINGLE_PHASE_KW = 6.0
    const val POWER_FACTOR = 0.85

    /**
     * محاسبه بار کل بر اساس لیست فیدرها (کیلووات)
     */
    fun calculateTotalLoad(feeders: List<Feeder>): Double {
        return feeders.sumOf { it.calculateLoadKw() }
    }

    /**
     * محاسبه بار از تعداد مستقیم (کیلووات)
     */
    fun calculateLoad(singlePhase: Int, threePhase: Int): Double {
        return (threePhase * THREE_PHASE_KW) + (singlePhase * SINGLE_PHASE_KW)
    }

    /**
     * تبدیل کیلووات به کیلوولت‌آمپر تقریبی
     */
    fun kwToKva(kw: Double): Double {
        return kw / POWER_FACTOR
    }

    /**
     * درصد بارگیری ترانس
     * @param loadKw بار محاسبه شده بر حسب کیلووات
     * @param capacityKva ظرفیت ترانس بر حسب کیلوولت‌آمپر
     */
    fun calculateLoadPercentage(loadKw: Double, capacityKva: Double?): Double? {
        if (capacityKva == null || capacityKva <= 0) return null
        val capacityKw = capacityKva * POWER_FACTOR
        return (loadKw / capacityKw) * 100.0
    }

    fun formatLoad(kw: Double): String {
        return String.format("%.1f کیلووات", kw)
    }

    /** نمایش همزمان کیلووات و کیلوولت‌آمپر */
    fun formatLoadBoth(kw: Double): String {
        val kva = kwToKva(kw)
        return String.format("%.1f kW  (≈ %.1f kVA)", kw, kva)
    }

    fun formatPercentage(percent: Double?): String {
        return if (percent == null) "—٪"
        else String.format("%.0f٪", percent)
    }
}
