// <project-root>/app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hodaka.storyvoice"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hodaka.storyvoice"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 基本
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Lifecycle / ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.3")

    // Fragment（FragmentContainerView/拡張）
    implementation("androidx.fragment:fragment-ktx:1.8.3")

    // WorkManager（後で通知・事前フェッチに利用）
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // JSON
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // 画像（WebP 挿絵）
    implementation("io.coil-kt:coil:2.7.0")

    // 広告（Week5で使用）
    implementation("com.google.android.gms:play-services-ads:23.3.0")

    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // java.time を API 24〜25 でも使うためのデシュガリング
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
}
