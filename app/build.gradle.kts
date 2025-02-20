import java.util.Properties

plugins {
    id("com.android.application") // ✅ Ensure correct plugin usage
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt") // ✅ Add Kapt if needed
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.harmoniq"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.harmoniq"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "GOOGLE_TRANSLATE_API_KEY", "\"${project.findProperty("GOOGLE_TRANSLATE_API_KEY") ?: ""}\"")
            buildConfigField("String", "ELEVEN_LABS_API_KEY", "\"${project.findProperty("ELEVEN_LABS_API_KEY") ?: ""}\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "GOOGLE_TRANSLATE_API_KEY", "\"${project.findProperty("GOOGLE_TRANSLATE_API_KEY") ?: ""}\"")
            buildConfigField("String", "ELEVEN_LABS_API_KEY", "\"${project.findProperty("ELEVEN_LABS_API_KEY") ?: ""}\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true // ✅ Ensure BuildConfig is enabled
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    packagingOptions {
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/INDEX.LIST")
        exclude("META-INF/LICENSE")
        exclude("META-INF/NOTICE")
    }
}

dependencies {
    // ✅ Google Auth Library for OAuth2
    implementation("com.google.auth:google-auth-library-oauth2-http:1.22.0")

    // ✅ Core AndroidX and Compose dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")

    // ✅ Jetpack Compose BOM (Bill of Materials)
    implementation(platform("androidx.compose:compose-bom:2023.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // ✅ Material Icons (Fix for missing Icons.Default)
    implementation("androidx.compose.material:material-icons-extended:1.4.3")

    // ✅ Retrofit & OkHttp (Networking)
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // ✅ Testing dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
