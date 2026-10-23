import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.lizmo.island"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.lizmo.island"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "0.7.0"
    }

    signingConfigs {
        create("release") {
            val keyBase64 = System.getenv("KEYSTORE_BASE64")
            if (!keyBase64.isNullOrBlank()) {
                val keystoreFile = rootProject.file("lizmo-release-ci.jks")
                keystoreFile.writeBytes(Base64.getDecoder().decode(keyBase64.trim()))
                storeFile = keystoreFile
            } else {
                val repoKeystore = rootProject.file("keystore/lizmo-release.jks")
                storeFile = if (repoKeystore.exists()) repoKeystore else rootProject.file("lizmo-release.jks")
            }

            val envStorePass = System.getenv("KEYSTORE_PASS")
            val envAlias = System.getenv("KEY_ALIAS")
            val envKeyPass = System.getenv("KEY_PASS")

            storePassword = if (!envStorePass.isNullOrBlank()) envStorePass else "lizmostore123"
            keyAlias = if (!envAlias.isNullOrBlank()) envAlias else "lizmo"
            keyPassword = if (!envKeyPass.isNullOrBlank()) envKeyPass else "lizmostore123"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
