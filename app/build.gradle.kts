plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release signing is driven by env vars + a keystore file at app/release.keystore.
// CI: the GitHub Actions workflow base64-decodes SIGNING_KEY_STORE_BASE64 into that
// path and exports the three SIGNING_* password/alias env vars.
// Local: `cp ~/keystores/mediaflow-release.keystore app/release.keystore` then
// `export SIGNING_KEY_STORE_PASSWORD=… SIGNING_KEY_ALIAS=mediaflow SIGNING_KEY_PASSWORD=…`.
// When any of those are missing, `assembleRelease` falls back to an unsigned APK.
val releaseKeystoreFile = file("release.keystore")
val signingConfigured = releaseKeystoreFile.exists() &&
    !System.getenv("SIGNING_KEY_STORE_PASSWORD").isNullOrEmpty() &&
    !System.getenv("SIGNING_KEY_PASSWORD").isNullOrEmpty()

android {
    namespace = "com.mediaflow.proxy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mediaflow.proxy"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (signingConfigured) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = System.getenv("SIGNING_KEY_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "mediaflow"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
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
            if (signingConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Ship per-ABI APKs + one universal fallback.  Per-ABI APKs are ~10 MB vs
    // the universal ~30 MB — most users only need their own CPU's binary.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.leanback)
    implementation(libs.preference.ktx)
    implementation(libs.qrcode.kotlin)
}
