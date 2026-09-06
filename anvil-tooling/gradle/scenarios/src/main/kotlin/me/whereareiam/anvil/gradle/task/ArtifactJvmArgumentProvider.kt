package me.whereareiam.anvil.gradle.task

import me.whereareiam.anvil.launcher.config.EngineProperties
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider

/** Supplies a resolved artifact path to the isolated JUnit worker without resolving it during configuration. */
abstract class ArtifactJvmArgumentProvider : CommandLineArgumentProvider {
    /** Stable artifact reference used by scenario models. */
    @get:Input
    abstract val artifactName: Property<String>

    /** Exactly one resolved local artifact. */
    @get:Classpath
    abstract val artifactFiles: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> {
        val files = artifactFiles.files
        require(files.size == 1) {
            "Anvil artifact '${artifactName.get()}' must resolve to exactly one file, found ${files.size}: $files"
        }
        return listOf("-D${EngineProperties.artifactProperty(artifactName.get())}=${files.single().absolutePath}")
    }
}
