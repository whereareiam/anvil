package me.whereareiam.anvil.gradle.unit

import me.whereareiam.anvil.gradle.AnvilScenariosPlugin
import me.whereareiam.anvil.gradle.AnvilUnitRegistry
import org.gradle.api.Plugin
import org.gradle.api.Project

internal fun Project.applyScenarioUnit() {
    pluginManager.apply(AnvilScenariosPlugin::class.java)
}

internal fun Project.anvilUnitRegistry(): AnvilUnitRegistry =
    extensions.getByType(AnvilUnitRegistry::class.java)

abstract class CapabilityUnitPlugin : Plugin<Project> {
    final override fun apply(project: Project) {
        project.applyScenarioUnit()
        val registry = project.anvilUnitRegistry()
        registry.capabilityArtifact(capability(registry))
    }

    protected abstract fun capability(registry: AnvilUnitRegistry): String
}

abstract class PlatformUnitPlugin : Plugin<Project> {
    final override fun apply(project: Project) {
        project.applyScenarioUnit()
        project.anvilUnitRegistry().platform(platformId, *artifacts)
    }

    protected abstract val platformId: String
    protected abstract val artifacts: Array<String>
}

class SessionPlugin : CapabilityUnitPlugin() {
    override fun capability(registry: AnvilUnitRegistry): String = "builtin-session"
}

class ServerPlugin : CapabilityUnitPlugin() {
    override fun capability(registry: AnvilUnitRegistry): String = "builtin-server"
}

class MessagesPlugin : CapabilityUnitPlugin() {
    override fun capability(registry: AnvilUnitRegistry): String = "builtin-messages"
}

class MovementPlugin : CapabilityUnitPlugin() {
    override fun capability(registry: AnvilUnitRegistry): String = "builtin-movement"
}

class InventoryPlugin : CapabilityUnitPlugin() {
    override fun capability(registry: AnvilUnitRegistry): String = "builtin-inventory"
}

class InteractionPlugin : CapabilityUnitPlugin() {
    override fun capability(registry: AnvilUnitRegistry): String = "builtin-interaction"
}

class DefaultPlugin : CapabilityUnitPlugin() {
    override fun capability(registry: AnvilUnitRegistry): String = "default"
}

class PaperPlugin : PlatformUnitPlugin() {
    override val platformId = "paper"
    override val artifacts = arrayOf("platform-paper-provider", "platform-bukkit-agent")
}

class SpigotPlugin : PlatformUnitPlugin() {
    override val platformId = "spigot"
    override val artifacts = arrayOf("platform-spigot-provider", "platform-bukkit-agent")
}

class VelocityPlugin : PlatformUnitPlugin() {
    override val platformId = "velocity"
    override val artifacts = arrayOf("platform-velocity-provider", "platform-velocity-agent")
}

class BungeeCordPlugin : PlatformUnitPlugin() {
    override val platformId = "bungeecord"
    override val artifacts = arrayOf("platform-bungeecord-provider", "platform-bungeecord-agent")
}
