plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace  = "com.alpha.vision.pro.gallery.data"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
}
dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.exifinterface)
}
