plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val persistentKeystore = file("persian-keyboard.keystore")

android {
    namespace = "com.customkeyboard.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.customkeyboard.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (persistentKeystore.exists()) {
            create("persistent") {
                storeFile = persistentKeystore
                storePassword = "keyboard123"
                keyAlias = "persiankeyboard"
                keyPassword = "keyboard123"
            }
        }
    }

    buildTypes {
        debug {
            if (persistentKeystore.exists()) {
                signingConfig = signingConfigs.getByName("persistent")
            }
        }
        release {
            isMinifyEnabled = false
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
        viewBinding = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
