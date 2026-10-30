plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }

android { namespace = "com.freemangnetplayer"; compileSdk = 35
    defaultConfig { applicationId = "com.freemangnetplayer"; minSdk = 24; targetSdk = 35; versionCode = 17; versionName = "0.1.16"
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
        "com.frostwire:jlibtorrent:1.2.19.0",
        "com.frostwire:jlibtorrent-android-arm:1.2.19.0",
        "com.frostwire:jlibtorrent-android-arm64:1.2.19.0",
        "com.frostwire:jlibtorrent-android-x86:1.2.19.0",
        "com.frostwire:jlibtorrent-android-x86_64:1.2.19.0"
    )
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("org.videolan.android:libvlc-all:3.6.5")
    implementation("androidx.webkit:webkit:1.12.1")
    // The small MIT session bridge is included as source so handle ownership
    // and native compatibility fixes can be reviewed and compiled together.
    implementation("com.frostwire:jlibtorrent:1.2.19.0")
    implementation("com.frostwire:jlibtorrent-android-arm:1.2.19.0")
    implementation("com.frostwire:jlibtorrent-android-arm64:1.2.19.0")
    implementation("com.frostwire:jlibtorrent-android-x86:1.2.19.0")
    implementation("com.frostwire:jlibtorrent-android-x86_64:1.2.19.0")
}
