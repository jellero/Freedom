plugins {
    id("com.android.application")
}

android {
    namespace = "dev.freedom.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.freedom.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-m1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // The Android client compiles the same pure-Java protocol state-machine core
    // exercised by the host simulator. Android-specific adapters remain in app/.
    sourceSets {
        getByName("main") {
            java.srcDir("../core/src/main/java")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
