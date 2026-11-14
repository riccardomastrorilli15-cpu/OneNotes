plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.lumenotes.core_ui"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(libs.oneui.design)
    api(libs.sesl.appcompat)
    api(libs.sesl.material)
    api(libs.sesl.recyclerview)
    api(libs.sesl.preference)
}
