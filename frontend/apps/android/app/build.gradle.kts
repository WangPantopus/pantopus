import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.paparazzi)
}

// Load local env from .env (not committed). Falls back to sensible defaults.
val envFile = rootProject.file(".env")
val localEnv =
    Properties().apply {
        if (envFile.exists()) {
            envFile.inputStream().use { load(it) }
        }
    }

fun envOr(
    key: String,
    default: String,
): String = (localEnv.getProperty(key) ?: System.getenv(key) ?: default).trim()

android {
    namespace = "app.pantopus.android"
    // compileSdk bumped to 35 to satisfy transitive androidx.core 1.15 AAR
    // metadata. targetSdk stays at 34 per the P1 platform spec.
    compileSdk = 35

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
        buildConfigField("String", "SENTRY_DSN", "\"${envOr("SENTRY_DSN", "")}\"")
        buildConfigField("String", "PANTOPUS_ENV", "\"${envOr("PANTOPUS_ENV", "local")}\"")

        // Google Maps API key — read from gradle.properties, ~/.gradle/gradle.properties,
        // or the env. Never hard-code a real key here.
        manifestPlaceholders["MAPS_API_KEY"] = envOr("MAPS_API_KEY", "")
    }

    // Release signing: read from .env / ~/.gradle/gradle.properties / env vars.
    // Falls back to the debug keystore so assembleRelease still works for
    // smoke tests — but a real release must override these.
    val keystoreFile = envOr("PANTOPUS_KEYSTORE_FILE", "")
    signingConfigs {
        if (keystoreFile.isNotEmpty() && file(keystoreFile).exists()) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = envOr("PANTOPUS_KEYSTORE_PASSWORD", "")
                keyAlias = envOr("PANTOPUS_KEY_ALIAS", "")
                keyPassword = envOr("PANTOPUS_KEY_PASSWORD", "")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
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
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs +=
            listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
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
    // Material 1 pulled in for PullRefreshIndicator (not yet in Material 3 stable).
    implementation(libs.androidx.compose.material)
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

    // Logging + crash reporting
    implementation(libs.timber)
    implementation(libs.sentry.android)
    implementation(libs.sentry.android.timber)
    implementation(libs.sentry.android.okhttp)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    // Snapshot testing (applied via the Paparazzi Gradle plugin above).
    testImplementation(libs.paparazzi)
}

// Fail the build if feature code reaches for a Material icon or a
// `ic_lucide_*` drawable directly — icons must route through
// [PantopusIconImage] so we can swap the renderer in one place.
//
// Written with a typed Gradle task class so the configuration cache can
// serialize it. The file collection and root directory are wired up at
// configuration time via providers.
abstract class VerifyPantopusIconsTask : DefaultTask() {
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val banned =
            listOf(
                Regex("""androidx\.compose\.material\.icons\."""),
                Regex("""painterResource\([^)]*R\.drawable\.ic_lucide_"""),
            )
        val root = rootDirectory.get().asFile.toPath()
        val violations = mutableListOf<String>()
        sources.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                banned.forEach { pattern ->
                    if (pattern.containsMatchIn(line)) {
                        violations += "${root.relativize(file.toPath())}:${index + 1}: ${line.trim()}"
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "verifyPantopusIcons failed — feature code must use PantopusIconImage, not " +
                    "Material icons or ic_lucide_* drawables directly:\n" +
                    violations.joinToString("\n"),
            )
        }
        logger.lifecycle("✓ verifyPantopusIcons: no stray icon usage in feature code.")
    }
}

tasks.register<VerifyPantopusIconsTask>("verifyPantopusIcons") {
    group = "verification"
    description = "Reject direct Material-icon or ic_lucide_* usage outside ui/theme/Icons.kt."
    sources.from(
        fileTree("src/main/java/app/pantopus/android") {
            include("**/*.kt")
            exclude("ui/theme/Icons.kt")
            exclude("ui/screens/_internal/**")
        },
    )
    rootDirectory.set(rootProject.layout.projectDirectory)
}
tasks.named("check") { dependsOn("verifyPantopusIcons") }

// Convenience alias so CI and the README can reference a single task name
// regardless of which build variants exist. Delegates to the Paparazzi
// plugin's per-variant verify task.
tasks.register("paparazziVerify") {
    group = "verification"
    description = "Verify Paparazzi snapshots for the debug variant."
    dependsOn("verifyPaparazziDebug")
}
tasks.register("paparazziRecord") {
    group = "verification"
    description = "Record new Paparazzi snapshot baselines for the debug variant."
    dependsOn("recordPaparazziDebug")
}
