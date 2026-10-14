plugins {
    id("com.android.application") version "8.7.3" apply false
    // Firebase Auth SDK metadata is newer than Kotlin 2.0.21 can read.
    // Kotlin 2.3.21 remains compatible with this project's AGP 8.7.3 / Gradle 8.11.1.
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}
