package com.billy.android.register

import org.gradle.api.provider.ListProperty
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject
import java.io.Serializable

abstract class AutoRegisterExtension @Inject constructor(objects: ObjectFactory) {
    val registerInfo: ListProperty<RegisterInfo> = objects.listProperty(RegisterInfo::class.java)
        .convention(mutableListOf())
}

open class RegisterInfo : Serializable {
    var scanInterface: String = ""
    var codeInsertToClassName: String = ""
    var codeInsertToMethodName: String = "<clinit>"
    var registerMethodName: String = ""
    var insertFilePath: String = ""
    var entryName: String = ""
    var scanSuperClasses: List<String> = mutableListOf()
    var include: List<String> = mutableListOf()
    var exclude: List<String> = mutableListOf()

    override fun toString(): String {
        return """
            RegisterInfo(
                scanInterface='$scanInterface',
                codeInsertToClassName='$codeInsertToClassName',
                codeInsertToMethodName='$codeInsertToMethodName',
                registerMethodName='$registerMethodName',
                scanSuperClasses=$scanSuperClasses,
                include=$include,
                exclude=$exclude,
                insertFilePath=$insertFilePath,
                entryName=$entryName
            )
        """.trimIndent()
    }
} 