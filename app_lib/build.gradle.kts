plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("auto-register")
}

android {
    namespace = "com.billy.app_lib"
    compileSdk = 35 // Android 15

    defaultConfig {
        minSdk = 21
        testOptions.targetSdk = 35 // Android 15
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(project(":app_lib_interface"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.25")
    implementation("androidx.core:core-ktx:1.12.0")
}

configure<com.billy.android.register.AutoRegisterExtension> {
    registerInfo = listOf(
        com.billy.android.register.RegisterInfo().apply {
            scanInterface = "com.billy.app_lib_interface.ICategory"
            scanSuperClasses = listOf("com.billy.app_lib.BaseCategory")
            codeInsertToClassName = "com.billy.app_lib_interface.CategoryManager"
            registerMethodName = "register"
            exclude = listOf(
                // 排除的类，支持正则表达式（包分隔符需要用/表示，不能用.）
                "com.billy.app_lib.BaseCategory".replace(".", "/") // 排除这个基类
            )
        }
    )
}