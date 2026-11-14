plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "site.casiopea.cruizer"
    compileSdk = 37

    defaultConfig {
        applicationId = "site.casiopea.cruizer"
        minSdk = 31
        targetSdk = 37
        versionCode = 48
        versionName = "1.0.0"
    }

    testOptions {
        // Session is a plain object but logs via android.util.Log; without this a
        // JVM test of the route lifecycle dies on the unmocked stub.
        unitTests.isReturnDefaultValues = true
    }

    signingConfigs {
        // One keystore everywhere: CI (secret DEBUG_KEYSTORE) restores the same
        // file to ~/.android/debug.keystore. Explicit so AGP can never
        // auto-generate a throwaway key on a machine where the default is missing.
        getByName("debug") {
            storeFile = listOf(
                File(System.getProperty("user.home"), ".android/debug.keystore"),
                File(System.getProperty("user.home"), ".config/.android/debug.keystore"),
            ).first { it.exists() }
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
            signingConfig = signingConfigs.getByName("debug")
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
