package me.whereareiam.anvil.integration.gradle

import me.whereareiam.anvil.integration.gradle.artifact.ArtifactRegistry
import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet

/**
 * Public Gradle configuration for Anvil engine defaults, artifacts, and execution inputs.
 *
 * Execution adapters read the source set, framework coordinates, EULA acceptance, and artifact
 * registrations from here; consumers configure the engine, the EULA, and named artifacts.
 */
open class AnvilExtension(
    objects: ObjectFactory,
    /** Engine defaults shared by foreground execution, JUnit, and editor preparation. */
    val engine: AnvilEngineExtension,
    /** Source set that holds Anvil scenarios, conventionally `src/anvil`. */
    val sourceSet: SourceSet,
    /** Framework version that aligns every Anvil module coordinate. */
    val frameworkVersion: String,
    private val artifacts: ArtifactRegistry,
) {
    private val eula: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    /** Whether `acceptEula()` was called. */
    val eulaAccepted: Provider<Boolean> = eula

    /** Configures launch defaults shared by foreground execution, JUnit, and editor preparation. */
    fun engine(action: Action<in AnvilEngineExtension>) {
        action.execute(engine)
    }

    /** Records explicit acceptance of Mojang's EULA for generated server runs. */
    fun acceptEula() {
        eula.set(true)
    }

    /** Registers one named artifact for use by Anvil scenario processes. */
    fun artifact(name: String, notation: Any) {
        artifacts.register(name, notation)
    }

    /** Connects an execution integration to registered artifact file inputs. */
    fun onArtifact(listener: (String, FileCollection) -> Unit) {
        artifacts.onRegistered { name, files -> listener(name, files) }
    }

    /** Returns the coordinate of one Anvil module at the framework version. */
    fun module(artifact: String): String = "me.whereareiam.anvil:$artifact:$frameworkVersion"
}
