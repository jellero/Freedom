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
        versionCode = 4
        versionName = "0.2.2-diagnostic"
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
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.zxing:core:3.5.4")

    testImplementation("junit:junit:4.13.2")
}
