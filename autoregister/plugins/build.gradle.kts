plugins {
    `kotlin-dsl`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.2.1"
//    alias(libs.plugins.kotlin.jvm)
}

group = "com.billy.android"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation("com.android.tools.build:gradle:8.7.0")
    implementation("org.ow2.asm:asm:9.5")
    implementation("org.ow2.asm:asm-commons:9.5")
    implementation("org.ow2.asm:asm-util:9.5")
}

gradlePlugin {
    plugins {
        create("autoRegister") {
            id = "auto-register"
            implementationClass = "com.billy.android.register.AutoRegisterPlugin"
            displayName = "Auto Register Plugin"
            description = "A plugin to automatically register classes to their managers"
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}


