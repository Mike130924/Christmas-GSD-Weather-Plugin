plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.christmasgsd.weather"
    compileSdk = 35
    defaultConfig { applicationId = "com.christmasgsd.weather"; minSdk = 23; targetSdk = 28; versionCode = 1; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation(project(":api")); implementation("androidx.appcompat:appcompat:1.7.0") }
