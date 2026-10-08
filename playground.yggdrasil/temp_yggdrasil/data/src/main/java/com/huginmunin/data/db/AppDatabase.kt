package com.huginmunin.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.huginmunin.data.dao.LogDao
import com.huginmunin.data.dao.PermissionDao
import com.huginmunin.data.dao.TrusteeDao
import com.huginmunin.data.entity.LogEntity
import com.huginmunin.data.entity.PermissionEntity
import com.huginmunin.data.entity.TrusteeEntity

@Database(
    entities = [
        LogEntity::class, 
        PermissionEntity::class, 
        TrusteeEntity::class
    ], 
    version = 1,  // Reset version to 1 since we removed entities
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
    abstract fun permissionDao(): PermissionDao
    abstract fun trusteeDao(): TrusteeDao
}
