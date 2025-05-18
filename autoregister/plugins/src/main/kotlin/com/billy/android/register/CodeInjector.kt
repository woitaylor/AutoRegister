package com.billy.android.register

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.AdviceAdapter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.jar.JarFile

/**
 * 代码注入器，负责注入注册代码
 */
class CodeInjector(
    private val buildDirectory: File,
    private val allDirectories: List<Directory>,
    private val allJars: List<RegularFile>
) {
    private val scanner: ClassScanner by lazy {
        ClassScanner(allDirectories, allJars, emptyList())
    }

    /**
     * 注入注册代码
     */
    fun injectRegisterCode(
        registerInfoMap: Map<RegisterInfo, Set<String>>
    ) {
        // 为每个配置注入代码
        for ((info, registerClasses) in registerInfoMap) {
            if (registerClasses.isEmpty()) continue

            val targetClassName = info.codeInsertToClassName.replace('.', '/')
            val targetMethodName = info.codeInsertToMethodName
            val registerMethodName = info.registerMethodName

            // 查找目标类文件
            val targetClassFile = findClassFile(info, targetClassName)
            Logger.e("injectRegisterCode targetClassFile path=${targetClassFile?.path}")
            if (targetClassFile != null) {
                injectCodeToClass(
                    targetClassFile,
                    targetClassName,
                    targetMethodName,
                    registerMethodName,
                    registerClasses
                )
            } else {
                Logger.e("目标类 $targetClassName 未找到")
            }
        }
    }

    private fun findClassFile(info: RegisterInfo, className: String): File? {
        // 使用Scanner查找类文件
        val sourceClassFile = scanner.findClassFile(className)
        if (sourceClassFile != null) {
            Logger.e("$className sourceClassFile path=${sourceClassFile.absolutePath}")
            // 如果找到了，则复制到临时目录
            info.entryName = className
            
            // 创建临时目录
            val tempDir = File(buildDirectory, "classes")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }
            
            val simpleClassName = className.substring(className.lastIndexOf('/') + 1)
            val tempFile = File(tempDir, "${simpleClassName}Transform.class")
            
            if (sourceClassFile.isDirectory) {
                // 如果是目录，直接复制文件
                val classFile = File(sourceClassFile, "$className.class")
                if (classFile.exists()) {
                    FileOutputStream(tempFile).use { output ->
                        classFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    info.insertFilePath = tempFile.path
                    return tempFile
                }
            } else if (sourceClassFile.extension == "jar") {
                // 如果是JAR文件，从JAR中提取
                JarFile(sourceClassFile).use { jar ->
                    val entry = jar.getEntry("$className.class")
                    if (entry != null) {
                        jar.getInputStream(entry).use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        info.insertFilePath = tempFile.path
                        return tempFile
                    }
                }
            } else {
                // 直接是class文件
                FileOutputStream(tempFile).use { output ->
                    sourceClassFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                info.insertFilePath = tempFile.path
                return tempFile
            }
        }
        return null
    }

    private fun injectCodeToClass(
        classFile: File,
        className: String,
        methodName: String,
        registerMethodName: String,
        registerClasses: Set<String>
    ) {
        try {
            val classReader = ClassReader(FileInputStream(classFile))
            val classWriter = ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)

            var injected = false

            classReader.accept(object : ClassVisitor(Opcodes.ASM9, classWriter) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?
                ): MethodVisitor {
                    val methodVisitor =
                        super.visitMethod(access, name, descriptor, signature, exceptions)

                    // 如果是目标方法，注入代码
                    if (name == methodName) {
                        injected = true
                        return object :
                            AdviceAdapter(Opcodes.ASM9, methodVisitor, access, name, descriptor) {
                            override fun onMethodEnter() {
                                // 对于非静态初始化块，在方法开始时注入代码
                                if (methodName != "<clinit>" && ((access and Opcodes.ACC_STATIC) != 0)) {
                                    injectStaticRegisterCode(
                                        registerClasses,
                                        registerMethodName,
                                        className
                                    )
                                }
                                super.onMethodEnter()
                            }

                            override fun onMethodExit(opcode: Int) {
                                // 对于<clinit>静态初始化块，在方法结束前注入代码，确保先初始化变量
                                if (methodName == "<clinit>") {
                                    injectStaticRegisterCode(
                                        registerClasses,
                                        registerMethodName,
                                        className
                                    )
                                } else if ((access and Opcodes.ACC_STATIC) == 0) {
                                    // 非静态方法的注入依然在方法结束前执行
                                    injectInstanceRegisterCode(
                                        registerClasses,
                                        registerMethodName,
                                        className
                                    )
                                }
                                super.onMethodExit(opcode)
                            }
                        }
                    }
                    return methodVisitor
                }

                // 如果需要的方法不存在，创建它
                override fun visitEnd() {
                    if (!injected && methodName == "<clinit>") {
                        val mv = visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
                        mv.visitCode()

                        // 在新创建的静态块中，先确保执行所有初始化逻辑
                        // 这里假设没有原始的<clinit>方法，所以先执行类中定义的初始化逻辑

                        // 然后执行注册代码
                        for (registerClass in registerClasses) {
                            // 创建实例: new RegisterClass()
                            mv.visitTypeInsn(Opcodes.NEW, registerClass)
                            mv.visitInsn(Opcodes.DUP)
                            mv.visitMethodInsn(
                                Opcodes.INVOKESPECIAL,
                                registerClass,
                                "<init>",
                                "()V",
                                false
                            )

                            // 调用注册方法: TargetClass.registerMethod(instance)
                            mv.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                className,
                                registerMethodName,
                                "(L${registerClass.replace('.', '/')};)V",
                                false
                            )
                        }
                        mv.visitInsn(Opcodes.RETURN)
                        mv.visitMaxs(0, 0)
                        mv.visitEnd()
                    }
                    super.visitEnd()
                }
            }, 0)

            // 将修改后的字节码写回到文件
            val modifiedBytes = classWriter.toByteArray()
            FileOutputStream(classFile).use { fos ->
                fos.write(modifiedBytes)
            }

            Logger.d("已为 $className 注入注册代码，注册了 ${registerClasses.size} 个类")
        } catch (e: Exception) {
            Logger.d("注入代码到 $className 失败")
        }
    }

    private fun MethodVisitor.injectStaticRegisterCode(
        registerClasses: Set<String>,
        registerMethodName: String,
        targetClassName: String
    ) {
        for (registerClass in registerClasses) {
            // 创建实例: new RegisterClass()
            visitTypeInsn(Opcodes.NEW, registerClass)
            visitInsn(Opcodes.DUP)
            visitMethodInsn(Opcodes.INVOKESPECIAL, registerClass, "<init>", "()V", false)

            // 调用注册方法: TargetClass.registerMethod(instance)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                targetClassName,
                registerMethodName,
                "(L${registerClass.replace('.', '/')};)V",
                false
            )
        }
    }

    private fun MethodVisitor.injectInstanceRegisterCode(
        registerClasses: Set<String>,
        registerMethodName: String,
        targetClassName: String
    ) {
        for (registerClass in registerClasses) {
            // this
            visitVarInsn(Opcodes.ALOAD, 0)

            // 创建实例: new RegisterClass()
            visitTypeInsn(Opcodes.NEW, registerClass)
            visitInsn(Opcodes.DUP)
            visitMethodInsn(Opcodes.INVOKESPECIAL, registerClass, "<init>", "()V", false)

            // 调用注册方法: this.registerMethod(instance)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                targetClassName,
                registerMethodName,
                "(L${registerClass.replace('.', '/')};)V",
                false
            )
        }
    }
}