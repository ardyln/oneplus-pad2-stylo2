plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.ardyln.stylo2"
    compileSdk = 35
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.ardyln.stylo2"
        minSdk = 31
        // 28: legacy Bluetooth permissions, no runtime BLUETOOTH_CONNECT prompt
        targetSdk = 28
        versionCode = 12
        versionName = "0.12"
    }

    signingConfigs {
        create("release") {
            // Personal key, kept out of the repo; see tools/build.sh
            storeFile = file(System.getenv("KEYSTORE") ?: "${System.getProperty("user.home")}/tools/stylo2-release.jks")
            storePassword = System.getenv("KS_PASS")
            keyAlias = "stylo2"
            keyPassword = System.getenv("KS_PASS")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        // targetSdk 28 is deliberate (see above)
        disable += "ExpiredTargetSdkVersion"
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.14.0")
}
