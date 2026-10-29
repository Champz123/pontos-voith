plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.matheus.pontosvoith"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.matheus.pontosvoith"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "V2"
    }

    // Chave FIXA: todas as versões futuras são assinadas com ela,
    // permitindo atualizar o app por cima sem perder os registros.
    signingConfigs {
        create("pessoal") {
            storeFile = file("pontosvoith.jks")
            storePassword = "PontosVoith2026"
            keyAlias = "pontosvoith"
            keyPassword = "PontosVoith2026"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("pessoal")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
}
