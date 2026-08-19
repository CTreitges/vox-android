plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chris.whisperbar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chris.whisperbar"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }

    // Lint soll den Build nicht an Warnungen scheitern lassen (Reports bleiben erhalten).
    lint {
        abortOnError = false
    }

    signingConfigs {
        create("release") {
            // Keystore + Passwort kommen aus Umgebungsvariablen (CI: aus GitHub-Secrets).
            // Kein Secret im Repo. Fehlt der Keystore lokal, bleibt release unsigniert.
            val ksPath = System.getenv("WB_KEYSTORE") ?: "keystore/whisperbar-release.p12"
            val ks = file(ksPath)
            if (ks.exists()) {
                storeFile = ks
                storeType = "PKCS12"
                storePassword = System.getenv("WB_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("WB_KEY_ALIAS") ?: "whisperbar"
                keyPassword = System.getenv("WB_KEY_PASSWORD")
                    ?: System.getenv("WB_KEYSTORE_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            val ks = file(System.getenv("WB_KEYSTORE") ?: "keystore/whisperbar-release.p12")
            if (ks.exists()) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
}

dependencies {
    // Bewusst KEIN AndroidX/Compose im App-Code — reine Framework-APIs (schlank, wenige Build-Risiken).
    testImplementation("junit:junit:4.13.2")
}
