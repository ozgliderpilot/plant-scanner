plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.nursery.scanner"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nursery.scanner"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sign release builds with the auto-generated debug keystore so the prod/test
            // release APKs are installable for sideloading out of the box (no keystore secret
            // to manage here). Swap in a real keystore for store/wide distribution — see
            // docs/deploy/android.md. Release builds are non-debuggable by default.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Two coexisting installs on one device: prod and test. The OS keys local storage (Room DB,
    // DataStore) off applicationId, so the `.test` suffix gives the test install fully isolated
    // data. Backend endpoint stays runtime config (Settings) — nothing baked in here.
    flavorDimensions += "environment"
    productFlavors {
        create("prod") {
            dimension = "environment"
            // applicationId stays com.nursery.scanner (defaultConfig).
            resValue("string", "app_name", "Nursery")
            resValue("color", "ic_launcher_background", "#1B5E20")
        }
        // Named "qa", not "test": AGP reserves flavor names starting with "test" (collides with
        // the unit-test source set). The applicationId suffix and launcher label keep the
        // "test" wording the maintainer/volunteers actually see. Variant task: assembleQaRelease.
        create("qa") {
            dimension = "environment"
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            // Required: clearly different launcher label so a volunteer can never confuse the
            // two installs. Nice-to-have: red icon background to distinguish them at a glance.
            resValue("string", "app_name", "Nursery TEST")
            resValue("color", "ic_launcher_background", "#B71C1C")
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

dependencies {
    // Pure business logic (composite build).
    implementation(libs.nursery.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.kotlinx.coroutines.android)
}
