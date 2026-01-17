plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.inclusive.assist"
    compileSdk = 34 // Use 34 (Stable) or 36 if you prefer

    defaultConfig {
        applicationId = "com.inclusive.assist"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // CRITICAL: This keeps your brain file safe
    aaptOptions {
        noCompress += "tflite"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // CameraX (The Eyes)
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // ML Kit (The Brain)
    // We only need these two for what you are doing now:
    implementation("com.google.mlkit:image-labeling:17.0.7")       // The Labeling Engine
    implementation("com.google.mlkit:image-labeling-custom:17.0.2") // The Custom Model Support

    // ML Kit Text Recognition (Reads English text instantly)
    implementation("com.google.mlkit:text-recognition:16.0.0")


    //location
    implementation("com.google.android.gms:play-services-location:21.0.1")

    implementation("com.google.guava:guava:31.1-android")


    // Read text- gemini api
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
}