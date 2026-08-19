plugins {
    id("com.android.application")
}

val releaseKeystorePath = providers.environmentVariable("FREEDOM_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("FREEDOM_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("FREEDOM_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("FREEDOM_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "dev.freedom.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.freedom.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 11
        versionName = "0.5.0-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.zxing:core:3.5.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
