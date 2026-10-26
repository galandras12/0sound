plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zerosound.anc"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zerosound.anc"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"
    }

    // TEST signature only - NOT for production use. See README.
    signingConfigs {
        create("test") {
            storeFile = file("keystore/0sound-test.jks")
            storePassword = "0soundtest"
            keyAlias = "0sound-test"
            keyPassword = "0soundtest"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("test")
        }
        debug {
            signingConfig = signingConfigs.getByName("test")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation("junit:junit:4.13.2")
}

// ./gradlew exportReleaseApk  ->  Release/0Sound-v<versionName>.apk
tasks.register<Copy>("exportReleaseApk") {
    dependsOn("assembleRelease")
    val versionName = android.defaultConfig.versionName
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("Release"))
    rename { "0Sound-v$versionName.apk" }
}
