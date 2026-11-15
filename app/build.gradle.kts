plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.lumenotes.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.lumenotes.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    buildFeatures { viewBinding = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Opt in only for an internal test APK; the standard debug key is not a release key.
            if (providers.gradleProperty("lume.signTestRelease").orNull == "true") {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging.resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
}

dependencies {
    implementation(project(":core-compat"))
    implementation(project(":core-model"))
    implementation(project(":core-database"))
    implementation(project(":core-data"))
    implementation(project(":core-ui"))
    implementation(project(":feature-editor"))
    implementation(project(":feature-search"))
    implementation(project(":feature-drawing"))
    implementation(project(":feature-settings"))
    implementation(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    // Keep Activity aligned with the explicitly backported AndroidX core APIs.
    implementation(libs.activity) { version { strictly(libs.versions.activity.get()) } }
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso)
}
