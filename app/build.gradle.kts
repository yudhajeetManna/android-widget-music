plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "studio.neodev.smartisland"
    compileSdk = 34

    defaultConfig {
        applicationId = "studio.neodev.smartisland"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
    }

    signingConfigs {
        create("release") {
            val keyBase64 = System.getenv("KEYSTORE_BASE64") ?: ""
            if (keyBase64.isNotEmpty()) {
                val keystoreFile = rootProject.file("lizmo-release-ci.jks")
                keystoreFile.writeBytes(
                    android.util.Base64.decode(keyBase64, android.util.Base64.DEFAULT)
                )
                storeFile = keystoreFile
            } else {
                storeFile = rootProject.file("lizmo-release.jks")
            }
            storePassword = System.getenv("KEYSTORE_PASS") ?: "lizmostore123"
            keyAlias = System.getenv("KEY_ALIAS") ?: "lizmo"
            keyPassword = System.getenv("KEY_PASS") ?: "lizmostore123"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
