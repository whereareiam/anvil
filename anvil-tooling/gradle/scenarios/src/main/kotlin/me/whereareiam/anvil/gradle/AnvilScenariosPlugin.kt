package me.whereareiam.anvil.gradle

import me.whereareiam.anvil.gradle.internal.AnvilPluginNames
import me.whereareiam.anvil.gradle.model.state
import me.whereareiam.anvil.gradle.task.ScenarioRunnerTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Installs foreground scenario listing and execution without JUnit.
 */
class AnvilScenariosPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(AnvilBasePlugin::class.java)
        val state = state(project)
        val capability = state.capability
        val anvil = state.sourceSet

        val scenarioRunnerTask = project.tasks.register(
            AnvilPluginNames.SCENARIO_TASK,
            ScenarioRunnerTask::class.java,
        ) {
            group = AnvilPluginNames.TASK_GROUP
            description = "Lists registered scenarios and groups, or starts one foreground scenario."
            providers.set(capability.scenarioProviders)
            eulaAccepted.set(capability.eulaAccepted)
            cacheDirectory.set(capability.cacheDirectory)
            workDirectory.set(capability.workDirectory)
            javaExecutables.set(capability.javaExecutables)
            protocolId.set(capability.protocolId)
            runtimeClasspath.from(anvil.runtimeClasspath)
            dependsOn(project.tasks.named(anvil.classesTaskName))
        }

        capability.onArtifact { name, files ->
            scenarioRunnerTask.configure {
                artifactFiles.from(files)
                artifactPaths.put(name, files.elements.map { elements ->
                    require(elements.size == 1) {
                        "Anvil artifact '$name' must resolve to exactly one file, found ${elements.size}"
                    }
                    elements.single().asFile.absolutePath
                })
                dependsOn(files)
            }
        }
    }
}
