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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
