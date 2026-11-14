plugins { id("com.android.library") }
android {
    namespace = "com.qcbru.flashcards.storage"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        javaCompileOptions { annotationProcessorOptions { arguments["room.schemaLocation"] = "$projectDir/schemas" } }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    api("androidx.room:room-runtime:2.8.5")
    annotationProcessor("androidx.room:room-compiler:2.8.5")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
