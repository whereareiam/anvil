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
    val protocolId: Property<String> = objects.property(String::class.java)
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
    val keepFailedWorkspaces: Property<Boolean> = objects.property(Boolean::class.java)
    val offline: Property<Boolean> = objects.property(Boolean::class.java)
    val refresh: Property<Boolean> = objects.property(Boolean::class.java)
    val downloadJava: Property<Boolean> = objects.property(Boolean::class.java)
    val consoleColors: Property<Boolean> = objects.property(Boolean::class.java)
    val startupTimeout: Property<Duration> = objects.property(Duration::class.java)
    val stopTimeout: Property<Duration> = objects.property(Duration::class.java)
    val properties: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)

    /** Selects a protocol provider fallback using the concise Gradle DSL form. */
    fun protocol(id: String) {
        require(id.isNotBlank()) { "Anvil protocol ID must not be blank" }
        protocolId.set(id)
    }
}
