plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Numéro de version incrémenté automatiquement à chaque compilation GitHub
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.moniptv.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.moniptv.app"
        minSdk = 21
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Clé de signature privée : fournie uniquement par les "Secrets" GitHub, jamais stockée dans le dépôt.
    // Elle prouve que les mises à jour viennent bien de toi.
    val keystorePath = System.getenv("SIGNING_KEYSTORE")
    val keystorePassword = System.getenv("SIGNING_PASSWORD")
    val hasReleaseKey = !keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseKey) {
            create("perso") {
                storeFile = file(keystorePath!!)
                storeType = "PKCS12"
                storePassword = keystorePassword
                keyAlias = "moniptv"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("perso")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    implementation("io.coil-kt:coil:2.7.0")
}
