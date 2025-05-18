// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

abstract class CheckAutoRegisterTask : DefaultTask() {
    @get:Input
    abstract val projectPaths: ListProperty<String>

    @get:Input
    abstract val pluginApplied: MapProperty<String, Boolean>

    @TaskAction
    fun run() {
        val maxPathLength = projectPaths.get().maxOfOrNull { it.length } ?: 0

        println("┌${"─".repeat(maxPathLength + 12)}┐")
        println("│ Checking 'auto-register' plugin in ${projectPaths.get().size} projects │")
        println("├${"─".repeat(maxPathLength + 12)}┤")

        projectPaths.get().sorted().forEach { path ->
            val status = if (pluginApplied.get()[path] == true) "✅ APPLIED" else "❌ MISSING"
            println("│ ${path.padEnd(maxPathLength)} : $status │")
        }

        println("└${"─".repeat(maxPathLength + 12)}┘")
    }
}

tasks.register<CheckAutoRegisterTask>("checkAutoRegisterPlugins") {
    group = "verification"
    description = "Check which projects apply the 'auto-register' plugin"

    // 使用Provider API收集数据
    val pluginCheckData = project.provider {
        allprojects.associate { it.path to it.pluginManager.hasPlugin("auto-register") }
    }

    projectPaths.set(pluginCheckData.map { it.keys.toList() })
    pluginApplied.set(pluginCheckData)
}