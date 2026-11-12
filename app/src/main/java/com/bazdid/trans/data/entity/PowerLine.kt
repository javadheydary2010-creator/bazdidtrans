package com.bazdid.trans.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "power_lines",
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
data class PowerLine(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val fromType: String,           // "pole" یا "transformer"
    val fromId: Long,
    val toType: String,             // "pole" یا "transformer"
    val toId: Long,
    val lineType: String = "LV",    // "MV" یا "LV"
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
