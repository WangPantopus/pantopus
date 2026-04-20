import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Load local env from .env (not committed). Falls back to sensible defaults.
val envFile = rootProject.file(".env")
val localEnv = Properties().apply {
    if (envFile.exists()) {
        envFile.inputStream().use { load(it) }
    }
}
fun envOr(key: String, default: String): String =
    (localEnv.getProperty(key) ?: System.getenv(key) ?: default).trim()

android {
    namespace = "app.pantopus.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.pantopus.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Inject config into BuildConfig — read via BuildConfig.PANTOPUS_API_BASE_URL etc.
        buildConfigField("String", "PANTOPUS_API_BASE_URL", "\"${envOr("PANTOPUS_API_BASE_URL", "http://10.0.2.2:8000")}\"")
        buildConfigField("String", "PANTOPUS_SOCKET_URL", "\"${envOr("PANTOPUS_SOCKET_URL", "http://10.0.2.2:8000")}\"")
        buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", "\"${envOr("STRIPE_PUBLISHABLE_KEY", "pk_test_REPLACE_ME")}\"")

        // Google Maps API key — read from gradle.properties, ~/.gradle/gradle.properties,
        // or the env. Never hard-code a real key here.
        manifestPlaceholders["MAPS_API_KEY"] = envOr("MAPS_API_KEY", "")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release builds need a signingConfig. Configure one via
            // ~/.gradle/gradle.properties (KEYSTORE_FILE, KEYSTORE_PASSWORD,
            // KEY_ALIAS, KEY_PASSWORD) and wire in here when ready.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Core + lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Networking
    implementation(libs.bundles.networking)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Image loading
    implementation(libs.coil.compose)

    // Realtime
    implementation(libs.socketio.client)

    // Payments
    implementation(libs.stripe.android)

    // Maps
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)

    // Logging
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
