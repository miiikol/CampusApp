package com.example.campus.core.di

import android.app.Application
import androidx.room.Room
import com.example.campus.core.common.Constants
import com.example.campus.data.local.AppDatabase
import com.example.campus.data.local.dao.*
import com.example.campus.data.remote.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 应用级依赖注入模块，提供网络与数据库相关的单例依赖。
 *
 * 内容：
 * - OkHttpClient（含日志拦截器与超时设置）
 * - Retrofit（绑定基础 URL 与 Gson 转换器）
 * - Room 数据库与各 DAO
 *
 * 注意：
 * - 日志拦截器级别为 BODY，仅用于开发调试；生产环境应降低或移除，避免输出敏感信息
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(client: OkHttpClient): ApiService {
        return Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(app: Application): AppDatabase {
        return Room.databaseBuilder(
            app,
            AppDatabase::class.java,
            Constants.DATABASE_NAME
        ).addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    @Singleton
    fun provideCourseDao(db: AppDatabase): CourseDao = db.courseDao()

    @Provides
    @Singleton
    fun provideNewsDao(db: AppDatabase): NewsDao = db.newsDao()

    @Provides
    @Singleton
    fun provideMarketDao(db: AppDatabase): MarketDao = db.marketDao()

    @Provides
    @Singleton
    fun provideMarketFavoriteDao(db: AppDatabase): MarketFavoriteDao = db.marketFavoriteDao()

    @Provides
    @Singleton
    fun provideLostFoundDao(db: AppDatabase): LostFoundDao = db.lostFoundDao()

    @Provides
    @Singleton
    fun provideNotificationDao(db: AppDatabase): NotificationDao = db.notificationDao()
}
