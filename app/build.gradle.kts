import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

fun envOrProp(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }

val playVersionCode: Int = envOrProp("VERSION_CODE")?.toIntOrNull() ?: 1
val playVersionName: String = envOrProp("VERSION_NAME") ?: "1.0"

// Play upload keystore: keystore.properties (local) or PLAY_UPLOAD_* env (CI).
// Without either, release builds keep the debug keystore for sideload convenience.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

fun keystoreProp(name: String, envName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(name)?.takeIf { it.isNotBlank() }

val uploadStoreFilePath = keystoreProp("storeFile", "PLAY_UPLOAD_STORE_FILE")
val uploadStorePassword = keystoreProp("storePassword", "PLAY_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = keystoreProp("keyAlias", "PLAY_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = keystoreProp("keyPassword", "PLAY_UPLOAD_KEY_PASSWORD")
val hasUploadKeystore =
    !uploadStoreFilePath.isNullOrBlank() &&
        !uploadStorePassword.isNullOrBlank() &&
        !uploadKeyAlias.isNullOrBlank() &&
        !uploadKeyPassword.isNullOrBlank()

android {
    namespace = "com.nursery.scanner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nursery.scanner"
        minSdk = 26
        targetSdk = 36
        versionCode = playVersionCode
        versionName = playVersionName
    }

    signingConfigs {
        if (hasUploadKeystore) {
            create("upload") {
                // storeFile in keystore.properties is relative to app/; absolute env paths also work.
                val store = file(uploadStoreFilePath!!)
                require(store.exists()) {
                    "Upload keystore not found at ${store.absolutePath}. " +
                        "Run ./scripts/create-upload-keystore.sh or set PLAY_UPLOAD_STORE_FILE."
                }
                storeFile = store
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Play / CI: upload keystore when configured. Otherwise debug keystore so
            // sideload release APKs still install without secrets — see docs/deploy/play.md.
            signingConfig =
                if (hasUploadKeystore) {
                    signingConfigs.getByName("upload")
                } else {
                    signingConfigs.getByName("debug")
                }
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
            resValue("string", "app_name", "GF Nursery")
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
            resValue("string", "app_name", "GF Nursery TEST")
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
        buildConfig = true
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

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
