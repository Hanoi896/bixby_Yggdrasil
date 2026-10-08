package com.huginmunin.app.munin.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [LogEntity::class], version = 1, exportSchema = false)
abstract class MuninDatabase : RoomDatabase() {
    abstract fun muninDao(): MuninDao
}
