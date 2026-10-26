plugins {
    alias(libs.plugins.android.application)
}

// Without both, release builds are signed with the debug key and can't update a real install.
val releaseKeystore: String? = providers.environmentVariable("AANGAN_KEYSTORE").orNull
val releaseKeyPass: String? = providers.environmentVariable("AANGAN_KEY_PASS").orNull

android {
    namespace = "dev.vaari.aangan"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "dev.vaari.aangan"
        // Targeting 37 would need a local-network permission prompt for the phone page.
        minSdk = 28
        targetSdk = 36
        // A new versionName on main is released by CI, and needs a CHANGELOG.md section.
        versionCode = 13
        versionName = "1.4"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = releaseKeyPass
                keyAlias = "aangan"
                keyPassword = releaseKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The encrypted dependency report would break reproducible builds (and F-Droid).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        lintConfig = file("lint.xml")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

base {
    archivesName = "aangan"
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

// Passes -Drobolectric.* through to the test JVM; as providers, so the configuration cache sees changes.
val robolectricModes = listOf("robolectric.graphicsMode", "robolectric.sqliteMode")
    .associateWith { providers.systemProperty(it) }
tasks.withType<Test>().configureEach {
    robolectricModes.forEach { (name, value) ->
        value.orNull?.let { systemProperty(name, it) }
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
