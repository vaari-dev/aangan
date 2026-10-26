plugins {
    alias(libs.plugins.android.application)
}

// The signing key must stay the same for future updates to install over this one. Release builds
// use AANGAN_KEYSTORE and AANGAN_KEY_PASS when set (CI's release job sets both); otherwise they're
// signed with the local debug key, which is fine for trying a build but can't update a release.
val releaseKeystore: String? = providers.environmentVariable("AANGAN_KEYSTORE").orNull
val releaseKeyPass: String? = providers.environmentVariable("AANGAN_KEY_PASS").orNull

android {
    namespace = "dev.vaari.aangan"
    // Compiled against the newest SDK, Android 17 QPR2.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "dev.vaari.aangan"
        // Android 9 and up, targeting Android 16's behaviour. (Targeting 17 would need a local-network
        // permission prompt for the phone page.)
        minSdk = 28
        targetSdk = 36
        // The release version. Raising versionName on main publishes it (see .github/workflows/ci.yml);
        // versionCode must go up with every release, and CHANGELOG.md needs a section for the new name.
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

    // No encrypted dependency report in the APK or bundle: there are no dependencies, and F-Droid
    // and reproducible builds need the files to be the same whoever builds them.
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
        // The tests read the real strings, assets and festival calendar.
        unitTests.isIncludeAndroidResources = true
    }
}

base {
    archivesName = "aangan"
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

// Lets -Drobolectric.graphicsMode and -Drobolectric.sqliteMode on the command line reach the test JVM
// (see src/test/resources/robolectric.properties). Read as providers, so the configuration cache
// notices when they change.
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
