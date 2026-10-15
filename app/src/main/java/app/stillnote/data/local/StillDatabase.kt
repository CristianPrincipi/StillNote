package app.stillnote.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Version 2 adds TaskRow.edited. Each version's schema is in app/schemas, which the migrations are made from.
@Database(entities = [TaskRow::class, ProjectRow::class], version = 2, autoMigrations = [AutoMigration(from = 1, to = 2)])
abstract class StillDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao

    companion object {
        fun open(context: Context): StillDatabase =
            Room.databaseBuilder(context, StillDatabase::class.java, "stillnote.db").build()
    }
}
