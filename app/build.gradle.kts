import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.bankingpages"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bankingpages"
        minSdk = 29
        targetSdk = 34
        versionCode = 27
        versionName = "3.6"
    }

    // One key (in signing/, git-ignored) signs debug and release alike, so the SHA-1
    // registered for Google sign-in stays the same for every build.
    val signingProps = rootProject.file("signing/keystore.properties").takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use(::load) } }

    signingConfigs {
        if (signingProps != null) create("app") {
            storeFile = rootProject.file("signing/banking-pages.jks")
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            if (signingProps != null) signingConfig = signingConfigs.getByName("app")
        }
        release {
            if (signingProps != null) signingConfig = signingConfigs.getByName("app")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }

    // The release variant is what goes on the phone (far smoother than debug); lint has a known crash on it.
    lint {
        checkReleaseBuilds = false
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-process:2.8.5")

    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Fingerprint / face / screen-lock gate in front of the vault.
    implementation("androidx.biometric:biometric:1.1.0")
    // Google sign-in + Drive permission, background backup and the daily logo check.
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Photo orientation, password-protected PDFs (e-Aadhaar) and vector bank logos.
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.caverock:androidsvg-aar:1.4")
    // Google's document scanner (edge detection, corner adjust) for documents, cheques and cards.
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    // Long-press drag to rearrange the home rows.
    implementation("sh.calvin.reorderable:reorderable:2.5.1")
    // Liquid Glass for the floating bar.
    implementation("io.github.kyant0:backdrop:1.0.0")
}
