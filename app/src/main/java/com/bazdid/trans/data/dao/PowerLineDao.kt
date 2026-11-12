package com.bazdid.trans.data.dao

import androidx.room.*
import com.bazdid.trans.data.entity.PowerLine
import kotlinx.coroutines.flow.Flow

@Dao
interface PowerLineDao {

    @Query("SELECT * FROM power_lines WHERE projectId = :projectId")
    fun getLinesByProject(projectId: Long): Flow<List<PowerLine>>

    @Query("SELECT * FROM power_lines WHERE projectId = :projectId")
    suspend fun getLinesByProjectOnce(projectId: Long): List<PowerLine>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(line: PowerLine): Long

    @Update
    suspend fun update(line: PowerLine)

    @Delete
    suspend fun delete(line: PowerLine)

    @Query("DELETE FROM power_lines WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long)
}
