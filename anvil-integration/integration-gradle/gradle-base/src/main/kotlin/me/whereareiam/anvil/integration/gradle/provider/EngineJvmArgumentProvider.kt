package me.whereareiam.anvil.integration.gradle.provider

import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider

/**
 * Supplies tracked engine properties to a forked test JVM when its task executes.
 */
abstract class EngineJvmArgumentProvider : CommandLineArgumentProvider {
    /**
     * Resolved engine inputs without unrelated JVM properties or credentials.
     */
    @get:Input
    abstract val properties: MapProperty<String, String>

    override fun asArguments(): Iterable<String> =
        properties.get().map { (name, value) -> "-D$name=$value" }
}
