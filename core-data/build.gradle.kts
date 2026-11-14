plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.lumenotes.core_data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core-model"))
    implementation(project(":core-database"))
    implementation(project(":feature-search"))
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
