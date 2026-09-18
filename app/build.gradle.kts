plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

import java.util.Properties

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localProp(key: String, defaultValue: String = ""): String {
    return (localProps.getProperty(key) ?: System.getenv(key) ?: defaultValue).trim()
}

android {
    namespace = "com.example.daysurpopt"
    compileSdk {
        version = release(35)
    }

    defaultConfig {
        applicationId = "com.jagones84.financialawareness"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val privacyPolicyUrl = localProp("PRIVACY_POLICY_URL", "https://www.google.com/policies/privacy/")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")

        buildConfigField(
            "String",
            "OPENROUTER_BASE_URL",
            "\"${localProp("OPENROUTER_BASE_URL", "https://openrouter.ai/")}\""
        )
        buildConfigField(
            "String",
            "OPENROUTER_HTTP_REFERER",
            "\"${localProp("OPENROUTER_HTTP_REFERER", "https://github.com/YourApp")}\""
        )
        buildConfigField(
            "String",
            "OPENROUTER_TITLE",
            "\"${localProp("OPENROUTER_TITLE", "FinancialAwareness")}\""
        )
    }

    testOptions {
        unitTests {
            // Lets pure-UI builders that call android.util.Log (e.g. PlotlySpecBuilder)
            // run in plain JVM unit tests: unmocked framework methods return defaults.
            isReturnDefaultValues = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.play.integrity)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.gson)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
