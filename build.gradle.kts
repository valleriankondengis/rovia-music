import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false

    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room3) apply false
}

val androidBuildToolsVersion =
    libs.versions.androidBuildTools.get()

subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            buildToolsVersion = androidBuildToolsVersion
        }
    }

    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            buildToolsVersion = androidBuildToolsVersion
        }
    }
}