import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.ohsung.marketbrief"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ohsung.marketbrief"
        minSdk = 26
        targetSdk = 36
        // CI passes the run number so every build can install over the previous one.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "0.1." + (System.getenv("VERSION_CODE") ?: "0")
        // Personal data.go.kr key, injected only into locally built APKs (local.properties / env), never committed.
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
        }
        val molitKey = System.getenv("MOLIT_KEY") ?: localProps.getProperty("molit.key", "")
        buildConfigField("String", "MOLIT_KEY", "\"$molitKey\"")
        // The owner's home, for personal builds only. Kept in local.properties (git-ignored), never in the repo.
        listOf("region", "seq", "name", "area", "label", "nearby").forEach { k ->
            val v = localProps.getProperty("myhome.$k", "").replace("\\", "").replace("\"", "")
            buildConfigField("String", "MYHOME_${k.uppercase()}", "\"$v\"")
        }
    }

    signingConfigs {
        // Development key for personal sideloaded builds only (not secret, not for Play Store).
        // Keeping it in the repo means every build — local or CI — can update the installed app.
        create("dev") {
            storeFile = rootProject.file("keystore/dev.jks")
            storePassword = "devpass123"
            keyAlias = "dev"
            keyPassword = "devpass123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("dev")
        }
        debug {
            signingConfig = signingConfigs.getByName("dev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    testImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.core:core-ktx:1.17.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.79.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.79.0")
}
