package com.bazdid.trans.data.dao

import androidx.room.*
import com.bazdid.trans.data.entity.Transformer
import kotlinx.coroutines.flow.Flow

@Dao
interface TransformerDao {

    @Query("SELECT * FROM transformers WHERE projectId = :projectId")
    fun getTransformersByProject(projectId: Long): Flow<List<Transformer>>

    @Query("SELECT * FROM transformers WHERE projectId = :projectId")
    suspend fun getTransformersByProjectOnce(projectId: Long): List<Transformer>

    @Query("SELECT * FROM transformers WHERE id = :id")
    suspend fun getById(id: Long): Transformer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transformer: Transformer): Long

    @Update
    suspend fun update(transformer: Transformer)

    @Delete
    suspend fun delete(transformer: Transformer)

    @Query("DELETE FROM transformers WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long)
}
