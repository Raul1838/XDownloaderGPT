plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.xgifdownloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.xgifdownloader"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("com.squareup:gifencoder:0.10.1")
}
