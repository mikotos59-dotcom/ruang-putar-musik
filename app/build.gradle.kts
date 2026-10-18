import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------- versi & kunci
// Nomor build: di GitHub Actions = 100 + nomor run (selalu naik), di PC = 3.
val nomorBuild = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(100)) ?: 3
// Repo GitHub tempat aplikasi mengecek pembaruan (otomatis dari Actions).
val repoGithub = System.getenv("GITHUB_REPOSITORY") ?: "mikotos59-dotcom/ruang-putar-musik"
// Versi NewPipeExtractor (mesin YouTube) — diperbarui otomatis oleh workflow mingguan.
val versiNewPipe = providers.gradleProperty("npeVersi").getOrElse("v0.26.5")
// Kunci tanda tangan: dari GitHub Secrets (KUNCI_BASE64 -> file), atau file kunci/ruangputar.jks di PC.
val fileKunci = System.getenv("KUNCI_FILE")?.let { file(it) } ?: rootProject.file("kunci/ruangputar.jks")

android {
    namespace = "id.ruangputar.musik"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.ruangputar.musik"
        minSdk = 24            // Android 7.0
        targetSdk = 36         // Android 16
        versionCode = nomorBuild
        versionName = "0.2.1"
        buildConfigField("String", "REPO_GITHUB", "\"$repoGithub\"")
        buildConfigField("String", "VERSI_NEWPIPE", "\"$versiNewPipe\"")
    }

    // Kunci tanda tangan TETAP, supaya APK versi baru bisa dipasang di atas versi lama
    // tanpa uninstall. Jangan diganti.
    signingConfigs {
        create("rilis") {
            storeFile = fileKunci
            storePassword = System.getenv("KUNCI_SANDI") ?: "ruangputar-musik"
            keyAlias = System.getenv("KUNCI_ALIAS") ?: "ruangputar"
            keyPassword = System.getenv("KUNCI_SANDI") ?: "ruangputar-musik"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (fileKunci.exists()) signingConfig = signingConfigs.getByName("rilis")
        }
        debug {
            if (fileKunci.exists()) signingConfig = signingConfigs.getByName("rilis")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true   // wajib untuk NewPipeExtractor di bawah Android 13
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // pemeriksaan gaya kode tidak boleh menggagalkan build APK
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES", "/META-INF/*.kotlin_module")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    implementation("com.github.teamnewpipe:NewPipeExtractor:$versiNewPipe")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")

    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.8.0")
}
