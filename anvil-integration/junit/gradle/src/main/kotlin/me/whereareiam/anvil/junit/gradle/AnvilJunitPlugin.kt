package me.whereareiam.anvil.junit.gradle

import me.whereareiam.anvil.launcher.config.EngineProperties
import me.whereareiam.anvil.gradle.AnvilBasePlugin
import me.whereareiam.anvil.gradle.model.AnvilPluginState
import me.whereareiam.anvil.gradle.model.state
import me.whereareiam.anvil.gradle.task.ArtifactJvmArgumentProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

/**
 * Installs automated JUnit scenario execution without foreground scenario tooling.
 */
class AnvilJunitPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(AnvilBasePlugin::class.java)
        val state = state(project)

        addJunitDependency(project, state)
        val anvilTestTask = registerAnvilTestTask(project, state)
        wireArtifacts(project, state, anvilTestTask)
        wireFullTesting(project, anvilTestTask)
    }

    private fun addJunitDependency(project: Project, state: AnvilPluginState) {
        project.dependencies.add(state.framework.name, state.coordinate("junit"))
    }

    private fun registerAnvilTestTask(
        project: Project,
        state: AnvilPluginState,
    ): TaskProvider<Test> {
        val capability = state.capability
        val anvil = state.sourceSet

        return project.tasks.register(ANVIL_TEST_TASK, Test::class.java) {
            group = "verification"
            description = "Runs automated Minecraft scenarios from the anvil source set."
            testClassesDirs = anvil.output.classesDirs
            classpath = anvil.runtimeClasspath
            useJUnitPlatform()
            testLogging {
                events("failed", "skipped")
                exceptionFormat = TestExceptionFormat.FULL
            }

            systemProperty(EngineProperties.EULA_ACCEPTED_PROPERTY, capability.eulaAccepted.get())
            systemProperty(EngineProperties.CACHE_DIRECTORY_PROPERTY, capability.cacheDirectory.get().asFile.absolutePath)
            systemProperty(EngineProperties.WORK_DIRECTORY_PROPERTY, capability.workDirectory.get().asFile.absolutePath)
            capability.parallelism.orNull?.let { systemProperty(EngineProperties.PARALLELISM_PROPERTY, it) }
            capability.startupMemoryMegabytes.orNull?.let { systemProperty(EngineProperties.STARTUP_MEMORY_PROPERTY, it) }
            capability.downloadParallelism.orNull?.let { systemProperty(EngineProperties.DOWNLOAD_PARALLELISM_PROPERTY, it) }

            capability.protocolId.orNull?.let { systemProperty(EngineProperties.PROTOCOL_PROPERTY, it) }
        }
    }

    private fun wireArtifacts(
        project: Project,
        state: AnvilPluginState,
        anvilTestTask: TaskProvider<Test>,
    ) {
        state.onArtifact { name, files ->
            val argument = project.objects.newInstance(ArtifactJvmArgumentProvider::class.java)
            argument.artifactName.set(name)
            argument.artifactFiles.from(files)

            anvilTestTask.configure {
                jvmArgumentProviders.add(argument)
                dependsOn(files)
            }
        }
    }

    private fun wireFullTesting(project: Project, anvilTestTask: TaskProvider<Test>) {
        val fullTesting = project.providers.gradleProperty(TEST_MODE_PROPERTY)
            .map { it.equals("full", ignoreCase = true) }
            .orElse(false)

        project.tasks.named("test") {
            if (fullTesting.get()) dependsOn(anvilTestTask)
        }
    }

    private companion object {
        const val ANVIL_TEST_TASK = "anvilTest"
        const val TEST_MODE_PROPERTY = "anvil.testMode"
    }
}
