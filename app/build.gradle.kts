plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.legs.designqa"
    compileSdk = 35
    // No release signingConfig: keystores stay out of the repo. Sideload with assembleDebug.
    defaultConfig { applicationId = "com.legs.designqa"; minSdk = 28; targetSdk = 35; versionCode = 28; versionName = "2.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2") }
