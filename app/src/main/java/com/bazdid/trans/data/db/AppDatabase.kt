package com.bazdid.trans.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bazdid.trans.data.dao.*
import com.bazdid.trans.data.entity.*

@Database(
    entities = [
        Project::class,
        Pole::class,
        Transformer::class,
        Feeder::class,
        PowerLine::class,
        SchematicNode::class,
        SchematicConnection::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun poleDao(): PoleDao
    abstract fun transformerDao(): TransformerDao
    abstract fun feederDao(): FeederDao
    abstract fun powerLineDao(): PowerLineDao
    abstract fun schematicDao(): SchematicDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bazdid_trans.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
