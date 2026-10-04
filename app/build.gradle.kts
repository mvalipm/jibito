plugins {
    // از AGP 9، کاتلین داخل خود پلاگین اندروید است (دیگر kotlin-android جدا لازم نیست)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // تست اسکرین‌شات صفحه‌ها روی JVM (Robolectric)؛ تصویرها در CI ساخته و به‌صورت Artifact گذاشته می‌شوند
    alias(libs.plugins.roborazzi)
}

/** پایه‌ی versionCode خودکار (پایین‌تر نیاید؛ نسخه‌های نصب‌شده تا ۷۵ دستی بودند) */
val VERSION_CODE_BASE = 100

android {
    namespace = "ir.jibito.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "ir.jibito.app"
        minSdk = 26
        targetSdk = 35
        // versionCode دستی نیست: در GitHub Actions شماره‌ی اجرای workflow است (برای همه‌ی شاخه‌ها یک شمارنده
        // که فقط بالا می‌رود)، پس هر APK تازه، از هر شاخه‌ای، روی نسخه‌ی قبلی نصب می‌شود و دو شاخه هیچ‌وقت
        // عدد تکراری یا کمتر نمی‌سازند. VERSION_CODE_BASE بالاتر از همه‌ی versionCodeهای دستیِ قبلی (تا ۷۵) است.
        // روی کامپیوتر (بدون GITHUB_RUN_NUMBER) همان عدد پایه است.
        versionCode = VERSION_CODE_BASE + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
        versionName = "0.50.1"
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
        // کلید انتشار هرگز در گیت نیست: از متغیرهای محیطی (در GitHub Actions از Secrets) خوانده می‌شود.
        // راهنمای ساختن کلید و تنظیم Secrets: RELEASE.md
        val releaseStore = System.getenv("JIBITO_KEYSTORE_FILE")
        if (!releaseStore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = System.getenv("JIBITO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("JIBITO_KEY_ALIAS")
                keyPassword = System.getenv("JIBITO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // «۰٫۵۰٫۰-۱۶۵»: شماره‌ی ساخت در تنظیمات دیده می‌شود تا معلوم باشد کدام APK نصب است
            System.getenv("GITHUB_RUN_NUMBER")?.let { versionNameSuffix = "-$it" }
        }
        release {
            // R8: کد استفاده‌نشده حذف و اسم‌ها کوتاه می‌شوند ← APK کوچک‌تر و سریع‌تر، و مهندسی معکوس سخت‌تر
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // بدون کلید انتشار (مثلاً روی کامپیوتر توسعه‌دهنده)، نسخه‌ی release امضا نمی‌شود
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    // Android Lint در هر ساخت CI. مشکلات قدیمی در lint-baseline.xml ثبت شده‌اند؛ فقط مشکل «تازه» ساخت را می‌شکند.
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkDependencies = false
        htmlReport = true
        // همه‌ی مشکل‌ها در خروجی CI هم نوشته شوند (نه فقط اولی)
        textReport = true
        textOutput = file("stdout")
    }
    testOptions {
        // منابع اپ (رشته‌ها، فونت وزیرمتن) در تست‌های Robolectric هم در دسترس باشند (لازم برای تست اسکرین‌شات)
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // تست Migration ها ساختار هر نسخه را از همین فایل‌های JSON می‌خواند
            it.systemProperty("room.schemaDir", "$projectDir/schemas")
        }
    }
}

// Room: فایل JSON ساختار هر نسخه‌ی دیتابیس در app/schemas ذخیره می‌شود (سند معماری بخش ۵)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // جابه‌جایی بین تب‌ها (هر تب حالت خودش را نگه می‌دارد)
    implementation(libs.androidx.navigation.compose)

    // پس‌زمینه‌ی شیشه‌ای مات نوار پایین (تار کردن محتوای زیرش)
    implementation(libs.haze)
    implementation(libs.androidx.work.runtime.ktx)
    // پروفایل‌های آماده‌ی کتابخانه‌ها (Compose و…) را روی گوشی نصب می‌کند تا اپ سریع‌تر باز شود؛
    // مهم برای نصب از کافه‌بازار و مایکت که مثل گوگل‌پلی پروفایل ابری ندارند
    implementation(libs.androidx.profileinstaller)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    debugImplementation(libs.compose.ui.tooling)
    // لازم برای تست‌های Compose (اسکرین‌شات) روی Robolectric
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    // تست‌هایی که اندروید واقعی لازم دارند (دیتابیس، Migration ها، پشتیبان‌گیری) بدون گوشی و شبیه‌ساز
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    // تست اسکرین‌شات
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}
