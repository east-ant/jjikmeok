plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.jjikmeok1"
    compileSdk = 36

    // tflite 파일 압축 방지
    androidResources {
        noCompress += "tflite"
    }

    defaultConfig {
        applicationId = "com.example.jjikmeok1"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")

    // 이미지 처리
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")

    // ✅ Jsoup - HTML 파싱 (크롤링용)
    implementation("org.jsoup:jsoup:1.17.2")

    // ✅ Glide - 이미지 로딩
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // ✅ RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // 기존 dependencies
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}