plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chris.whisperbar"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.chris.whisperbar"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_NATIVE=OFF",
                    "-DWHISPER_BUILD_TESTS=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF",
                )
            }
        }
        // arm64-v8a = reale Geraete, x86_64 = CI-Emulator.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Grosses GGML-Modell nicht komprimieren, damit der Asset-Streaming-Loader es lesen kann.
    androidResources {
        noCompress += "bin"
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

    // whisper.cpp braucht keine 16 KB-Page-Ausrichtung fuer diesen Build; Standard-Packaging.
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // Bewusst KEIN AndroidX/Compose im App-Code — reine Framework-APIs (schlank, wenige Build-Risiken).
    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
