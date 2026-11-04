plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.legs.designqa"
    compileSdk = 35
    defaultConfig { applicationId = "com.legs.designqa"; minSdk = 28; targetSdk = 35; versionCode = 34; versionName = "2.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2") }
