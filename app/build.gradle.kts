plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is optional.
// F-Droid builds without these variables and produces an unsigned APK.
val releaseKeystorePath =
    providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull
val releaseStorePassword =
    providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias =
    providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword =
    providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull

val signingValues =
    listOf(
        releaseKeystorePath,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    )

val hasReleaseSigning = signingValues.all { !it.isNullOrBlank() }
val hasPartialSigning = signingValues.any { !it.isNullOrBlank() }

if (hasPartialSigning && !hasReleaseSigning) {
    throw GradleException("Incomplete release signing configuration")
}

val debugKeystoreFile =
    listOf(
        File(System.getProperty("user.home"), ".android/debug.keystore"),
        File(System.getProperty("user.home"), ".config/.android/debug.keystore"),
    ).firstOrNull { it.exists() }

android {
    namespace = "site.casiopea.cruizer"
    compileSdk = 37

    defaultConfig {
        applicationId = "site.casiopea.cruizer"
        minSdk = 31
        targetSdk = 37
        versionCode = 50
        versionName = "1.0.2"
    }

    testOptions {
        // Session is a plain object but logs via android.util.Log; without this a
        // JVM test of the route lifecycle dies on the unmocked stub.
        unitTests.isReturnDefaultValues = true
    }

    // Reproducible builds: the dependency-info block embeds AGP/dependency versions per builder.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        } else if (debugKeystoreFile != null) {
            // Local-dev convenience: fall back to the debug keystore so
            // assembleRelease stays installable. Never used by F-Droid
            // (signing configs are stripped there) or by CI (release env set).
            getByName("debug") {
                storeFile = debugKeystoreFile
            }
        }
    }

    buildTypes {
        debug { }

        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // Developer CI signs with the permanent release key.
            // F-Droid builds remain unsigned for reproducibility verification.
            signingConfig =
                when {
                    hasReleaseSigning -> signingConfigs.getByName("release")
                    debugKeystoreFile != null -> signingConfigs.getByName("debug")
                    else -> null
                }

            // Reproducible builds: AGP 9 embeds the git commit in META-INF otherwise.
            vcsInfo { include = false }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// AGP 9.5's compose-mapping task resolves compose-group-mapping against its
// embedded Kotlin (2.2.10), which was never published. Force the real one.
configurations.matching { it.name.contains("composeMapping") }.configureEach {
    resolutionStrategy.force("org.jetbrains.kotlin:compose-group-mapping:2.3.20")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.core)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.androidx.datastore.prefs)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
