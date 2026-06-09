package com.pointage.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.pointage.app.data.dao.EmployeeDao
import com.pointage.app.data.dao.ExportDao
import com.pointage.app.data.dao.FaceSignatureDao
import com.pointage.app.data.dao.PointageDao
import com.pointage.app.data.model.Employee
import com.pointage.app.data.model.FaceSignature
import com.pointage.app.data.model.MethodeAuthentification
import com.pointage.app.data.model.Pointage
import com.pointage.app.data.model.TypePointage

class Converters {
    @TypeConverter
    fun fromTypePointage(value: TypePointage): String = value.name

    @TypeConverter
    fun toTypePointage(value: String): TypePointage = TypePointage.valueOf(value)

    @TypeConverter
    fun fromMethode(value: MethodeAuthentification): String = value.name

    @TypeConverter
    fun toMethode(value: String): MethodeAuthentification = MethodeAuthentification.valueOf(value)
}

@Database(entities = [Employee::class, Pointage::class, FaceSignature::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun pointageDao(): PointageDao
    abstract fun faceSignatureDao(): FaceSignatureDao
    abstract fun exportDao(): ExportDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pointage_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
