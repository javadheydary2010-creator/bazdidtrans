package com.bazdid.trans.data.dao

import androidx.room.*
import com.bazdid.trans.data.entity.Feeder
import kotlinx.coroutines.flow.Flow

@Dao
interface FeederDao {

    @Query("SELECT * FROM feeders WHERE transformerId = :transformerId ORDER BY id ASC")
    fun getFeedersByTransformer(transformerId: Long): Flow<List<Feeder>>

    @Query("SELECT * FROM feeders WHERE transformerId = :transformerId ORDER BY id ASC")
    suspend fun getFeedersByTransformerOnce(transformerId: Long): List<Feeder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feeder: Feeder): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(feeders: List<Feeder>)

    @Update
    suspend fun update(feeder: Feeder)

    @Delete
    suspend fun delete(feeder: Feeder)

    @Query("DELETE FROM feeders WHERE transformerId = :transformerId")
    suspend fun deleteByTransformer(transformerId: Long)
}
