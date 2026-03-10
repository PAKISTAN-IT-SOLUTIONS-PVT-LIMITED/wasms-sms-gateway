plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    // google-services plugin removed — Firebase disabled until real credentials configured
    // alias(libs.plugins.google.services)
}

android {
    namespace = "net.wasms.smsgateway"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wasms.gateway"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.findProperty("overrideVersionCode") as? String)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("overrideVersionName") as? String) ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export for migration testing
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.generateKotlin", "true")
        }
    }

    signingConfigs {
        create("release") {
            // Values injected from environment or local.properties
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "keystore/release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "wasms"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "API_BASE_URL", "\"${(project.findProperty("overrideApiBaseUrl") as? String) ?: "https://wasms.net/new/api/v1"}\"")
            buildConfigField("String", "WS_HOST", "\"${(project.findProperty("overrideWsHost") as? String) ?: "wasms.net"}\"")
            buildConfigField("String", "WS_PATH", "\"${(project.findProperty("overrideWsPath") as? String) ?: "/new/app/wasms-gateway"}\"")
            buildConfigField("Boolean", "FCM_ENABLED", (project.findProperty("overrideFcmEnabled") as? String) ?: "false")
            buildConfigField("Boolean", "CERT_PINNING_ENABLED", (project.findProperty("overrideCertPinningEnabled") as? String) ?: "false")
        }

        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("Boolean", "FCM_ENABLED", (project.findProperty("overrideFcmEnabled") as? String) ?: "true")
            buildConfigField("Boolean", "CERT_PINNING_ENABLED", (project.findProperty("overrideCertPinningEnabled") as? String) ?: "true")
            signingConfig = signingConfigs.getByName("release")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "API_BASE_URL", "\"${(project.findProperty("overrideApiBaseUrl") as? String) ?: "https://wasms.net/api/v1"}\"")
            buildConfigField("String", "WS_HOST", "\"${(project.findProperty("overrideWsHost") as? String) ?: "wasms.net"}\"")
            buildConfigField("String", "WS_PATH", "\"${(project.findProperty("overrideWsPath") as? String) ?: "/app/wasms-gateway"}\"")
            buildConfigField("Boolean", "FCM_ENABLED", (project.findProperty("overrideFcmEnabled") as? String) ?: "true")
            buildConfigField("Boolean", "CERT_PINNING_ENABLED", (project.findProperty("overrideCertPinningEnabled") as? String) ?: "true")
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
        buildConfig = true
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.runtime)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX Core
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splash)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Security
    implementation(libs.security.crypto)
    implementation(libs.sqlcipher)

    // DataStore
    implementation(libs.datastore.preferences)

    // Firebase — disabled until real google-services.json is configured
    // implementation(platform(libs.firebase.bom))
    // implementation(libs.firebase.messaging)
    // implementation(libs.firebase.crashlytics)
    // implementation(libs.firebase.analytics)

    // Play Integrity
    implementation(libs.play.integrity)

    // Camera + QR
    implementation(libs.camera.core)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode)

    // WorkManager
    implementation(libs.work.runtime)
    implementation(libs.work.hilt)
    ksp(libs.work.hilt.compiler)

    // Image loading
    implementation(libs.coil.compose)

    // Logging
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.hilt.testing)
    debugImplementation(libs.compose.ui.test.manifest)
}
