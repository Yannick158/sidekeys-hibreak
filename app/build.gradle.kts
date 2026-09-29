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
        versionCode = 51
        versionName = "1.20.1"
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
            // R8: Play measures how much of the DEX is optimised and warns below
            // 25 %. Off, obfuscation sat at 0 %. The keep rules that make this
            // safe live in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Left unset without a keystore. The check below then fails only if a
            // release build is actually requested -- throwing here would run at
            // configuration time and break every task, so a fresh clone could not
            // even generate a wrapper or build a debug APK.
            signingConfig = when {
                hasReleaseKeystore -> signingConfigs.getByName("release")
                project.hasProperty("allowDebugSigning") -> {
                    logger.warn("WARNING: release is signed with the local DEBUG key -- do not publish it!")
                    signingConfigs.getByName("debug")
                }
                else -> null
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

    lint {
        // Machine-readable output, so a pre-upload check can actually read it.
        textReport = true
        xmlReport = true
        // Anything that would ship broken behaviour fails the build rather than
        // scrolling past in a log.
        warningsAsErrors = false
        abortOnError = true
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

// Refuse to produce an unsigned release, but only when one is actually asked
// for. Configuration-time failure would make the repo unusable without the
// private keystore, which only the maintainer has.
gradle.taskGraph.whenReady {
    val buildsRelease = allTasks.any { task ->
        task.project == project &&
            task.name.contains("Release") &&
            (task.name.startsWith("assemble") || task.name.startsWith("bundle"))
    }
    val keystoreDir = System.getenv("SIDEKEYS_KEYSTORE_DIR")
    val hasKeystore = keystoreDir?.let { File(it, "keystore.properties").exists() } == true
    if (buildsRelease && !hasKeystore && !project.hasProperty("allowDebugSigning")) {
        throw GradleException(
            "No release keystore. Point SIDEKEYS_KEYSTORE_DIR at the directory holding " +
                "keystore.properties, or build a local test APK with " +
                "./gradlew assembleRelease -PallowDebugSigning (never publish that one).",
        )
    }
}
