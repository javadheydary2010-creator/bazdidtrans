package com.bazdid.trans.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schematic_connections",
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
data class SchematicConnection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transformerId: Long,
    val fromNodeId: Long,
    val toNodeId: Long
)
