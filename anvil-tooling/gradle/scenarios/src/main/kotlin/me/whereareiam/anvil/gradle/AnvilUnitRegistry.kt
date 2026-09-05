package me.whereareiam.anvil.gradle

import me.whereareiam.anvil.gradle.internal.AnvilCoordinates
import me.whereareiam.anvil.gradle.type.AnvilDependencyBucket
import org.gradle.api.Project
import javax.inject.Inject

/** Registration surface used by Anvil platform and capability unit plugins. */
open class AnvilUnitRegistry @Inject constructor(
    private val project: Project,
    private val frameworkVersion: String,
) {
    private val selectedPlatforms = linkedSetOf<String>()

    /** Includes a capability wiring artifact in the Anvil runtime. */
    fun capability(notation: Any) {
        project.dependencies.add(
            AnvilDependencyBucket.CAPABILITIES.configurationName,
            notation,
        )
    }

    /** Includes an Anvil capability artifact aligned to the applied framework version. */
    fun capabilityArtifact(artifact: String) {
        capability(AnvilCoordinates.module(artifact, frameworkVersion))
    }

    /**
     * Registers a platform and includes the provider and agent artifacts owned by that platform.
     *
     * @param id stable platform identifier
     * @param artifacts platform provider and agent artifact names
     */
    fun platform(id: String, vararg artifacts: String) {
        require(id.isNotBlank()) { "Anvil platform ID must not be blank" }
        require(artifacts.isNotEmpty()) { "Anvil platform '$id' must declare at least one artifact" }
        if (!selectedPlatforms.add(id)) return

        artifacts.forEach { artifact ->
            require(artifact.isNotBlank()) { "Anvil platform artifact must not be blank" }
            project.dependencies.add(
                AnvilDependencyBucket.PLATFORMS.configurationName,
                AnvilCoordinates.module(artifact, frameworkVersion),
            )
        }
    }

}
