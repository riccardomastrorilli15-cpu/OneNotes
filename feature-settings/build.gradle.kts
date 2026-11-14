plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.lumenotes.feature_settings"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.datastore)
    implementation(libs.coroutines.android)
}
