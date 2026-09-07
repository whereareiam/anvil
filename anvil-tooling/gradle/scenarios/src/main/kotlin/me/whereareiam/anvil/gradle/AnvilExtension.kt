package me.whereareiam.anvil.gradle

import me.whereareiam.anvil.gradle.internal.AnvilPluginNames
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Gradle configuration for Anvil tests, artifacts, authentication, and manual scenarios.
 */
open class AnvilExtension @Inject constructor(
    objects: ObjectFactory,
    private val project: Project,
    private val frameworkVersion: String,
) {
    private val artifacts = linkedMapOf<String, FileCollection>()
    private val artifactListeners = mutableListOf<(String, FileCollection) -> Unit>()

    /** Provider class names used by scenario listing and manual runner tasks. */
    val scenarioProviders: ListProperty<String> =
        objects.listProperty(String::class.java).convention(emptyList())

    /** Shared immutable artifact and protocol cache. */
    val cacheDirectory: DirectoryProperty = objects.directoryProperty()

    /** Generated, disposable scenario workspace root. */
    val workDirectory: DirectoryProperty = objects.directoryProperty()

    /** Selected protocol-provider identifier used by Anvil scenarios. */
    val protocolId: Property<String> = objects.property(String::class.java)

    /** Selects the protocol provider used by each running scenario. */
    fun protocol(id: String) {
        require(id.isNotBlank()) { "Anvil protocol ID must not be blank" }
        protocolId.set(id)
    }

    /** Maximum concurrent preparation and process starts. */
    val parallelism: Property<Int> = objects.property(Int::class.java)

    /** Combined heap budget for processes starting concurrently. */
    val startupMemoryMegabytes: Property<Int> = objects.property(Int::class.java)

    /** Maximum concurrent artifact downloads. */
    val downloadParallelism: Property<Int> = objects.property(Int::class.java)

    /** Mojang EULA acknowledgement required before server launch. */
    val eulaAccepted: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    /** Type-safe coordinates for bundled protocol providers. */
    val protocols: ProtocolDependencies = ProtocolDependencies(frameworkVersion)

    /** Records explicit acceptance of Mojang's EULA for generated server runs. */
    fun acceptEula() {
        eulaAccepted.set(true)
    }

    /**
     * Registers one named artifact for use by `Distribution.artifact(name)` or a workspace asset's
     * `AssetSource.artifact(name)`. Maven coordinates and project dependencies are resolved by
     * Gradle; task providers and paths are accepted as file notations.
     */
    fun artifact(name: String, notation: Any) {
        require(name.matches(Regex(AnvilPluginNames.ARTIFACT_NAME_PATTERN))) {
            "Anvil artifact names may contain only letters, digits, '.', '_' and '-'"
        }
        require(name !in artifacts) { "Duplicate Anvil artifact: $name" }

        val files = when (notation) {
            is Project -> project.configurations.detachedConfiguration(
                project.dependencies.project(mapOf("path" to notation.path))
            )

            is Dependency -> project.configurations.detachedConfiguration(notation)
            is CharSequence -> {
                val value = notation.toString()
                if (value.count { character -> character == ':' } >= 2)
                    project.configurations.detachedConfiguration(project.dependencies.create(value))
                else
                    project.files(value)
            }

            else -> project.files(notation)
        }
        artifacts[name] = files
        artifactListeners.forEach { listener -> listener(name, files) }
    }

    internal fun onArtifact(listener: (String, FileCollection) -> Unit) {
        artifactListeners += listener
        artifacts.forEach(listener)
    }

}
