import java.util.Properties
import java.io.FileInputStream

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
        
        // Read local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }

        buildConfigField("String", "GEMINI_API_KEY", "\"${localProperties.getProperty("GEMINI_API_KEY")}\"")
        buildConfigField("String", "GROQ_API_KEY", "\"${localProperties.getProperty("GROQ_API_KEY")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // CRITICAL: This keeps your model files safe
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

    // CameraX - Reliable camera implementation
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // ML Kit Text Recognition (Reads English text instantly) - Keeping this for other features
    implementation("com.google.mlkit:text-recognition:16.0.0")
    // ML Kit Image Labeling (Better descriptions: "Cup", "Laptop", "Person")
    implementation("com.google.mlkit:image-labeling:17.0.7")

    //location
    implementation("com.google.android.gms:play-services-location:21.0.1")

    implementation("com.google.guava:guava:31.1-android")

    // Network for Groq API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}