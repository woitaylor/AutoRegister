package com.billy.android.register

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register

class AutoRegisterPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("autoregister", AutoRegisterExtension::class.java)

        // 处理应用模块 - 只有 app 模块执行注册任务
        project.plugins.withType(AppPlugin::class.java) {
            val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            
            // 在配置阶段收集依赖模块的配置
            val allRegisterInfoProvider = project.provider {
                val mergedList = extension.registerInfo.get().toMutableList()
                // 获取所有依赖的项目模块
                project.configurations["implementation"].dependencies
                    .filterIsInstance<ProjectDependency>()
                    .forEach { dependency ->
                        val dependencyProject = dependency.dependencyProject
                        // 获取依赖模块的 AutoRegisterExtension
                        dependencyProject.extensions.findByType(AutoRegisterExtension::class.java)?.let { libExtension ->
                            Logger.i("Collecting AutoRegister config from ${dependencyProject.name}")
                            mergedList.addAll(libExtension.registerInfo.get())
                        }
                    }
                mergedList as List<RegisterInfo>
            }
            // 只在 application 模块中配置变体和注册任务
            configureVariants(project, allRegisterInfoProvider, androidComponents)
        }

        // 处理库模块 - 只创建和配置扩展，不执行任务
        project.plugins.withType(LibraryPlugin::class.java) {
            // 不做任何事情，只保留扩展配置供 app 模块收集
            // 不需要为库模块配置变体和注册任务
        }
    }

    private fun configureVariants(
        project: Project,
        registerInfoProvider: Provider<List<RegisterInfo>>,
        androidComponents: AndroidComponentsExtension<*, *, out Variant>
    ) {
        androidComponents.onVariants { variant ->
            val buildDirProvider = project.layout.buildDirectory.dir(
                project.provider { "intermediates/auto_register/${variant.name}" }
            )

            val taskProvider = with(project.tasks) {
                register<AutoRegisterTask>("${variant.name}AutoRegisterTask") {
                    registerInfo.set(registerInfoProvider)
                    appBuilderDir.set(buildDirProvider)
                }
            }

            variant.artifacts.forScope(ScopedArtifacts.Scope.ALL)
                .use(taskProvider)
                .toTransform(
                    ScopedArtifact.CLASSES,
                    AutoRegisterTask::allJars,
                    AutoRegisterTask::allDirectories,
                    AutoRegisterTask::output
                )
        }
    }
}