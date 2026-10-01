package me.whereareiam.anvil.integration.gradle.platform

import me.whereareiam.anvil.integration.gradle.AnvilBasePlugin
import me.whereareiam.anvil.integration.gradle.AnvilExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

private fun Project.installPlatform(artifacts: Array<String>) {
	project.pluginManager.apply(AnvilBasePlugin::class.java)
	val anvil = project.extensions.getByType(AnvilExtension::class.java)
	artifacts.forEach { artifact ->
		require(artifact.isNotBlank()) { "Anvil platform artifact must not be blank" }
		project.dependencies.add(anvil.sourceSet.implementationConfigurationName, anvil.module(artifact))
	}
}

class PaperPlugin : Plugin<Project> {
	override fun apply(project: Project) = project.installPlatform(arrayOf("platform-paper-provider", "platform-bukkit-agent"))
}

class SpigotPlugin : Plugin<Project> {
	override fun apply(project: Project) = project.installPlatform(arrayOf("platform-spigot-provider", "platform-bukkit-agent"))
}

class VelocityPlugin : Plugin<Project> {
	override fun apply(project: Project) = project.installPlatform(arrayOf("platform-velocity-provider", "platform-velocity-agent"))
}

class BungeeCordPlugin : Plugin<Project> {
	override fun apply(project: Project) = project.installPlatform(arrayOf("platform-bungeecord-provider", "platform-bungeecord-agent"))
}
