plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val generatedWebAssets = layout.buildDirectory.dir("generated/assets")

val syncCartridgeWebAssets by tasks.registering(Sync::class) {
    from(rootProject.projectDir.parentFile) {
        include(
            "index.html",
            "style.css",
            "app.js",
            "romm.js",
            "config.js",
            "osk.js",
            "gamepad.js",
            "host-bridge.js",
            "rtc.js",
            "build-stamp.js",
        )
    }
    into(generatedWebAssets.map { it.dir("web") })
}

android {
    namespace = "org.moveweight.cartridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.moveweight.cartridge"
        minSdk = 24
        targetSdk = 35
        versionCode = 32
        versionName = "0.31.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(generatedWebAssets)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        abortOnError = false
        checkReleaseBuilds = false
    }
}

tasks.named("preBuild") {
    dependsOn(syncCartridgeWebAssets)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.12.1")
    testImplementation("junit:junit:4.13.2")
}
