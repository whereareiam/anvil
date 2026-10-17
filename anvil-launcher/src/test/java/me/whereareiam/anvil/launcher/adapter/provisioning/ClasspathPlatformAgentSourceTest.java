package me.whereareiam.anvil.launcher.adapter.provisioning;

import me.whereareiam.anvil.capability.AgentExtensions;
import me.whereareiam.anvil.capability.agent.api.model.AgentExtension;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClasspathPlatformAgentSourceTest {
	private static final Map<String, Path> JARS = Map.of(
			"example.Agent", Path.of("agent.jar"),
			"example.bukkit.Operations", Path.of("extension-bukkit.jar"),
			"example.bukkit.Listeners", Path.of("extension-bukkit.jar"),
			"example.velocity.Operations", Path.of("extension-velocity.jar")
	);

	@Test
	void locatesTheAgentAndOnceEachJarOfThePlatformsExtensions() {
		ClasspathPlatformAgentSource source = new ClasspathPlatformAgentSource(JARS::get, AgentExtensions.of(List.of(
				AgentExtension.builder().platform("paper").platform("spigot").entrypoint("example.bukkit.Operations").build(),
				AgentExtension.builder().platform("paper").entrypoint("example.bukkit.Listeners").build(),
				AgentExtension.builder().platform("velocity").entrypoint("example.velocity.Operations").build()
		)));

		assertEquals(Path.of("agent.jar"), source.locate(PlatformAgentDescriptor.builder().entrypointClassName("example.Agent").build()));
		assertEquals(List.of(Path.of("extension-bukkit.jar")), source.extensions("paper"));
		assertEquals(List.of(Path.of("extension-velocity.jar")), source.extensions("velocity"));
		assertEquals(List.of(), source.extensions("bungeecord"));
	}
}
