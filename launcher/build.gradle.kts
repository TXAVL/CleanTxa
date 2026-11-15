plugins {
    alias(libs.plugins.android.application)
}

val launcherAppId = providers.gradleProperty("launcherApplicationId").orElse("com.gathin.tvhome").get()

android {
    namespace = "com.gathin.tvhome"
    compileSdk = 37

    defaultConfig {
        applicationId = launcherAppId
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}

