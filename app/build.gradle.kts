/*
 * 应用级 Gradle 构建脚本。
 *
 * 核心配置：
 * - 插件：Android、Kotlin、Hilt（依赖注入）、kapt、kotlin-parcelize
 * - 构建特性：ViewBinding、DataBinding、BuildConfig
 * - 依赖：Jetpack（Navigation/Room/Lifecycle）、Retrofit、Glide、高德地图、测试框架
 * - BASE_URL 从环境变量注入，AMAP_API_KEY 从 local.properties 读取
 */
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.hilt)
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

import java.util.Properties

android {
    namespace = "com.example.campus"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.campus"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val baseUrlRaw = System.getenv("CAMPUS_BASE_URL") ?: "http://10.0.2.2/campus_api/"
        val baseUrl = if (baseUrlRaw.endsWith("/")) baseUrlRaw else "$baseUrlRaw/"
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")

        val localProps = Properties().apply {
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) {
                localPropsFile.inputStream().use { load(it) }
            }
        }
        val amapKey = localProps.getProperty("AMAP_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getenv("AMAP_API_KEY")
            ?: "REPLACE_WITH_YOUR_AMAP_KEY"
        manifestPlaceholders += mapOf("AMAP_API_KEY" to amapKey)

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
        dataBinding = true
    }
    signingConfigs {
        create("release") {
            val localProps = Properties().apply {
                val localPropsFile = rootProject.file("local.properties")
                if (localPropsFile.exists()) {
                    localPropsFile.inputStream().use { load(it) }
                }
            }

            fun prop(name: String): String? =
                (localProps.getProperty(name)?.takeIf { it.isNotBlank() } ?: System.getenv(name))

            val storeFilePath = prop("RELEASE_STORE_FILE") ?: "D:/Android/bin/mykeys.jks"
            storeFile = file(storeFilePath)
            storePassword = prop("RELEASE_STORE_PASSWORD")
            keyAlias = prop("RELEASE_KEY_ALIAS")
            keyPassword = prop("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            val releaseConfig = signingConfigs.findByName("release")
            if (
                releaseConfig != null &&
                !releaseConfig.keyAlias.isNullOrBlank() &&
                !releaseConfig.storePassword.isNullOrBlank() &&
                !releaseConfig.keyPassword.isNullOrBlank()
            ) {
                signingConfig = releaseConfig
            }
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Glide
    implementation(libs.glide)
    kapt(libs.glide.compiler)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // UI layouts
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")

    implementation("com.amap.api:3dmap-location-search:10.1.600_loc6.5.1_sea9.7.4")

    testImplementation(libs.junit)
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.robolectric:robolectric:4.12.2")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
