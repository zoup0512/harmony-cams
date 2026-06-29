plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.nvr"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        consumerProguardFiles("consumer-rules.pro")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("test.jks")
            storePassword = "android"
            keyAlias = "key0"
            keyPassword = "android"
        }
        create("release") {
            storeFile = rootProject.file("test.jks")
            storePassword = "android"
            keyAlias = "key0"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // ComboLite core - 作为插件时使用 compileOnly（宿主提供）
    compileOnly(libs.combolite.core)

    // Koin dependency injection
    compileOnly("io.insert-koin:koin-core:4.1.0")

    // VLC Media Player dependency
    implementation("org.videolan.android:libvlc-all:3.6.5")

    // RTSP server for phone camera streaming
    implementation("com.github.pedroSG94:RTSP-Server:1.4.1")
    implementation("com.github.pedroSG94.RootEncoder:library:2.7.2")

    // Compose dependencies
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.runtime:runtime")

    // Lifecycle / ViewModel for Compose（状态上提，取代 40+ remember）
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // Video recording / camera / media
    implementation(libs.androidx.media)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // Image loading（替代主线程 BitmapFactory.decodeFile，修复截图 OOM/ANR）
    implementation(libs.coil.compose)

    // SMB/CIFS support for network discovery
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")

    // Kotlin parcelize
    implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime:2.1.0")
}
