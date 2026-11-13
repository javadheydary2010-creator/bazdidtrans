package com.bazdid.trans.data.dao

import androidx.room.*
import com.bazdid.trans.data.entity.SchematicConnection
import com.bazdid.trans.data.entity.SchematicNode
import kotlinx.coroutines.flow.Flow

@Dao
interface SchematicDao {

    @Query("SELECT * FROM schematic_nodes WHERE transformerId = :transformerId")
    fun getNodes(transformerId: Long): Flow<List<SchematicNode>>

    @Query("SELECT * FROM schematic_nodes WHERE transformerId = :transformerId")
    suspend fun getNodesOnce(transformerId: Long): List<SchematicNode>

    @Query("SELECT * FROM schematic_connections WHERE transformerId = :transformerId")
    fun getConnections(transformerId: Long): Flow<List<SchematicConnection>>

    @Query("SELECT * FROM schematic_connections WHERE transformerId = :transformerId")
    suspend fun getConnectionsOnce(transformerId: Long): List<SchematicConnection>

    @Query("SELECT * FROM schematic_nodes WHERE id = :id")
    suspend fun getNodeById(id: Long): SchematicNode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: SchematicNode): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: SchematicConnection): Long

    @Update
    suspend fun updateNode(node: SchematicNode)

    @Delete
    suspend fun deleteNode(node: SchematicNode)

    @Delete
    suspend fun deleteConnection(connection: SchematicConnection)

    @Query("DELETE FROM schematic_nodes WHERE transformerId = :transformerId")
    suspend fun deleteNodesByTransformer(transformerId: Long)

    @Query("DELETE FROM schematic_connections WHERE transformerId = :transformerId")
    suspend fun deleteConnectionsByTransformer(transformerId: Long)

    @Query("DELETE FROM schematic_connections WHERE fromNodeId = :nodeId OR toNodeId = :nodeId")
    suspend fun deleteConnectionsForNode(nodeId: Long)

    @Delete
    suspend fun deleteNode(node: SchematicNode)

}
