# AutoRegister

一个用于自动注册组件的Gradle插件，支持AGP 8.7+和Gradle 8.9+。

## 功能介绍

在apk打包过程中，对编译后的所有class（包含jar包中的class）进行扫描，将符合条件的类扫描出来，并在目标类的static块中生成注册代码。

适用场景：
- 工厂模式中自动注册产品实现类
- 策略模式中自动注册策略实现类
- 接口及其管理类在基础库中定义，其实现类在不同的module中，需要在主工程中进行注册

## 使用方法

### 1. 添加插件依赖

在项目根目录的build.gradle.kts中添加：

```kotlin
buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.0")
        classpath("com.billy.android:autoregister:1.0.0")
    }
}
```

### 2. 应用插件并配置

在应用模块的build.gradle.kts中：

```kotlin
plugins {
    id("com.android.application")
    id("auto-register")
}

autoregister {
    registerInfo = listOf(
        mapOf(
            "scanInterface" to "com.example.ICategory",
            "scanSuperClasses" to listOf("com.example.BaseCategory"),
            "codeInsertToClassName" to "com.example.CategoryManager",
            "registerMethodName" to "register",
            "exclude" to listOf(
                "com.example.BaseCategory".replace(".", "/")
            )
        ),
        mapOf(
            "scanInterface" to "com.example.IOther",
            "codeInsertToClassName" to "com.example.OtherManager",
            "codeInsertToMethodName" to "init", // 非static方法
            "registerMethodName" to "registerOther" // 非static方法
        )
    )
}
```

## 配置项说明

- `scanInterface`：(必须) 接口名（完整类名），所有直接实现此接口的类将会被收集
- `codeInsertToClassName`：(必须) 类名（完整类名），将收集到的类注册代码插入到此类中
- `codeInsertToMethodName`：方法名，注册代码将插入到此方法中，若未指定，则默认为static块（方法名为：`<clinit>`）
- `registerMethodName`：(必须) 注册方法名，用于注册收集到的类实例
- `scanSuperClasses`：类名（完整类名）或类名数组，所有直接继承此类的子类将会被收集
- `include`：需要扫描的类（正则表达式，包分隔符用/代替），默认为所有类
- `exclude`：不需要扫描的类（正则表达式，包分隔符用/代替）

## 注意事项

1. 扫描到的类不能为接口或抽象类，并且必须提供public无参构造方法
2. 如需排除抽象类，请在exclude中添加相应的类名
3. 生成的代码格式为：`注册类.注册方法(扫描到的类实例)` 