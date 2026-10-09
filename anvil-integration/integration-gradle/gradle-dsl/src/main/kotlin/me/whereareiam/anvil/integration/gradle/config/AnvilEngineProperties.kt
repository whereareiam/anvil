package me.whereareiam.anvil.integration.gradle.config

import me.whereareiam.anvil.integration.gradle.AnvilExtension
import me.whereareiam.anvil.launcher.config.EngineProperties
import org.gradle.api.Project
import org.gradle.api.provider.Provider

/** Maps supported Gradle DSL values into the isolated Anvil engine JVM. */
object AnvilEngineProperties {
    /** Creates tracked engine inputs shared by JUnit, foreground runs, and editor preparation. */
    fun create(project: Project, extension: AnvilExtension): Provider<Map<String, String>> {
        val properties = project.objects.mapProperty(String::class.java, String::class.java)
        supportedProperties.forEach { name ->
            properties.putAll(project.providers.systemProperty(name)
                .map { value -> mapOf(name to value) }
                .orElse(emptyMap()))
        }
        properties.putAll(project.providers.systemPropertiesPrefixedBy(EngineProperties.ARTIFACT_PROPERTY_PREFIX))
        val engine = extension.engine
        properties.put(EngineProperties.EULA_ACCEPTED_PROPERTY, extension.eulaAccepted.map(Boolean::toString))
        properties.putAll(engine.properties.map { values ->
            values.filterKeys { key -> !key.startsWith("anvil.auth.") }
        })
        properties.put(EngineProperties.CACHE_DIRECTORY_PROPERTY, engine.cacheDirectory.map { it.asFile.absolutePath })
        properties.put(EngineProperties.ACCOUNTS_DIRECTORY_PROPERTY, engine.accountsDirectory.map { it.asFile.absolutePath })
        properties.put(EngineProperties.WORK_DIRECTORY_PROPERTY, engine.workDirectory.map { it.asFile.absolutePath })
        properties.putAll(engine.executionProviderId.map { mapOf(EngineProperties.EXECUTION_PROPERTY to it) }.orElse(emptyMap()))
        properties.putAll(engine.protocolId.map { mapOf(EngineProperties.PROTOCOL_PROPERTY to it) }.orElse(emptyMap()))
        properties.putAll(engine.parallelism.map { mapOf(EngineProperties.PARALLELISM_PROPERTY to it.toString()) }.orElse(emptyMap()))
        properties.putAll(engine.startupMemoryMegabytes.map { mapOf(EngineProperties.STARTUP_MEMORY_PROPERTY to it.toString()) }.orElse(emptyMap()))
        properties.putAll(engine.downloadParallelism.map { mapOf(EngineProperties.DOWNLOAD_PARALLELISM_PROPERTY to it.toString()) }.orElse(emptyMap()))
        properties.putAll(engine.keepFailedWorkspaces.map { mapOf(EngineProperties.KEEP_FAILED_WORKSPACES_PROPERTY to it.toString()) }.orElse(emptyMap()))
        properties.putAll(engine.offline.map { mapOf(EngineProperties.OFFLINE_PROPERTY to it.toString()) }.orElse(emptyMap()))
        properties.putAll(engine.refresh.map { mapOf(EngineProperties.REFRESH_PROPERTY to it.toString()) }.orElse(emptyMap()))
        properties.putAll(engine.downloadJava.map { mapOf(EngineProperties.AUTO_DOWNLOAD_JAVA_PROPERTY to it.toString()) }.orElse(emptyMap()))
        properties.putAll(engine.startupTimeout.map { mapOf(EngineProperties.STARTUP_TIMEOUT_PROPERTY to it.toString()) }.orElse(emptyMap()))
        properties.putAll(engine.stopTimeout.map { mapOf(EngineProperties.STOP_TIMEOUT_PROPERTY to it.toString()) }.orElse(emptyMap()))
        properties.putAll(engine.consoleColors.map { mapOf(EngineProperties.CONSOLE_COLORS_PROPERTY to it.toString()) }.orElse(emptyMap()))
        return properties
    }

    private val supportedProperties = listOf(
        EngineProperties.EXECUTION_PROPERTY,
        EngineProperties.PROTOCOL_PROPERTY,
        EngineProperties.JAVA_VERSION_PROPERTY,
        EngineProperties.JAVA_DISTRIBUTION_PROPERTY,
        EngineProperties.JAVA_RELEASE_PROPERTY,
        EngineProperties.JAVA_HOME_PROPERTY,
        EngineProperties.JAVA_EXECUTABLE_PROPERTY,
        EngineProperties.JAVA_ARCHIVE_URI_PROPERTY,
        EngineProperties.JAVA_ARCHIVE_SHA256_PROPERTY,
        EngineProperties.AUTO_DOWNLOAD_JAVA_PROPERTY,
        EngineProperties.CACHE_DIRECTORY_PROPERTY,
        EngineProperties.ACCOUNTS_DIRECTORY_PROPERTY,
        EngineProperties.WORK_DIRECTORY_PROPERTY,
        EngineProperties.KEEP_FAILED_WORKSPACES_PROPERTY,
        EngineProperties.OFFLINE_PROPERTY,
        EngineProperties.REFRESH_PROPERTY,
        EngineProperties.DOWNLOAD_PARALLELISM_PROPERTY,
        EngineProperties.EULA_ACCEPTED_PROPERTY,
        EngineProperties.PARALLELISM_PROPERTY,
        EngineProperties.STARTUP_MEMORY_PROPERTY,
        EngineProperties.STARTUP_TIMEOUT_PROPERTY,
        EngineProperties.STOP_TIMEOUT_PROPERTY,
        EngineProperties.CONSOLE_COLORS_PROPERTY,
    )
}
