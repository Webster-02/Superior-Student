plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firebase's Google Services plugin needs app/google-services.json.
// Apply it only after the matching file is added, so the current app can
// still build while Firebase Console setup is in progress.
if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.superiorstudent.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.superiorstudent.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 58
        versionName = "1.27.14"
    }

    buildFeatures { viewBinding = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.webkit:webkit:1.12.1")

    // Firebase services for the Student Connect feature.
    // Authentication strategy will be finalized before creating student profiles.
    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

kotlin { jvmToolchain(17) }
