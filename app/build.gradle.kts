plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ytdlVersion = "0.17.4"

android {
    namespace = "com.tony.linkamp3"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tony.linkamp3"
        minSdk = 29
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        create("propia") {
            storeFile = file("linkamp3.jks")
            storePassword = "linkamp3pass"
            keyAlias = "linkamp3"
            keyPassword = "linkamp3pass"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("propia")
        }
        debug {
            signingConfig = signingConfigs.getByName("propia")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdlVersion")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdlVersion")
    implementation("androidx.core:core-ktx:1.13.1")
}
