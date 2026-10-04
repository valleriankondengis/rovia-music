plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.rovia.music.data.media.store"
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
    implementation(project(":core:library-api"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}