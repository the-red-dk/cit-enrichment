plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun quoteBuildConfig(value: String): String {
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

android {
    namespace = "com.daksha.cit.enrichment"
    compileSdk = 34

    flavorDimensions += "mode"

    defaultConfig {
        applicationId = "com.daksha.cit.enrichment"
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", quoteBuildConfig(localProperties.getProperty("cloudinary.cloudName", "")))
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", quoteBuildConfig(localProperties.getProperty("cloudinary.uploadPreset", "")))
        buildConfigField("String", "CLOUDINARY_FOLDER", quoteBuildConfig(localProperties.getProperty("cloudinary.folder", "cit-student-enrichment/claims")))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    productFlavors {
        create("guest") {
            dimension = "mode"
            versionNameSuffix = "-guest"
            buildConfigField("boolean", "ENABLE_FIREBASE_AUTH", "false")
            resValue("string", "app_name", "CIT Student Enrichment")
        }
        create("auth") {
            dimension = "mode"
            versionNameSuffix = "-auth"
            buildConfigField("boolean", "ENABLE_FIREBASE_AUTH", "true")
            resValue("string", "app_name", "CIT Student Enrichment Auth")
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("com.google.firebase:firebase-auth-ktx:22.3.1")
    implementation("com.google.firebase:firebase-firestore-ktx:24.10.0")
    implementation("com.google.firebase:firebase-storage-ktx:20.3.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
