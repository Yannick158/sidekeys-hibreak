import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sidekeys.hibreak"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.sidekeys.hibreak"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 25
        versionName = "1.7.4"
    }

    // The signing keystore lives OUTSIDE the repo tree so it can never be
    // published by accident (zip upload, `git add -f`, web UI, ...). Point
    // SIDEKEYS_KEYSTORE_DIR at the directory holding sidekeys.jks and
    // keystore.properties. Without it, a release build fails fast — pass
    // -PallowDebugSigning for a local, debug-signed test build (never ship it).
    val keystoreDir = System.getenv("SIDEKEYS_KEYSTORE_DIR")
    val keystoreProps = keystoreDir?.let { File(it, "keystore.properties") }
    val hasReleaseKeystore = keystoreProps?.exists() == true

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                val props = Properties().apply {
                    keystoreProps!!.inputStream().use { load(it) }
                }
                storeFile = File(keystoreDir, props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = when {
                hasReleaseKeystore -> signingConfigs.getByName("release")
                project.hasProperty("allowDebugSigning") -> {
                    logger.warn("WARNUNG: release wird mit dem lokalen DEBUG-Key signiert — NICHT veröffentlichen!")
                    signingConfigs.getByName("debug")
                }
                else -> throw GradleException(
                    "Kein Release-Keystore gefunden. Setze SIDEKEYS_KEYSTORE_DIR auf das Verzeichnis " +
                        "mit keystore.properties, oder baue ein lokales Testbuild mit " +
                        "./gradlew assembleRelease -PallowDebugSigning",
                )
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
        kotlinCompilerExtensionVersion = libs.versions.androidxComposeCompiler.get()
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // Optional: lets the user grant elevated rights without a PC. The app works without it.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(libs.junit)
}
