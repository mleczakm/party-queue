import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Single source of truth for the version: version.properties (SemVer). Tag releases as v<versionName>.
val appVersionName: String = Properties()
    .apply { rootProject.file("version.properties").inputStream().use { load(it) } }
    .getProperty("versionName")
val appVersionCode: Int = appVersionName.substringBefore('-').split('.').map(String::toInt)
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

android {
    namespace = "pl.mleczki.partyqueue"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "pl.mleczki.partyqueue"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    // One signing key for local and CI builds, debug and release, so APKs from anywhere install over each other: the keystore
    // named by SIGNING_KEYSTORE (CI restores it from the SIGNING_KEYSTORE_B64 secret, see scripts/set-signing-secret.sh), else
    // ~/.android/debug.keystore. Without either, Android's own debug key is used.
    val projectKeystore = File(System.getenv("SIGNING_KEYSTORE") ?: "${System.getProperty("user.home")}/.android/debug.keystore")
    val signing = if (projectKeystore.exists()) {
        signingConfigs.create("project") {
            storeFile = projectKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    } else {
        signingConfigs.getByName("debug")
    }

    buildFeatures {
        compose = true
    }
    buildTypes {
        debug {
            signingConfig = signing
        }
        release {
            // Not minified (GeckoView, Ktor and Compose would need keep rules) but optimised and not debuggable.
            // Sideloadable, signed with the key above; a store release would need its own key.
            isMinifyEnabled = false
            signingConfig = signing
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources {
        // The default pattern drops "_*" directories, which would remove the add-ons' _locales.
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:!CVS:!thumbs.db:!picasa.ini:!*~"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
        // GeckoView gates parts of its API (e.g. GeckoPreferenceController) behind an experimental marker; we use them on purpose.
        disable += "UnsafeOptInUsageError"
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/io.netty.versions.properties")
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:157.0.20261005135250")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    implementation("io.ktor:ktor-server-cio:3.6.0")
    implementation("io.ktor:ktor-server-websockets:3.6.0")

    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    implementation("com.google.zxing:core:3.5.4")

    testImplementation("junit:junit:4.13.2")
    // android.jar only has stubs for org.json; unit tests need the real thing.
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.ktor:ktor-server-test-host:3.6.0")
}

// Third-party add-ons are not committed; download and verify them before building.
val fetchAddons by tasks.registering(Exec::class) {
    workingDir = rootDir
    commandLine("bash", "scripts/fetch-addons.sh")
    onlyIf {
        listOf("ublock", "bgplay").any { !file("src/main/assets/extensions/$it/manifest.json").exists() }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchAddons) }
