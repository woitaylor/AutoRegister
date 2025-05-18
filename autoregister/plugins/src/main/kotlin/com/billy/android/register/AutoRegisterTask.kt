package com.billy.android.register

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

abstract class AutoRegisterTask : DefaultTask() {
    // This property will be set to all Jar files available in scope
    @get:InputFiles
    abstract val allJars: ListProperty<RegularFile>

    // Gradle will set this property with all class directories that available in scope
    @get:InputFiles
    abstract val allDirectories: ListProperty<Directory>

    // Task will put all classes from directories and jars after optional modification into single jar
    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:Input
    abstract val registerInfo: ListProperty<RegisterInfo>

    @get:OutputDirectory
    abstract val appBuilderDir: DirectoryProperty

    @Internal
    val jarPaths = mutableSetOf<String>()

    @TaskAction
    fun execute() {
        Logger.e("${name} taskAction start")
        Logger.e("registerInfo=${registerInfo.get()}")
        
        // 扫描阶段 - 查找需要注册的类
        val scanner = ClassScanner(
            allDirectories.get(),
            allJars.get(),
            registerInfo.get()
        )
        val classesToRegister = scanner.scan()

        // 打印扫描结果
        for ((info, classSet) in classesToRegister) {
            Logger.e("scanned info=$info")
            Logger.e("scanned class=$classSet")
        }

        // 注入阶段 - 注入注册代码
        val injector = CodeInjector(
            appBuilderDir.get().asFile,
            allDirectories.get(),
            allJars.get()
        )
        injector.injectRegisterCode(classesToRegister)

        // 打印注入结果
        for ((info, _) in classesToRegister) {
            Logger.e("injected info=$info")
        }

        // 将所有内容打包到输出JAR文件
        packToJar()
    }

    /**
     * 将所有内容打包到输出JAR文件
     */
    private fun packToJar() {
        jarPaths.clear()
        val jarOutput = JarOutputStream(
            BufferedOutputStream(
                FileOutputStream(
                    output.get().asFile
                )
            )
        )

        // 从JAR文件中复制类
        allJars.get().forEach { file ->
            println("handling " + file.asFile.absolutePath)
            val jarFile = JarFile(file.asFile)
            jarFile.entries().iterator().forEach { jarEntry ->
                if (jarEntry.name.contains("CategoryManager")) {
                    Logger.e("find CategoryManager from jar: ${jarEntry.name} ")
                }
                
                registerInfo.get()
                    .firstOrNull {
                        val index = jarEntry.name.indexOf(".")
                        if (index != -1) {
                            jarEntry.name.substring(0, index) == it.entryName
                        } else {
                            it.entryName == jarEntry.name
                        }
                    }
                    ?.apply {
                        Logger.w("jar match name=${jarEntry.name}")
                        jarOutput.writeEntity(jarEntry.name, File(insertFilePath).inputStream())
                    } ?: run {
                        jarOutput.writeEntity(jarEntry.name, jarFile.getInputStream(jarEntry))
                    }
            }
            jarFile.close()
        }

        // 从目录中复制类
        allDirectories.get().forEach { directory ->
            println("handling " + directory.asFile.absolutePath)
            directory.asFile.walk().forEach { file ->
                if (file.isFile) {
                    // 获取相对路径
                    val relativePath = directory.asFile.toURI().relativize(file.toURI()).getPath()
                    if (relativePath.contains("CategoryManager")) {
                        Logger.e("find CategoryManager from directory: ${relativePath}")
                    }
                    
                    registerInfo.get().firstOrNull {
                        it.entryName == relativePath.replace(
                            File.separatorChar,
                            '/'
                        )
                    }?.apply {
                        Logger.w("dir match path=${relativePath}")
                        jarOutput.writeEntity(name, File(insertFilePath).inputStream())
                    } ?: run {
                        jarOutput.writeEntity(
                            relativePath.replace(File.separatorChar, '/'),
                            file.inputStream()
                        )
                    }
                }
            }
        }
        jarOutput.close()
    }

    // writeEntity methods check if the file has name that already exists in output jar
    private fun JarOutputStream.writeEntity(name: String, inputStream: InputStream) {
        // check for duplication name first
        if (jarPaths.contains(name)) {
            printDuplicatedMessage(name)
        } else {
            putNextEntry(JarEntry(name))
            inputStream.copyTo(this)
            closeEntry()
            jarPaths.add(name)
        }
    }

    private fun JarOutputStream.writeEntity(relativePath: String, byteArray: ByteArray) {
        // check for duplication name first
        if (jarPaths.contains(relativePath)) {
            printDuplicatedMessage(relativePath)
        } else {
            putNextEntry(JarEntry(relativePath))
            write(byteArray)
            closeEntry()
            jarPaths.add(relativePath)
        }
    }

    private fun printDuplicatedMessage(name: String) {
        Logger.w("发现重复的文件: $name")
    }
} 