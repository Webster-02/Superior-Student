plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.superiorstudent.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.superiorstudent.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.5"
    }

    buildFeatures { viewBinding = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
}

kotlin { jvmToolchain(17) }
