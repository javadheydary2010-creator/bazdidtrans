package com.bazdid.trans.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feeders",
    foreignKeys = [
        ForeignKey(
            entity = Transformer::class,
            parentColumns = ["id"],
            childColumns = ["transformerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("transformerId")]
)
data class Feeder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transformerId: Long,
    val name: String = "",              // فیدر ۱، فیدر ۲، ...
    val singlePhaseCount: Int = 0,      // تعداد انشعاب تک‌فاز
    val threePhaseCount: Int = 0,       // تعداد انشعاب سه‌فاز
    val note: String = ""
) {
    /** بار این فیدر بر حسب کیلووات */
    fun calculateLoadKw(): Double {
        return (threePhaseCount * 8.0) + (singlePhaseCount * 6.0)
    }
}
