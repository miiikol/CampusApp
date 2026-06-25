package com.example.campus.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.campus.data.local.dao.*
import com.example.campus.data.local.entity.*

/**
 * 应用本地数据库（Room）。
 *
 * 说明：
 * - 作为本地缓存与部分强本地数据的承载（用户/课表/资讯/二手/失物招领）
 * - 通过 DAO 对外暴露数据访问能力，UI 层不得直接操作数据库
 *
 * 配置：
 * - version = 1：当前未做迁移脚本；升级版本时应补充 Migration 或调整策略
 * - exportSchema = false：若需要数据库版本演进记录，可改为 true 并配置 schemaLocation
 */
@Database(
    entities = [
        UserEntity::class,
        CourseEntity::class,
        NewsEntity::class,
        MarketEntity::class,
        MarketFavoriteEntity::class,
        LostFoundEntity::class,
        NotificationEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun courseDao(): CourseDao
    abstract fun newsDao(): NewsDao
    abstract fun marketDao(): MarketDao
    abstract fun marketFavoriteDao(): MarketFavoriteDao
    abstract fun lostFoundDao(): LostFoundDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN color INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE courses ADD COLUMN isRemote INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE courses ADD COLUMN baseCourseId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE courses ADD COLUMN onlyWeek INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'student'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE market_items ADD COLUMN status TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notifications_local (
                      id INTEGER NOT NULL PRIMARY KEY,
                      userId TEXT NOT NULL,
                      type TEXT NOT NULL,
                      title TEXT NOT NULL,
                      content TEXT NOT NULL,
                      relatedType TEXT,
                      relatedId TEXT,
                      isRead INTEGER NOT NULL,
                      createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_local_userId_id ON notifications_local(userId, id)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS market_favorites (
                      userId TEXT NOT NULL,
                      itemId TEXT NOT NULL,
                      createdAt INTEGER NOT NULL,
                      PRIMARY KEY(userId, itemId)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_market_favorites_userId_createdAt ON market_favorites(userId, createdAt)")
            }
        }
    }
}
