plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val configuredVersionCode = providers.gradleProperty("LANERC_VERSION_CODE")
    .orNull
    ?.toIntOrNull()
val configuredVersionName = providers.gradleProperty("LANERC_VERSION_NAME")
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
val configuredManifestUrls = providers.gradleProperty("LANERC_UPDATE_MANIFEST_URLS")
    .orNull
    ?.trim()
    .orEmpty()
val configuredAccountApiBase = providers.gradleProperty("LANERC_ACCOUNT_API_BASE")
    .orNull
    ?.trim()
    ?.removeSuffix("/")
    ?.takeIf { it.isNotEmpty() }
val configuredAnnouncementUrls = providers.gradleProperty("LANERC_ANNOUNCEMENT_URLS")
    .orNull?.trim().orEmpty()
val configuredSourceScriptBase = providers.gradleProperty("LANERC_SOURCE_SCRIPT_BASE_URL")
    .orNull?.trim()?.removeSuffix("/")?.takeIf { it.isNotEmpty() }

val releaseStoreFile = providers.gradleProperty("LANERC_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.gradleProperty("LANERC_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("LANERC_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("LANERC_RELEASE_KEY_PASSWORD").orNull
val releaseSigningAvailable = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.juying.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.juying.app"
        minSdk = 26
        targetSdk = 34
        versionCode = configuredVersionCode ?: 2
        versionName = configuredVersionName ?: "1.1.0"
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URLS",
            buildConfigString(configuredManifestUrls)
        )
        buildConfigField(
            "String",
            "ACCOUNT_API_BASE",
            buildConfigString(configuredAccountApiBase ?: "https://api.songxiang.online")
        )
        buildConfigField(
            "String",
            "ANNOUNCEMENT_URLS",
            buildConfigString(
                configuredAnnouncementUrls.ifBlank {
                    "https://ssyjuying.oss-cn-shanghai.aliyuncs.com/api/android/announcement.json," +
                        "https://www.lanerc.app/api/android/announcement.json"
                }
            )
        )
        buildConfigField(
            "String",
            "SOURCE_SCRIPT_BASE_URL",
            buildConfigString(configuredSourceScriptBase ?: "https://ssyjuying.oss-cn-shanghai.aliyuncs.com/source-scripts")
        )
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // QuickJS
    implementation("app.cash.quickjs:quickjs-android:0.9.2")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Media
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    // GPU 视频效果管线（Anime4K 风格的本地 GLSL 线条修复与纹理放大）
    implementation("androidx.media3:media3-effect:1.2.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Android 12+ compatible animated system splash screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
