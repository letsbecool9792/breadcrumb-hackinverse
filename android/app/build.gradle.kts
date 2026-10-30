import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Where the release key is and its passwords, in android/keystore.properties,
// which git ignores (copy keystore.properties.example). Without it a release
// build comes out unsigned, and no phone installs it.
val releaseKey = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "com.lbc.breadcrumb"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // The same as the code's namespace, and permanent once published. adb
        // commands and the launcher shortcut's targetPackage use it. Debug
        // builds add ".debug" (below).
        applicationId = "com.lbc.breadcrumb"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = file(releaseKey.getProperty("storeFile"))
                storePassword = releaseKey.getProperty("storePassword")
                keyAlias = releaseKey.getProperty("keyAlias")
                keyPassword = releaseKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // An app of its own, beside the release on the same phone, with its own
        // data: a debug build or a test on the phone never touches what the
        // release keeps. Its name and shortcuts are in src/debug/res.
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            // LiteRT-LM ships only arm64-v8a and x86_64, so a 32-bit phone
            // can't search anyway; x86_64 is emulators. Phones get arm64 alone,
            // which saves ~55 MB of native libraries.
            ndk {
                abiFilters += "arm64-v8a"
            }
            // R8: the code shrunk from 32 MB to what is used, and the resources nothing reaches.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // The native libraries (LiteRT-LM's 22 MB, ML Kit's 11 MB) go compressed in the APK and are
    // unpacked at install: a download about half the size, for about 15 MB more on the phone.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Schema JSON is checked in: Room validates migrations against it, and
    // auto-migrations cannot be generated without it.
    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.litertlm.android)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    // Virtual time testing for TwoStageSearch flow debouncing and step execution (§9)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
