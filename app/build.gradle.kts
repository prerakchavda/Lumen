import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.seerflix.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.seerflix.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "0.9.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Trakt client id for the "Top 10 ... Right Now" charts. Kept out of git: set
        // `trakt.clientId=...` in local.properties or the TRAKT_CLIENT_ID env var (CI secret).
        // Blank = the Top 10 rows fall back to Seerr/TMDB popularity.
        val localProps = Properties()
        rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { localProps.load(it) }
        val traktClientId = (System.getenv("TRAKT_CLIENT_ID")?.takeIf { it.isNotBlank() }
            ?: localProps.getProperty("trakt.clientId") ?: "")
            .trim().replace("\"", "")
        buildConfigField("String", "TRAKT_CLIENT_ID", "\"$traktClientId\"")
    }

    signingConfigs {
        // CI passes the release key through env vars (GitHub secrets). Local builds read the
        // gitignored keystore.properties that scripts/setup-release-signing.ps1 writes, so APKs
        // built on this PC and on GitHub share one signature and update over each other.
        val keystoreProps = Properties()
        rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { keystoreProps.load(it) }
        fun signingValue(env: String, prop: String): String? =
            System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)
        val keystorePath = signingValue("SEERFLIX_KEYSTORE_PATH", "storeFile")
        val keystorePassword = signingValue("SEERFLIX_KEYSTORE_PASSWORD", "storePassword")
        val keyAlias = signingValue("SEERFLIX_KEY_ALIAS", "keyAlias")
        val keyPassword = signingValue("SEERFLIX_KEY_PASSWORD", "keyPassword")
        if (!keystorePath.isNullOrBlank() &&
            !keystorePassword.isNullOrBlank() &&
            !keyAlias.isNullOrBlank() &&
            !keyPassword.isNullOrBlank()
        ) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            // R8: shrinks and optimizes bytecode (inlining, devirtualization, dead-code removal).
            // Compose apps are dramatically smoother with it, especially on low-end TV sticks.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.jellyfin.media3.ffmpeg.decoder)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.zxing.core)
    // Installs the bundled baseline profiles (ours + Compose/Media3) on sideloaded installs, so
    // ART precompiles hot code instead of interpreting it until background dexopt runs.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.newpipe.extractor)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
