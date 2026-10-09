package me.whereareiam.anvil.integration.junit.gradle

import me.whereareiam.anvil.integration.gradle.AnvilBasePlugin
import me.whereareiam.anvil.integration.gradle.AnvilExtension
import me.whereareiam.anvil.integration.gradle.artifact.provider.ArtifactJvmArgumentProvider
import me.whereareiam.anvil.integration.gradle.config.AnvilEngineProperties
import me.whereareiam.anvil.integration.gradle.provider.EngineJvmArgumentProvider
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
        val extension = project.extensions.getByType(AnvilExtension::class.java)

        project.dependencies.add(extension.sourceSet.implementationConfigurationName, extension.module("junit"))
        val anvilTestTask = registerAnvilTestTask(project, extension)
        wireArtifacts(project, extension, anvilTestTask)
        wireFullTesting(project, anvilTestTask)
    }

    private fun registerAnvilTestTask(
        project: Project,
        extension: AnvilExtension,
    ): TaskProvider<Test> {
        val anvil = extension.sourceSet
        val engineArguments = project.objects.newInstance(EngineJvmArgumentProvider::class.java)
        engineArguments.properties.set(AnvilEngineProperties.create(project, extension))

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

            jvmArgumentProviders.add(engineArguments)
        }
    }

    private fun wireArtifacts(
        project: Project,
        extension: AnvilExtension,
        anvilTestTask: TaskProvider<Test>,
    ) {
        extension.onArtifact { name, files ->
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
