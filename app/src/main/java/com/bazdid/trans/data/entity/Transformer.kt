package com.bazdid.trans.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transformers",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class Transformer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val name: String = "",                  // کد یا نام ترانس
    val latitude: Double,
    val longitude: Double,
    val capacityKva: Double? = null,        // ظرفیت ترانس (اختیاری)
    val feederCount: Int = 1,               // تعداد فیدر
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    // محاسبه بار کل بر اساس فیدرها (در لایه بالاتر انجام می‌شود)
}
