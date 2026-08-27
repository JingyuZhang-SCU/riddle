import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 正式签名：把 riddle-release.keystore 和 keystore.properties 放在本目录
// （两者都被 .gitignore 忽略，绝不入库）。缺省时 release 退回 debug 签名，
// 保证任何人 clone 后都能直接构建。
val keystoreProps = Properties()
val keystorePropsFile = rootProject.file("keystore.properties")
val haveReleaseSigning = keystorePropsFile.exists()
if (haveReleaseSigning) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}

android {
    namespace = "com.riddle.diary"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.riddle.diary"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.1.1"
    }

    signingConfigs {
        if (haveReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (haveReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
