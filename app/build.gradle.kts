plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.legs.designqa"
    compileSdk = 35
    defaultConfig { applicationId = "com.legs.designqa"; minSdk = 28; targetSdk = 35; versionCode = 4; versionName = "1.3.2" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
