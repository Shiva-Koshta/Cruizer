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
        versionCode = 44
        versionName = "0.5.15"
    }

    testOptions {
        // Session is a plain object but logs via android.util.Log; without this a
        // JVM test of the route lifecycle dies on the unmocked stub.
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        debug { }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
