package com.bazdid.trans.data.dao

import androidx.room.*
import com.bazdid.trans.data.entity.Pole
import kotlinx.coroutines.flow.Flow

@Dao
interface PoleDao {

    @Query("SELECT * FROM poles WHERE projectId = :projectId")
    fun getPolesByProject(projectId: Long): Flow<List<Pole>>

    @Query("SELECT * FROM poles WHERE projectId = :projectId")
    suspend fun getPolesByProjectOnce(projectId: Long): List<Pole>

    @Query("SELECT * FROM poles WHERE id = :id")
    suspend fun getById(id: Long): Pole?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pole: Pole): Long

    @Update
    suspend fun update(pole: Pole)

    @Delete
    suspend fun delete(pole: Pole)

    @Query("DELETE FROM poles WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long)
}
