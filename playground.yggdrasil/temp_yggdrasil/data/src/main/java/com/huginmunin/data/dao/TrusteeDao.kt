package com.huginmunin.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.huginmunin.data.entity.TrusteeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrusteeDao {
    @Query("SELECT * FROM trustees")
    fun getAllTrustees(): Flow<List<TrusteeEntity>>

    @Insert
    suspend fun insertTrustee(trustee: TrusteeEntity)

    @Delete
    suspend fun deleteTrustee(trustee: TrusteeEntity)

    @Query("DELETE FROM trustees WHERE id = :id")
    suspend fun deleteTrusteeById(id: Long)
}
