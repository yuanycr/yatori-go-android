plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.yatori.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.yatori.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 41
        versionName = "0.4.1"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
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
    }

    // Compress the large Go shared library in the APK. Android extracts it
    // on install, so runtime behavior and the arm64-only ABI stay unchanged.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(files("libs/yatori-mobile.aar"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.12.1")
}
