plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }

android { namespace = "com.omniplayer"; compileSdk = 35
    defaultConfig { applicationId = "com.freemangnetplayer"; minSdk = 24; targetSdk = 35; versionCode = 40; versionName = "0.3.12"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        providers.gradleProperty("targetAbi").orNull?.let { requested ->
            require(requested in setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
            ndk { abiFilters += requested }
        }
    }
    val signingPath = System.getenv("FMP_KEYSTORE")
    if (!signingPath.isNullOrBlank()) {
        signingConfigs.create("fmp") {
            storeFile = file(signingPath)
            storeType = "PKCS12"
            storePassword = System.getenv("FMP_STORE_PASSWORD")
            keyAlias = "fmp"
            keyPassword = System.getenv("FMP_STORE_PASSWORD")
        }
        buildTypes.getByName("debug").signingConfig = signingConfigs.getByName("fmp")
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("fmp")
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("main").assets.srcDir("../third-party")
}

// The wrapper's 2018 native dependency pauses the session on Android 11+
// when SELinux rejects its netlink route socket. Upgrade Java and every ABI
// together; mixing JNI and Java versions is unsupported.
dependencies {
    implementation(project(":core"))
    implementation(project(":magnet"))
    implementation(project(":live"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
}
