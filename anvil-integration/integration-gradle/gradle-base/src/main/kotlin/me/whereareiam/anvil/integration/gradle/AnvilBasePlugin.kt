package me.whereareiam.anvil.integration.gradle

import me.whereareiam.anvil.integration.gradle.artifact.ArtifactRegistry
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import java.util.Properties

/**
 * Installs the `anvil` extension, its dependency buckets, and the dedicated `src/anvil` source set.
 */
class AnvilBasePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(JavaPlugin::class.java)

        val sourceSet = configureSourceSet(project)
        val anvil = project.extensions.create(
            GradleIdentifiers.EXTENSION_NAME,
            AnvilExtension::class.java,
            project.objects,
            AnvilEngineExtension(project.objects, project),
            sourceSet,
            resolveVersion(project),
            ArtifactRegistry(project),
        )
        project.dependencies.add(sourceSet.implementationConfigurationName, anvil.module("api"))
        project.dependencies.add(sourceSet.runtimeOnlyConfigurationName, anvil.module("launcher"))
    }

    private fun configureSourceSet(project: Project): SourceSet {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val main = sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME)
        val test = sourceSets.named(SourceSet.TEST_SOURCE_SET_NAME)
        val anvil = sourceSets.create(GradleIdentifiers.SOURCE_SET_NAME) {
            compileClasspath += main.get().output + test.get().compileClasspath
            // Test dependencies are shared through the configurations below; test classes stay out.
            runtimeClasspath += output + compileClasspath
        }
        project.configurations.named(anvil.implementationConfigurationName) {
            extendsFrom(project.configurations.getByName(test.get().implementationConfigurationName))
        }
        project.configurations.named(anvil.runtimeOnlyConfigurationName) {
            extendsFrom(project.configurations.getByName(test.get().runtimeOnlyConfigurationName))
        }
        return anvil
    }

    private fun resolveVersion(project: Project): String =
        AnvilBasePlugin::class.java.`package`.implementationVersion
            ?: project.providers.gradleProperty(GradleIdentifiers.VERSION_PROPERTY_NAME)
                .orElse(project.provider { packagedVersion() })
                .get()

    private fun packagedVersion(): String {
        val properties = Properties()
        val resource = requireNotNull(javaClass.getResourceAsStream("/META-INF/anvil/plugin.properties")) {
            "Anvil plugin version metadata is missing"
        }
        resource.use(properties::load)
        return requireNotNull(properties.getProperty("version")) { "Anvil plugin version is missing" }
    }
}
