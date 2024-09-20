    plugins {
        alias(libs.plugins.android.application)
    }

    android {
        namespace = "com.spoofsense.facedetection"
        compileSdk = 34

        defaultConfig {
            applicationId = "com.spoofsense.facedetection"
            minSdk = 24
            targetSdk = 34
            versionCode = 1
            versionName = "1.0"

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        buildTypes {
            release {
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
                )
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
    }

    dependencies {

        implementation(libs.appcompat)
        implementation(libs.material)
        implementation(libs.activity)
        implementation(libs.constraintlayout)
        implementation(libs.camera.core)
        implementation(libs.camera.lifecycle)
        implementation(libs.camera.camera2)
        implementation(libs.mlkit.face.detection)
//        implementation(libs.camera.video)
        implementation(libs.camera.view)
//        implementation(libs.camera.mlkit.vision)
//        implementation(libs.camera.extensions)
        implementation(libs.okhttp)
        testImplementation(libs.junit)
        androidTestImplementation(libs.ext.junit)
        androidTestImplementation(libs.espresso.core)
    }