plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val aksAbis = (project.findProperty("aks.abis") as String? ?: "arm64-v8a")
    .split(",").map { it.trim() }.filter { it.isNotEmpty() }
val ytdlpVersion = project.findProperty("aks.ytdlpVersion") as String

android {
    namespace = "com.aksdown.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aksdown.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = (project.findProperty("aks.versionCode") as String).toInt()
        versionName = project.findProperty("aks.versionName") as String
        ndk { abiFilters += aksAbis }
        buildConfigField("String", "YTDLP_VERSION", "\"$ytdlpVersion\"")
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("AKS_KEYSTORE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("AKS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AKS_KEY_ALIAS")
                keyPassword = System.getenv("AKS_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Shrinking is off on purpose: the engine library uses reflection (Jackson).
            isMinifyEnabled = false
            isShrinkResources = false
            val rel = signingConfigs.getByName("release")
            if (rel.storeFile != null) signingConfig = rel
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // Release lint must not block the build; API-level checks are guarded in code.
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        // Native executables (python, ffmpeg, qjs) must be extracted to nativeLibraryDir
        // so Android allows executing them (W^X: app data dirs are not executable).
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
        }
    }

    androidResources {
        // The bundled yt-dlp zipapp is a raw resource and must not be compressed/altered.
        noCompress += listOf("ytdlp")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// Fail early if the engine files were not fetched (scripts/fetch-engine.sh).
val checkEngine by tasks.registering {
    doLast {
        val raw = file("src/main/res/raw/ytdlp")
        if (!raw.exists() || raw.length() < 1_000_000) throw GradleException("yt-dlp missing: run scripts/fetch-engine.sh")
        aksAbis.forEach { abi ->
            val qjs = file("src/main/jniLibs/$abi/libqjs.so")
            if (!qjs.exists()) throw GradleException("QuickJS missing for $abi: run scripts/build-quickjs.sh")
        }
    }
}
tasks.named("preBuild") { dependsOn(checkEngine) }

dependencies {
    val ytdl = "0.17.2"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-process:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
