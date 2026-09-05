package me.whereareiam.anvil.gradle

import me.whereareiam.anvil.gradle.internal.AnvilCoordinates
import me.whereareiam.anvil.gradle.internal.AnvilPluginNames
import me.whereareiam.anvil.gradle.model.AnvilDependencyBuckets
import me.whereareiam.anvil.gradle.model.AnvilPluginState
import me.whereareiam.anvil.gradle.task.AuthenticationTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage

/**
 * Installs the shared Anvil source set, dependency buckets, extension, and authentication tasks.
 */
class AnvilBasePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(JavaPlugin::class.java)

        val frameworkVersion = AnvilBasePlugin::class.java.`package`.implementationVersion
            ?: project.providers.gradleProperty(AnvilPluginNames.VERSION_PROPERTY)
                .orElse(AnvilPluginNames.DEFAULT_VERSION)
                .get()
        val capability = project.extensions.create(
            AnvilPluginNames.EXTENSION,
            AnvilExtension::class.java,
            project,
            frameworkVersion,
        )
        capability.cacheDirectory.convention(
            project.layout.dir(project.provider {
                project.file(System.getProperty("user.home")).resolve(".anvil")
            })
        )
        capability.workDirectory.convention(project.layout.buildDirectory.dir("anvil"))

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val main = sourceSets.named("main")
        val test = sourceSets.named("test")
        val anvil = sourceSets.create(AnvilPluginNames.SOURCE_SET) {
            compileClasspath += main.get().output + test.get().compileClasspath
            runtimeClasspath += output + compileClasspath + test.get().runtimeClasspath
        }

        val buckets = AnvilDependencyBuckets.create(project)
        val protocolRuntime = project.configurations.create(AnvilPluginNames.PROTOCOL_RUNTIME_CONFIGURATION) {
            description = "Protocol providers and dependencies used by authentication tooling"
            isCanBeConsumed = false
            isCanBeResolved = true
            extendsFrom(buckets.protocols)
            attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.LIBRARY))
        }
        project.extensions.create(
            AnvilPluginNames.UNIT_REGISTRY_EXTENSION,
            AnvilUnitRegistry::class.java,
            project,
            frameworkVersion,
        )
        project.configurations.named(anvil.implementationConfigurationName) {
            extendsFrom(
                project.configurations.getByName(test.get().implementationConfigurationName),
                buckets.framework,
                buckets.capabilities,
                buckets.protocols,
                buckets.platforms,
            )
        }
        project.configurations.named(anvil.runtimeOnlyConfigurationName) {
            extendsFrom(
                project.configurations.getByName(test.get().runtimeOnlyConfigurationName),
                buckets.launcher,
            )
        }

        project.dependencies.add(buckets.framework.name, AnvilCoordinates.module("api", frameworkVersion))
        project.dependencies.add(buckets.launcher.name, AnvilCoordinates.module("launcher", frameworkVersion))
        project.extensions.add(
            AnvilPluginState::class.java,
            AnvilPluginNames.STATE_EXTENSION,
            AnvilPluginState(capability, anvil, buckets, frameworkVersion),
        )

        project.configurations.named(anvil.runtimeOnlyConfigurationName) {
            extendsFrom(buckets.platforms, buckets.protocols)
        }
        project.configurations.named(anvil.implementationConfigurationName) {
            extendsFrom(buckets.protocols)
        }

        project.tasks.register(AnvilPluginNames.LOGIN_TASK, AuthenticationTask::class.java) {
            group = AnvilPluginNames.TASK_GROUP
            description = "Authenticates an owner-local profile using the selected protocol provider."
            operation.set(AnvilPluginNames.LOGIN_OPERATION)
            cacheDirectory.set(capability.cacheDirectory)
            protocolId.set(capability.protocolId)
            runtimeClasspath.from(protocolRuntime)
        }
        project.tasks.register(AnvilPluginNames.LOGOUT_TASK, AuthenticationTask::class.java) {
            group = AnvilPluginNames.TASK_GROUP
            description = "Removes an owner-local profile using the selected protocol provider."
            operation.set(AnvilPluginNames.LOGOUT_OPERATION)
            cacheDirectory.set(capability.cacheDirectory)
            protocolId.set(capability.protocolId)
            runtimeClasspath.from(protocolRuntime)
        }
    }
}
