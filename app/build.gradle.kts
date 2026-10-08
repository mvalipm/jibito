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
        // اندروید ۱۶. اندروید ۱۷ (۳۷) عمداً عقب افتاده: آن‌جا پیامک‌های رمز یک‌بارمصرف ۳ ساعت از اپ پنهان می‌مانند
        // و اسم فروشگاهِ خرید اینترنتی (از پیامک «رمز دوم») از دست می‌رود؛ اول باید برایش راه دیگری پیدا شود.
        targetSdk = 36
        // versionCode دستی نیست: در GitHub Actions شماره‌ی اجرای workflow است (برای همه‌ی شاخه‌ها یک شمارنده
        // که فقط بالا می‌رود)، پس هر APK تازه، از هر شاخه‌ای، روی نسخه‌ی قبلی نصب می‌شود و دو شاخه هیچ‌وقت
        // عدد تکراری یا کمتر نمی‌سازند. VERSION_CODE_BASE بالاتر از همه‌ی versionCodeهای دستیِ قبلی (تا ۷۵) است.
        // روی کامپیوتر (بدون GITHUB_RUN_NUMBER) همان عدد پایه است.
        versionCode = VERSION_CODE_BASE + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
        // هر PR که امکان یا migration تازه دارد این را بالا می‌برد و یک خط به CHANGELOG.md (ریشه‌ی ریپو) اضافه می‌کند
        versionName = "0.60.0"

        // تست‌های روی گوشی/امولاتور (app/src/androidTest)؛ هر تست در فرایند خودش و با داده‌ی پاک (Orchestrator)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
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
    // زبان اپ داخل خود اپ روی فارسی ثابت می‌شود؛ پس در App Bundle همه‌ی زبان‌ها باید در خود بسته بمانند
    bundle {
        language {
            enableSplit = false
        }
    }
    // Android Lint در هر ساخت CI؛ هر خطای لینت ساخت را می‌شکند (فهرست baseline دیگر نداریم).
    lint {
        abortOnError = true
        checkDependencies = false
        // «نسخه‌ی تازه‌تر هست»: نتیجه‌اش با هر انتشار بیرونی عوض می‌شود، نه با کد ما؛ ارتقای کتابخانه‌ها جدا و آگاهانه انجام می‌شود
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        // targetSdk 36 عمدی است (توضیح بالای targetSdk)؛ تا حل مسئله‌ی پیامک رمز در اندروید ۱۷، هشدارش خاموش است
        disable += "OldTargetApi"
        // هر هشدار تازه‌ی لینت هم ساخت را می‌شکند (دیگر هیچ هشداری نمانده)
        warningsAsErrors = true
    }
    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        // منابع اپ (رشته‌ها، فونت وزیرمتن) در تست‌های Robolectric هم در دسترس باشند (لازم برای تست اسکرین‌شات)
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // تست Migration ها ساختار هر نسخه را از همین فایل‌های JSON می‌خواند
            it.systemProperty("room.schemaDir", "$projectDir/schemas")
            // ChangelogTest: CHANGELOG.md باید برای همین versionName بخش داشته باشد
            it.systemProperty("jibito.changelog", rootProject.file("CHANGELOG.md").path)
            it.systemProperty("jibito.versionName", defaultConfig.versionName.orEmpty())
        }
    }
}

/** CHANGELOG.md ریشه‌ی ریپو را در assets اپ می‌گذارد (صفحه‌ی «چه چیزی تازه است» همان را نشان می‌دهد) */
abstract class CopyChangelog : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val changelog: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        changelog.get().asFile.copyTo(File(out, "CHANGELOG.md"))
    }
}

val copyChangelog = tasks.register<CopyChangelog>("copyChangelog") {
    changelog.set(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyChangelog, CopyChangelog::outputDir)
    }
}

// Room: فایل JSON ساختار هر نسخه‌ی دیتابیس در app/schemas ذخیره می‌شود (سند معماری بخش ۵)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// هر هشدار کامپایلر کاتلین (کد اپ و تست‌ها) ساخت را می‌شکند، تا هشدارها جمع نشوند
kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
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
    implementation(libs.haze.blur)
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

    // تست‌های روی امولاتور برای مسیرهای حیاتی: رسیدن پیامک، دکمه‌های نوتیفیکیشن، بازگردانی پشتیبان (CI: کار instrumented)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
}
