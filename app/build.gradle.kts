plugins {
    id("com.android.application")
    // Compose-Compiler-Plugin (Kotlin selbst kommt built-in mit AGP 9).
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.chris.whisperbar"
    // Compose 1.12 (BOM 2026.08.00) verlangt compileSdk 37 + AGP >= 9.2.0:
    // https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.0-alpha01
    compileSdk = 37

    defaultConfig {
        applicationId = "com.chris.whisperbar"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.0.0"
    }

    buildFeatures {
        compose = true
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
            // R8 (Full Mode = AGP-Default) + Resource-Shrinking: Compose/M3 ohne Shrinker = mehrere MB DEX.
            // Stacktraces entschluesseln: app/build/outputs/mapping/release/mapping.txt
            // https://developer.android.com/build/shrink-code
            isMinifyEnabled = true
            isShrinkResources = true
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
    // kotlinOptions {} entfaellt: Built-in-Kotlin nimmt jvmTarget = targetCompatibility (17).
    // https://developer.android.com/build/migrate-to-built-in-kotlin

    testOptions {
        unitTests {
            // Pflicht fuer Robolectric (Themes, Strings, ui-test-manifest-Activity).
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Compose BOM 2026.08.00 -> ui 1.12.0, material3 1.4.0
    // https://developer.android.com/develop/ui/compose/bom/bom-mapping
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // setContent, BackHandler, enableEdgeToEdge — https://developer.android.com/jetpack/androidx/releases/activity
    implementation("androidx.activity:activity-compose:1.13.0")
    // collectAsStateWithLifecycle etc. — https://developer.android.com/jetpack/androidx/releases/lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // Bewusst NICHT: material-icons-core/-extended (35,7 MB AAR, von Google nicht mehr empfohlen),
    // navigation-compose (State-Navigation reicht fuer ~6 Screens). Icons als eigene res/drawable/ic_*.xml.

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // ComponentActivity fuer createComposeRule(): https://developer.android.com/develop/ui/compose/testing
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // Robolectric 4.16.1: SDK 23–36; SDK 35 laeuft mit JDK 17 — https://github.com/robolectric/robolectric/releases
    testImplementation("org.robolectric:robolectric:4.16.1")
    // https://developer.android.com/jetpack/androidx/releases/test
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
}
