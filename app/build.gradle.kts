plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("auto-register")
}

android {
    namespace = "com.billy.android.autoregister.demo"
    compileSdk = 35 // Android 15

    defaultConfig {
        applicationId = "com.billy.android.autoregsiter.demo"
        minSdk = 21
        targetSdk = 35 // Android 15
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
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.25")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation(project(":app_lib"))
    implementation(project(":app_lib_interface"))
}

// 功能介绍：
//  在编译期扫描将打到apk包中的所有类
//  将 scanInterface的实现类 或 scanSuperClasses的子类
//  并在 codeInsertToClassName 类的 codeInsertToMethodName 方法中生成如下代码：
//  codeInsertToClassName.registerMethodName(scanInterface)
// 要点：
//  1. codeInsertToMethodName 若未指定，则默认为static块
//  2. codeInsertToMethodName 与 registerMethodName 需要同为static或非static
// 自动生成的代码示例：
/*
  在com.billy.app_lib_interface.CategoryManager.class文件中
  static
  {
    register(new CategoryA()); //scanInterface的实现类
    register(new CategoryB()); //scanSuperClass的子类
  }
 */

configure<com.billy.android.register.AutoRegisterExtension> {
    registerInfo = listOf(
        com.billy.android.register.RegisterInfo().apply {
            scanInterface = "com.billy.app_lib.IOther"
            codeInsertToClassName = "com.billy.app_lib.OtherManager"
            codeInsertToMethodName = "init" // 非static方法
            registerMethodName = "registerOther" // 非static方法
        }
    )
}