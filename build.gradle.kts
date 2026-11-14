import org.jlleitschuh.gradle.ktlint.KtlintExtension

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 supplies Kotlin; this selects a compiler able to consume Room 3's metadata.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint)
}

ktlint { version.set("1.8.0") }

// SESL publishes replacements in the original AndroidX/Material packages.
// Keep exactly one implementation of each class in the app's runtime graph.
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    extensions.configure<KtlintExtension> { version.set("1.8.0") }
    // Isolate the fork substitutions to the application. Standalone library
    // instrumentation tests still need the standard AndroidX core artifacts.
    if (name == "app") {
        configurations.configureEach {
            exclude(group = "androidx.appcompat", module = "appcompat")
            exclude(group = "androidx.core", module = "core")
            exclude(group = "androidx.fragment", module = "fragment")
            exclude(group = "androidx.recyclerview", module = "recyclerview")
            exclude(group = "androidx.drawerlayout", module = "drawerlayout")
            exclude(group = "androidx.coordinatorlayout", module = "coordinatorlayout")
            exclude(group = "androidx.customview", module = "customview")
            exclude(group = "androidx.viewpager", module = "viewpager")
            exclude(group = "androidx.viewpager2", module = "viewpager2")
            exclude(group = "androidx.preference", module = "preference")
            exclude(group = "com.google.android.material", module = "material")
        }
    }
}
