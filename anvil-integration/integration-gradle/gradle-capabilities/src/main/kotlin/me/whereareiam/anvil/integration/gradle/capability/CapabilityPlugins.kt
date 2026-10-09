package me.whereareiam.anvil.integration.gradle.capability

import me.whereareiam.anvil.integration.gradle.AnvilBasePlugin
import me.whereareiam.anvil.integration.gradle.AnvilExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

private fun Project.installCapability(artifact: String) {
	pluginManager.apply(AnvilBasePlugin::class.java)
	val anvil = extensions.getByType(AnvilExtension::class.java)
	dependencies.add(anvil.sourceSet.implementationConfigurationName, anvil.module(artifact))
}

abstract class CapabilityPlugin(private val artifact: String) : Plugin<Project> {
	final override fun apply(project: Project) = project.installCapability(artifact)
}

class SessionPlugin : CapabilityPlugin("builtin-session")
class ConsolePlugin : CapabilityPlugin("builtin-console")
class ServerPlugin : CapabilityPlugin("builtin-server")
class MessagesPlugin : CapabilityPlugin("builtin-messages")
class MovementPlugin : CapabilityPlugin("builtin-movement")
class InventoryPlugin : CapabilityPlugin("builtin-inventory")
class InteractionPlugin : CapabilityPlugin("builtin-interaction")
class DefaultPlugin : CapabilityPlugin("default")
