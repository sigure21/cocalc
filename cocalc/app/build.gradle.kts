plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.cocalc"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.cocalc"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ NDK / CMake 설정 (여기 중요)
        externalNativeBuild {
            cmake {
                // 필요 시 C++ 표준 지정(권장)
                cppFlags += "-std=c++17"
            }
        }

        // ✅ ABI 필터(선택이지만 실습에서 빌드 빠르고 안정적)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
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

    // ✅ CMakeLists.txt 연결 (여기 중요)
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            // 설치된 CMake 버전에 맞춰 필요 시 지정 가능 (선택)
            // version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
