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

    // TensorFlow Lite - Object Detection
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")

    // ML Kit Text Recognition (Reads English text instantly) - Keeping this for other features
    implementation("com.google.mlkit:text-recognition:16.0.0")

    //location
    implementation("com.google.android.gms:play-services-location:21.0.1")

    implementation("com.google.guava:guava:31.1-android")

    // Read text- gemini api
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
}