plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.rovia.music.data.media.store"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
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
    implementation(project(":core:library-api"))
    implementation(project(":data:database"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Media3 APIs imported directly by EmbeddedLyricsRepository.
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.extractor)
    implementation(libs.androidx.media3.inspector)
}