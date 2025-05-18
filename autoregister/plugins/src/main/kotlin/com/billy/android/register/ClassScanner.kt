package com.billy.android.register

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile

/**
 * 类扫描器，负责扫描符合条件的类
 */
class ClassScanner(
    private val allDirectories: List<Directory>,
    private val allJars: List<RegularFile>,
    private val registerInfoList: List<RegisterInfo>
) {
    private val scannedClasses = ConcurrentHashMap<String, Boolean>()
    private val classesToRegister = mutableMapOf<RegisterInfo, MutableSet<String>>()

    init {
        registerInfoList.forEach { info ->
            classesToRegister[info] = mutableSetOf()
        }
    }

    /**
     * 扫描所有类文件
     */
    fun scan(): Map<RegisterInfo, Set<String>> {
        scannedClasses.clear()

        // 扫描目录中的类
        allDirectories.forEach { directory ->
            scanDirectory(directory.asFile)
        }

        // 扫描JAR包中的类
        allJars.forEach { jarFile ->
            scanJarFile(jarFile.asFile)
        }

        return classesToRegister.mapValues { it.value.toSet() }
    }

    /**
     * 查找类文件，首先在当前模块查找，然后在依赖模块中查找
     */
    fun findClassFile(className: String): File? {
        // 在当前模块目录中查找
        for (directory in allDirectories) {
            val classFile = directory.asFile.resolve("$className.class")
            if (classFile.exists()) {
                Logger.e("find $className from directory name=$classFile exists=true")
                return classFile
            } else {
                Logger.e("find $className from directory name=$classFile exists=false")
            }
        }

        // 在当前模块JAR中查找
        for (jarFile in allJars) {
            try {
                JarFile(jarFile.asFile).use { jar ->
                    val entry = jar.getEntry("$className.class")
                    if (entry != null) {
                        Logger.e("find $className from jar=${jarFile.asFile.path}")
                        return jarFile.asFile
                    }
                }
            } catch (e: Exception) {
                Logger.e("Error checking jar file: ${jarFile.asFile.path}", e)
            }
        }

        return null
    }

    private fun scanDirectory(directory: File) {
        directory.walk().filter { it.isFile && it.name.endsWith(".class") }.forEach { file ->
            val relativePath = file.relativeTo(directory).path
            val className = relativePath.removeSuffix(".class").replace(File.separatorChar, '/')
            processClass(FileInputStream(file), className)
        }
    }

    private fun scanJarFile(jarFile: File) {
        try {
            JarFile(jarFile).use { jar ->
                jar.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".class") }
                    .forEach { entry ->
                        val className = entry.name.removeSuffix(".class")
                        jar.getInputStream(entry).use { input ->
                            processClass(input, className)
                        }
                    }
            }
        } catch (e: Exception) {
            Logger.e("Error scanning jar file: ${jarFile.path}", e)
        }
    }

    private fun processClass(inputStream: InputStream, className: String) {
        // 避免重复扫描
        if (scannedClasses.containsKey(className)) {
            return
        }
        scannedClasses[className] = true

        try {
            val classReader = ClassReader(inputStream)
            val classInfo = ClassInfo()

            // 扫描类信息
            classReader.accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visit(
                    version: Int,
                    access: Int,
                    name: String,
                    signature: String?,
                    superName: String?,
                    interfaces: Array<out String>?
                ) {
                    classInfo.className = name
                    classInfo.superClassName = superName ?: ""
                    classInfo.interfaces = interfaces?.toList() ?: emptyList()
                    classInfo.isAbstract = (access and Opcodes.ACC_ABSTRACT) != 0
                    classInfo.isInterface = (access and Opcodes.ACC_INTERFACE) != 0
                }

                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?
                ): MethodVisitor? {
                    // 检查是否有无参构造函数
                    if (name == "<init>" && descriptor == "()V" && (access and Opcodes.ACC_PUBLIC) != 0) {
                        classInfo.hasDefaultConstructor = true
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions)
                }
            }, ClassReader.SKIP_CODE)

            // 检查该类是否符合注册条件
            for (info in registerInfoList) {
                if (shouldRegisterClass(classInfo, info)) {
                    classesToRegister[info]?.add(classInfo.className)
                }
            }
        } catch (e: IOException) {
            Logger.e("Error processing class $className", e)
        }
    }

    private fun shouldRegisterClass(classInfo: ClassInfo, registerInfo: RegisterInfo): Boolean {
        // 排除接口和抽象类
        if (classInfo.isInterface || classInfo.isAbstract) {
            return false
        }

        // 必须有公共无参构造函数
        if (!classInfo.hasDefaultConstructor) {
            return false
        }

        // 检查包含和排除规则
        val className = classInfo.className.replace('/', '.')
        if (registerInfo.include.isNotEmpty() &&
            !registerInfo.include.any { className.matches(it.toRegex()) }
        ) {
            return false
        }
        if (registerInfo.exclude.isNotEmpty() &&
            registerInfo.exclude.any { className.matches(it.toRegex()) }
        ) {
            return false
        }

        // 检查是否实现了特定接口
        if (registerInfo.scanInterface.isNotEmpty()) {
            val interfaceName = registerInfo.scanInterface.replace('.', '/')
            if (classInfo.interfaces.contains(interfaceName)) {
                return true
            }
        }

        // 检查是否继承自特定类
        if (registerInfo.scanSuperClasses.isNotEmpty()) {
            val superClassName = classInfo.superClassName
            return registerInfo.scanSuperClasses.any {
                it.replace('.', '/') == superClassName
            }
        }

        return false
    }

    /**
     * 类信息数据类
     */
    class ClassInfo {
        var className: String = ""
        var superClassName: String = ""
        var interfaces: List<String> = emptyList()
        var isInterface: Boolean = false
        var isAbstract: Boolean = false
        var hasDefaultConstructor: Boolean = false
    }
}


