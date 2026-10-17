plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 签名信息：本地用 keystore.properties（不入库），CI 用环境变量
import java.util.Properties
val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.jinge.moviepilot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jinge.moviepilot"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "0.4.1"
    }

    signingConfigs {
        getByName("debug")
        create("release") {
            storeFile = rootProject.file(
                ksProps.getProperty("storeFile")
                    ?: System.getenv("KEYSTORE_PATH")
                    ?: "keystore/moviepilot-release.jks"
            )
            storePassword = ksProps.getProperty("storePassword")
                ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = ksProps.getProperty("keyAlias")
                ?: System.getenv("KEYSTORE_ALIAS") ?: "moviepilot"
            keyPassword = ksProps.getProperty("keyPassword")
                ?: System.getenv("KEY_PASSWORD") ?: storePassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // 海报加载：用 Coil
    implementation("io.coil-kt:coil-compose:2.6.0")
}
