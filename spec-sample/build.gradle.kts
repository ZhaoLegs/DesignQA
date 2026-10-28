plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.legs.specsample"
    compileSdk = 35
    defaultConfig { applicationId = "com.legs.specsample"; minSdk = 28; targetSdk = 35; versionCode = 1; versionName = "0.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { debugImplementation(project(":spec-inspector")) }
