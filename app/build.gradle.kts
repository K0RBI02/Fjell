plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

/** A setting from the environment (CI secrets) or from ~/.gradle/gradle.properties (local builds). */
fun setting(name: String): String? = providers.environmentVariable(name).orElse(providers.gradleProperty(name)).orNull

fun git(vararg args: String): String? = runCatching {
    providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim()
}.getOrNull()?.takeIf { it.isNotEmpty() }

// The version comes from git, so a tag and the app can't disagree: on tag v1.1 it's "1.1",
// five commits later "1.1+5". The code counts commits, so every build installs over the last.
val appVersionCode = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
val appVersionName = git("describe", "--tags", "--match", "v*")
    ?.removePrefix("v")
    ?.replace(Regex("-(\\d+)-g[0-9a-f]+$"), "+$1")
    ?: "dev"

/** Present when the release key is available: in CI from secrets, locally from ~/.gradle/gradle.properties. */
val releaseKeystore = setting("HELGELAND_KEYSTORE")?.let(::file)?.takeIf { it.exists() }

android {
    namespace = "dev.korbi.helgeland"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "dev.korbi.helgeland"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = setting("HELGELAND_KEYSTORE_PASSWORD")
                keyAlias = setting("HELGELAND_KEY_ALIAS")
                keyPassword = setting("HELGELAND_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Without the key (pull requests, Dependabot) it falls back to the debug key, so CI can still build.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            // Developer builds only: -Phelgeland.debuggable lets `adb run-as` reach the app's data.
            isDebuggable = providers.gradleProperty("helgeland.debuggable").isPresent
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    lint {
        // Style nudges and "newer version available" (Dependabot's job) would only drown real findings.
        disable += setOf("UseKtx", "GradleDependency")
        warningsAsErrors = false
        abortOnError = true
    }
    testOptions {
        // Robolectric renders our real fonts, strings and drawables for the screenshot tests.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric reaches into java.io internals that newer JDKs lock away.
            it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

roborazzi {
    // Reference images live with the tests, so a review shows exactly what changed.
    outputDir.set(file("src/test/screenshots"))
}
