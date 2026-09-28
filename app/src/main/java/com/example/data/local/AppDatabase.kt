package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.AIActionDao
import com.example.data.local.dao.AttendanceDao
import com.example.data.local.dao.DocumentDao
import com.example.data.local.dao.FriendChatDao
import com.example.data.local.dao.FriendshipDao
import com.example.data.local.dao.HolidayExceptionDao
import com.example.data.local.dao.NoticeDao
import com.example.data.local.dao.RoutineDao
import com.example.data.local.dao.RoutineVersionDao
import com.example.data.local.dao.SettingsDao
import com.example.data.local.dao.SharedDocumentDao
import com.example.data.local.dao.SubjectDao
import com.example.data.local.dao.SyllabusDao
import com.example.data.local.dao.TaskDao
import com.example.data.local.dao.UserDao
import com.example.data.local.entities.AcademicTaskEntity
import com.example.data.local.entities.AIActionEntity
import com.example.data.local.entities.AppSettingsEntity
import com.example.data.local.entities.AttendanceRecordEntity
import com.example.data.local.entities.ClassScheduleEntity
import com.example.data.local.entities.DocumentEntity
import com.example.data.local.entities.FriendChatMessageEntity
import com.example.data.local.entities.FriendshipEntity
import com.example.data.local.entities.HolidayExceptionEntity
import com.example.data.local.entities.NoticeEntity
import com.example.data.local.entities.RoutineVersionEntity
import com.example.data.local.entities.SharedDocumentEntity
import com.example.data.local.entities.SubjectEntity
import com.example.data.local.entities.SyllabusTopicEntity
import com.example.data.local.entities.UserEntity

@Database(
    entities = [
        SubjectEntity::class,
        ClassScheduleEntity::class,
        AttendanceRecordEntity::class,
        HolidayExceptionEntity::class,
        SyllabusTopicEntity::class,
        DocumentEntity::class,
        NoticeEntity::class,
        AcademicTaskEntity::class,
        AppSettingsEntity::class,
        UserEntity::class,
        FriendshipEntity::class,
        FriendChatMessageEntity::class,
        SharedDocumentEntity::class,
        AIActionEntity::class,
        RoutineVersionEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun routineDao(): RoutineDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun holidayExceptionDao(): HolidayExceptionDao
    abstract fun syllabusDao(): SyllabusDao
    abstract fun documentDao(): DocumentDao
    abstract fun noticeDao(): NoticeDao
    abstract fun taskDao(): TaskDao
    abstract fun settingsDao(): SettingsDao
    abstract fun userDao(): UserDao
    abstract fun friendshipDao(): FriendshipDao
    abstract fun friendChatDao(): FriendChatDao
    abstract fun sharedDocumentDao(): SharedDocumentDao
    abstract fun aiActionDao(): AIActionDao
    abstract fun routineVersionDao(): RoutineVersionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "studymate_database.db"
            )
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build()
        }
    }
}
