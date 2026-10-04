package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.AuthorizedDeviceDao
import com.example.data.dao.HostConfigDao
import com.example.data.dao.SessionLogDao
import com.example.data.entity.AuthorizedDeviceEntity
import com.example.data.entity.HostConfigEntity
import com.example.data.entity.SessionLogEntity

@Database(
    entities = [
        AuthorizedDeviceEntity::class,
        SessionLogEntity::class,
        HostConfigEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun authorizedDeviceDao(): AuthorizedDeviceDao
    abstract fun sessionLogDao(): SessionLogDao
    abstract fun hostConfigDao(): HostConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ubaid_host.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
