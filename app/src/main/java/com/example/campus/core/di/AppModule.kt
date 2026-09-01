package com.example.campus.core.di

import android.app.Application
import androidx.room.Room
import com.example.campus.BuildConfig
import com.example.campus.core.common.Constants
import com.example.campus.data.local.AppDatabase
import com.example.campus.data.local.dao.*
import com.example.campus.data.remote.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 应用级依赖注入模块，提供网络与数据库相关的单例依赖。
 * 内容：
 * - OkHttpClient（含日志拦截器与超时设置）
 * - Retrofit（绑定基础 URL 与 Gson 转换器）
 * - Room 数据库与各 DAO
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(userDao: UserDao): OkHttpClient {
        // 连接/读取/写入统一设置 30 秒超时，避免请求长时间挂起
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // 认证拦截器：从本地 Room 读取当前用户 token，附加到所有请求的 Authorization 头。
        // 后端受保护接口（课表/资讯/二手/通知等）依赖 Bearer Token 鉴权；未登录时不附加。
        // 注意：本拦截器运行在 OkHttp 线程池（非主线程），runBlocking 不会阻塞 UI。
        builder.addInterceptor { chain ->
            val token = runBlocking {
                runCatching { userDao.getCurrentUser()?.token }.getOrNull()
            }
            if (token.isNullOrBlank()) {
                chain.proceed(chain.request())
            } else {
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                )
            }
        }

        // Debug 构建输出 BODY 日志以便调试；Release 仅输出 HEADERS，不泄露敏感数据
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
        } else {
            // Release 构建启用 SSL Certificate Pinning，防止中间人攻击
            builder.certificatePinner(
                CertificatePinner.Builder()
                    // 预置泛域名 pin；部署前请将 hash 替换为你 HTTPS 证书的 SHA256 指纹
                    // 获取方式: openssl s_client -connect your-api.com:443 </dev/null 2>/dev/null | openssl x509 -noout -pubkey | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
                    // .add("your-api.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                    .build()
            )
        }

        return builder.build()
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
