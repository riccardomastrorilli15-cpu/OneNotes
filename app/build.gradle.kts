plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.onenotes.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.onenotes.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/AL2.0",
                "/META-INF/LGPL2.1",
                "/META-INF/LICENSE.md",
                "/META-INF/LICENSE-notice.md",
                "/META-INF/DEPENDENCIES"
            )
        }
    }
}

// Keep the room schemas for future migrations.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

configurations.all {
    // The One UI (sesl) libraries are patched forks of the AndroidX / Material artifacts:
    // they ship the very same packages, so Google's originals MUST be excluded to avoid
    // "duplicate class" errors. These two are the only exclusions documented by oneui-core;
    // every other forked module (fragment, recyclerview, preference, drawerlayout,
    // coordinatorlayout, material) is resolved through the sesl artifacts declared below.
    exclude(group = "androidx.appcompat", module = "appcompat")
    exclude(group = "androidx.core", module = "core")
}

dependencies {
    // ---------------------------------------------------------------- One UI
    implementation(libs.oneui.design)
    implementation(libs.sesl.appcompat)
    implementation(libs.sesl.material)
    implementation(libs.sesl.recyclerview)
    implementation(libs.sesl.swiperefreshlayout)
    implementation(libs.sesl.preference)
    implementation(libs.sesl.drawerlayout)
    implementation(libs.sesl.coordinatorlayout)

    // --------------------------------------------------------------- Jetpack
    implementation(libs.androidx.activity.ktx)
    // Only the Kotlin extensions (viewModels(), commit {}): the classes of the fragment
    // library come from the sesl fork pulled in by sesl-appcompat, so the Google artifact
    // that this module asks for is filtered out to avoid duplicate classes.
    implementation(libs.androidx.fragment.ktx) {
        exclude(group = "androidx.fragment", module = "fragment")
    }
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    // Biometric: the sesl stack ships its own fork of androidx.fragment, so the stock
    // module that this artifact asks for has to be filtered out.
    implementation(libs.androidx.biometric) {
        exclude(group = "androidx.fragment", module = "fragment")
    }

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    // ------------------------------------------------------------ Google / IO
    // Same as above: Google Sign-In would drag in the stock androidx.fragment.
    implementation(libs.play.services.auth) {
        exclude(group = "androidx.fragment", module = "fragment")
    }
    implementation(libs.okhttp)
    implementation(libs.mlkit.text.recognition)
}
