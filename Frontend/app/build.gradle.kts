import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Load key from local.properties
val localProps = Properties()
val localFile = rootProject.file("local.properties")
if (localFile.exists()) {
    localProps.load(localFile.inputStream())
}
val fxApiKey: String = localProps.getProperty("EXCHANGERATE_API_KEY") ?: ""

android {
    namespace = "com.example.stylescannerapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.stylescannerapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "EXCHANGERATE_API_KEY", "\"$fxApiKey\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { viewBinding = true; buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // Retrofit for networking
    implementation (libs.retrofit)

    // Gson for JSON parsing
    implementation (libs.converter.gson)

    // Gson library (optional if you need custom parsing)
    implementation (libs.gson)

    // OkHttp Logging (optional for debugging)
    implementation (libs.logging.interceptor)

}