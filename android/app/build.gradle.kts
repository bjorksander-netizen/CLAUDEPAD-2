plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bjorn.claudepad"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bjorn.claudepad"
        minSdk = 24
        targetSdk = 34
        versionCode = 14
        versionName = "2.9.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

// Catatan font:
// JetBrains Mono diletakkan di src/main/res/font/jetbrains_mono.ttf.
// Di CI, file itu diunduh oleh workflow sebelum Gradle dijalankan.
// Untuk build lokal, jalankan skrip android/fetch-font.sh (atau .bat),
// atau biarkan kosong — aplikasi otomatis memakai monospace bawaan sistem
// (lihat Fonts.kt), sehingga build tetap berhasil tanpa file font.
