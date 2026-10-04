import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile =
    rootProject.file("keystore.properties")

val keystoreProperties =
    Properties()

if (keystorePropertiesFile.exists()) {
    FileInputStream(
        keystorePropertiesFile,
    ).use {
        keystoreProperties.load(it)
    }
}

android {
    namespace = "com.rovia.music"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rovia.music"
        minSdk = 36
        targetSdk = 36

        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            storeFile =
                rootProject.file(
                    keystoreProperties.getProperty(
                        "storeFile",
                    ),
                )

            storePassword =
                keystoreProperties.getProperty(
                    "storePassword",
                )

            keyAlias =
                keystoreProperties.getProperty(
                    "keyAlias",
                )

            keyPassword =
                keystoreProperties.getProperty(
                    "keyPassword",
                )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            signingConfig =
                signingConfigs.getByName(
                    "release",
                )

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt",
                ),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_17

        targetCompatibility =
            JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes +=
                "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(
        platform(
            libs.androidx.compose.bom,
        ),
    )

    implementation(
        libs.androidx.compose.ui,
    )
    implementation(
        libs.androidx.compose.foundation,
    )
    implementation(
        libs.androidx.compose.animation,
    )
    implementation(
        libs.androidx.compose.material3,
    )
    implementation(
        libs.androidx.compose.material.icons.core,
    )
    implementation(
        libs.androidx.compose.ui.tooling.preview,
    )

    implementation(
        libs.androidx.core.ktx,
    )
    implementation(
        libs.androidx.activity.compose,
    )

    implementation(
        libs.androidx.lifecycle.runtime.ktx,
    )
    implementation(
        libs.androidx.lifecycle.runtime.compose,
    )
    implementation(
        libs.androidx.lifecycle.viewmodel.compose,
    )
    implementation(
        libs.androidx.lifecycle.viewmodel.navigation3,
    )

    implementation(
        libs.androidx.navigation3.runtime,
    )
    implementation(
        libs.androidx.navigation3.ui,
    )

    implementation(
        project(":core:model"),
    )
    implementation(
        project(":core:ui"),
    )
    implementation(
        project(":core:library-api"),
    )
    implementation(
        project(":core:playback-api"),
    )
    implementation(
        project(":data:media-store"),
    )
    implementation(
        project(":data:database"),
    )
    implementation(
        project(":playback:media3"),
    )
    implementation(
        project(":feature:home"),
    )
    implementation(
        project(":feature:search"),
    )
    implementation(
        project(":feature:library"),
    )
    implementation(
        project(":feature:player"),
    )
    implementation(
        project(":feature:settings"),
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling,
    )

    testImplementation(
        libs.junit,
    )
}