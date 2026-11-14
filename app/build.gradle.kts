plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.christmasgsd.weather"
    compileSdk = 35

    signingConfigs {
        create("release") {
            val storePath = System.getenv("RELEASE_STORE_FILE")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.christmasgsd.weather"
        minSdk = 23
        targetSdk = 28
        versionCode = providers.gradleProperty("appVersionCode").orNull?.toIntOrNull() ?: 6
        versionName = providers.gradleProperty("appVersionName").orNull ?: "0.5.2"
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":api"))
    implementation("androidx.appcompat:appcompat:1.7.0")
}
