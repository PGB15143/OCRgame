import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.projectocr"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }


    defaultConfig {
        applicationId = "com.example.projectocr"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // local.properties에서 값 읽기
        val localProperties = Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            localFile.inputStream().use { localProperties.load(it) }
        }

        val openaiApiKey = localProperties.getProperty("OPENAI_API_KEY", "")
        val googleVisionApiKey = localProperties.getProperty("GOOGLE_VISION_API_KEY", "")

        buildConfigField("String", "OPENAI_API_KEY", "\"$openaiApiKey\"")
        buildConfigField("String", "GOOGLE_VISION_API_KEY", "\"$googleVisionApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += "META-INF/*"
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        baseline = file("lint-baseline.xml")
    }
}

tasks.register("testClasses")

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.core:core:1.9.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation("com.google.code.gson:gson:2.9.1")
    implementation("com.google.cloud:google-cloud-vision:3.54.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.18.0")

    implementation("com.squareup.okhttp3:okhttp:4.9.1")


    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")

}