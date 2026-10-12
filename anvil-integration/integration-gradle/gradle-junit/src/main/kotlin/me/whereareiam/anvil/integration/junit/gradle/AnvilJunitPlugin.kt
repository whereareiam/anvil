package me.whereareiam.anvil.integration.junit.gradle

import me.whereareiam.anvil.integration.gradle.AnvilBasePlugin
import me.whereareiam.anvil.integration.gradle.AnvilExtension
import me.whereareiam.anvil.integration.gradle.artifact.provider.ArtifactJvmArgumentProvider
import me.whereareiam.anvil.integration.gradle.config.AnvilEngineProperties
import me.whereareiam.anvil.integration.gradle.provider.EngineJvmArgumentProvider
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

/**
 * Installs automated JUnit scenario execution without foreground scenario tooling.
 *
 * Registers `anvilTest`, adds `anvil { suites { ... } }` for further live-test tasks, wires every
 * [AnvilTest] task to the `anvil` source set, the engine settings, and the registered artifacts, and
 * registers the `anvil` task that runs all of them.
 */
class AnvilJunitPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(AnvilBasePlugin::class.java)
        val extension = project.extensions.getByType(AnvilExtension::class.java)

        project.dependencies.add(extension.sourceSet.implementationConfigurationName, extension.module("junit"))
        val suites = createSuites(project, extension)
        wireAnvilTests(project, extension)
        wireArtifacts(project, extension)
        registerAnvilTestTask(project, suites)
        registerSuiteTasks(project, suites)
        registerAggregateTask(project)
    }

    private fun createSuites(project: Project, extension: AnvilExtension): NamedDomainObjectContainer<AnvilSuite> {
        val suites = project.objects.domainObjectContainer(AnvilSuite::class.java)
        (extension as ExtensionAware).extensions.add(SUITES_TYPE, SUITES_EXTENSION, suites)
        return suites
    }

    private fun wireAnvilTests(project: Project, extension: AnvilExtension) {
        val anvil = extension.sourceSet
        val engineArguments = project.objects.newInstance(EngineJvmArgumentProvider::class.java)
        engineArguments.properties.set(AnvilEngineProperties.create(project, extension))

        project.tasks.withType(AnvilTest::class.java).configureEach {
            group = TASK_GROUP
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

    private fun wireArtifacts(project: Project, extension: AnvilExtension) {
        extension.onArtifact { name, files ->
            val argument = project.objects.newInstance(ArtifactJvmArgumentProvider::class.java)
            argument.artifactName.set(name)
            argument.artifactFiles.from(files)

            project.tasks.withType(AnvilTest::class.java).configureEach {
                jvmArgumentProviders.add(argument)
                dependsOn(files)
            }
        }
    }

    private fun registerAnvilTestTask(project: Project, suites: NamedDomainObjectContainer<AnvilSuite>) {
        project.tasks.register(ANVIL_TEST_TASK, AnvilTest::class.java) {
            description = "Runs automated Minecraft scenarios from the anvil source set."
            val claimedTags = suites.flatMap { it.includedTags.get() }.distinct()
            useJUnitPlatform {
                excludeTags(*claimedTags.toTypedArray())
            }
        }
    }

    private fun registerSuiteTasks(project: Project, suites: NamedDomainObjectContainer<AnvilSuite>) {
        suites.all {
            val suite = this
            project.tasks.register(suite.taskName, AnvilTest::class.java) {
                description = "Runs the automated Minecraft scenarios of the ${suite.name} suite."
                val tags = suite.includedTags.get()
                check(tags.isNotEmpty()) { "Anvil suite '${suite.name}' must include at least one JUnit tag" }
                useJUnitPlatform {
                    includeTags(*tags.toTypedArray())
                }
            }
        }
    }

    private fun registerAggregateTask(project: Project) {
        project.tasks.register(AGGREGATE_TASK) {
            group = TASK_GROUP
            description = "Runs anvilTest and the task of every Anvil suite."
            dependsOn(project.tasks.withType(AnvilTest::class.java))
        }
    }

    private companion object {
        const val AGGREGATE_TASK = "anvil"
        const val ANVIL_TEST_TASK = "anvilTest"
        const val SUITES_EXTENSION = "suites"
        const val TASK_GROUP = "anvil"
        val SUITES_TYPE = object : TypeOf<NamedDomainObjectContainer<AnvilSuite>>() {}
    }
}
