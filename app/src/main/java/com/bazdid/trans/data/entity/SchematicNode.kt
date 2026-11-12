package com.bazdid.trans.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * گره در نقشه دو‌بعدی فشار ضعیف (پایه یا خروجی)
 */
@Entity(
    tableName = "schematic_nodes",
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
data class SchematicNode(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transformerId: Long,
    val x: Float,                       // مختصات در Canvas
    val y: Float,
    val nodeType: String = "pole",      // "pole" یا "output"
    val label: String = "",
    val isThreePhase: Boolean = false,  // فقط برای output
    val singlePhaseCount: Int = 0,      // فقط برای output
    val threePhaseCount: Int = 0        // فقط برای output
)
