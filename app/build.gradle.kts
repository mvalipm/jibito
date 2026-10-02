plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ir.jibito.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.jibito.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 40
        versionName = "0.32.2"
    }

    // یک کلید ثابت برای نسخه‌ی آزمایشی، تا هر نسخه‌ی جدید روی قبلی نصب شود
    // (بدون این، هر بار باید اپ قبلی را پاک کنید)
    signingConfigs {
        getByName("debug") {
            storeFile = file("app-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

// Room: فایل JSON ساختار هر نسخه‌ی دیتابیس در app/schemas ذخیره می‌شود (سند معماری بخش ۵)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // پس‌زمینه‌ی شیشه‌ای مات نوار پایین (تار کردن محتوای زیرش)
    implementation("dev.chrisbanes.haze:haze:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
