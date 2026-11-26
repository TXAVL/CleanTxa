plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.txa.cleantxa"
    compileSdk = 37

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    defaultConfig {
        applicationId = "com.txa.cleantxa"
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "1.3.5"
    }

    // Khoá ký cố định cho mọi bản phát hành (để cập nhật đè được). File nằm ở keystore/ và không đưa lên GitHub;
    // bản sao lưu ở Documents\CleanTxa-khoa-ky. Mất file này thì người dùng phải gỡ app rồi cài lại.
    val khoaKy = rootProject.file("keystore/cleantxa.keystore")
    signingConfigs {
        if (khoaKy.isFile) {
            create("cleantxa") {
                storeFile = khoaKy
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("cleantxa")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("cleantxa")?.let { signingConfig = it }
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

    androidResources {
        noCompress += "apk"
    }

    packaging {
        resources {
            excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES")
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.zxing.core)
    implementation(libs.libadb.android)
    implementation(libs.conscrypt.android)
    implementation(libs.bcpkix.jdk15to18)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
