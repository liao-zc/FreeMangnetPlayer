plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }

android { namespace = "com.omniplayer"; compileSdk = 35
    defaultConfig { applicationId = "com.freemangnetplayer"; minSdk = 24; targetSdk = 35; versionCode = 29; versionName = "0.3.1"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
configurations.configureEach {
    resolutionStrategy.force(
        "org.libtorrent4j:libtorrent4j:2.1.0-39",
        "org.libtorrent4j:libtorrent4j-android-arm:2.1.0-39",
        "org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-39",
        "org.libtorrent4j:libtorrent4j-android-x86:2.1.0-39",
        "org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-39"
    )
}

dependencies {
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
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("org.videolan.android:libvlc-all:3.6.5")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // The small MIT session bridge is included as source so handle ownership
    // and native compatibility fixes can be reviewed and compiled together.
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-x86:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-39")
}
