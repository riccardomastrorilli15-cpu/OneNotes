plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.lumenotes.core_compat"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The parent app substitutes the old SESL fork for AndroidX core. These
// backported classes compile against it; never include both core AARs.
configurations.configureEach {
    exclude(group = "androidx.core", module = "core")
}

dependencies {
    implementation(libs.sesl.core)
    implementation(libs.lifecycle.runtime)
}
