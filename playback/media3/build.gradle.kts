plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.rovia.music.playback.media3"
    compileSdk = 37

    defaultConfig {
        minSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:playback-api"))

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    implementation(libs.kotlinx.coroutines.core)
}