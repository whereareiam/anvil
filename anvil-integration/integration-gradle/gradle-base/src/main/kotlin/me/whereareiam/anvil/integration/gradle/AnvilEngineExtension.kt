package me.whereareiam.anvil.integration.gradle

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import java.io.File
import java.time.Duration

/** Global engine defaults used by Anvil Gradle execution integrations. */
open class AnvilEngineExtension(
    objects: ObjectFactory,
    project: Project,
) {
    val executionProviderId: Property<String> = objects.property(String::class.java)
    val protocolLibrary: Property<String> = objects.property(String::class.java)
    val supportPolicy: Property<String> = objects.property(String::class.java)
    val protocolReleases: MapProperty<String, File> = objects.mapProperty(String::class.java, File::class.java)
    val cacheDirectory: DirectoryProperty = objects.directoryProperty().convention(
        project.layout.dir(project.providers.systemProperty("user.home").map { File(it, ".anvil") })
    )
    val accountsDirectory: DirectoryProperty = objects.directoryProperty().convention(
        project.layout.dir(project.providers.systemProperty("user.home").map { File(it, ".anvil/accounts") })
    )
    val workDirectory: DirectoryProperty = objects.directoryProperty().convention(project.layout.buildDirectory.dir("anvil"))
    val parallelism: Property<Int> = objects.property(Int::class.java)
    val startupMemoryMegabytes: Property<Int> = objects.property(Int::class.java)
    val downloadParallelism: Property<Int> = objects.property(Int::class.java)
    val processors: Property<Int> = objects.property(Int::class.java)
    val processPriority: Property<String> = objects.property(String::class.java)
    val keepFailedWorkspaces: Property<Boolean> = objects.property(Boolean::class.java)
    val offline: Property<Boolean> = objects.property(Boolean::class.java)
    val refresh: Property<Boolean> = objects.property(Boolean::class.java)
    val downloadJava: Property<Boolean> = objects.property(Boolean::class.java)
    val consoleColors: Property<Boolean> = objects.property(Boolean::class.java)
    val startupTimeout: Property<Duration> = objects.property(Duration::class.java)
    val stopTimeout: Property<Duration> = objects.property(Duration::class.java)
    val properties: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)

    /** Selects the default protocol library; scenarios and players may still choose another one. */
    fun protocolLibrary(id: String) {
        require(id.isNotBlank()) { "Anvil protocol library must not be blank" }
        protocolLibrary.set(id)
    }

    /** Selects the default support policy: `lenient` runs untested versions with a warning, `strict` refuses them. */
    fun supportPolicy(policy: String) {
        require(policy.lowercase() in setOf("lenient", "strict")) { "Anvil support policy must be lenient or strict" }
        supportPolicy.set(policy.lowercase())
    }

    /** Adds release data for a protocol library, for example a Minecraft version newer than this Anvil release. */
    fun protocolReleases(library: String, file: File) {
        require(library.isNotBlank()) { "Anvil protocol library must not be blank" }
        protocolReleases.put(library, file)
    }
}
