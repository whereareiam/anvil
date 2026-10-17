package me.whereareiam.anvil.capability;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.capability.agent.api.model.AgentExtension;
import me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentExtensionsTest {
	private static final AgentExtension BUKKIT = AgentExtension.builder()
			.platform("paper").platform("spigot").entrypoint("example.agent.bukkit.Operations").build();
	private static final AgentExtension VELOCITY = AgentExtension.builder()
			.platform("velocity").entrypoint("example.agent.velocity.Operations").build();
	private static final AgentExtension CONSOLE = AgentExtension.builder()
			.platform("paper").entrypoint("example.agent.console.Operations").build();

	@Test
	void collectsTheExtensionsOfPlayerAndProcessProvidersByPlatform(@TempDir Path directory) throws IOException {
		try (URLClassLoader loader = providers(directory)) {
			AgentExtensions extensions = AgentExtensions.discover(loader);

			assertEquals(Set.of(BUKKIT, CONSOLE), Set.copyOf(extensions.forPlatform("paper")));
			assertEquals(List.of(BUKKIT), extensions.forPlatform("spigot"));
			assertEquals(List.of(VELOCITY), extensions.forPlatform("velocity"));
			assertEquals(List.of(), extensions.forPlatform("neoforge"));
		}
	}

	private URLClassLoader providers(Path directory) throws IOException {
		Path services = Files.createDirectories(directory.resolve("META-INF/services"));
		String player = AgentPlayerCapabilityProvider.class.getName();
		String process = AgentProcessCapabilityProvider.class.getName();
		Files.writeString(services.resolve(player), PlayerFeatureProvider.class.getName());
		Files.writeString(services.resolve(process), ProcessFeatureProvider.class.getName());

		return new URLClassLoader(new URL[]{directory.toUri().toURL()}, getClass().getClassLoader()) {
			@Override
			public Enumeration<URL> getResources(String name) throws IOException {
				if (name.equals("META-INF/services/" + player) || name.equals("META-INF/services/" + process))
					return findResources(name);
				if (name.startsWith("META-INF/services/")) return Collections.emptyEnumeration();

				return super.getResources(name);
			}
		};
	}

	/**
	 * A player capability whose platform half runs on Bukkit and Velocity.
	 */
	public static final class PlayerFeatureProvider implements AgentPlayerCapabilityProvider<PlayerFeature> {
		@Override
		public @NotNull CapabilityDescriptor descriptor() {
			return CapabilityDescriptor.builder().id("example.player-feature").build();
		}

		@Override
		public @NotNull Class<PlayerFeature> capability() {
			return PlayerFeature.class;
		}

		@Override
		public @NotNull Set<AgentExtension> agentExtensions() {
			return Set.of(BUKKIT, VELOCITY);
		}

		@Override
		public @NotNull PlayerFeature create(@NotNull AgentPlayerCapabilityContext context) {
			throw new AssertionError("Discovery creates no capability");
		}
	}

	/**
	 * A process capability whose platform half runs on Paper.
	 */
	public static final class ProcessFeatureProvider implements AgentProcessCapabilityProvider<ProcessFeature> {
		@Override
		public @NotNull CapabilityDescriptor descriptor() {
			return CapabilityDescriptor.builder().id("example.process-feature").build();
		}

		@Override
		public @NotNull Class<ProcessFeature> capability() {
			return ProcessFeature.class;
		}

		@Override
		public @NotNull Set<AgentExtension> agentExtensions() {
			return Set.of(CONSOLE);
		}

		@Override
		public @NotNull ProcessFeature create(@NotNull AgentProcessCapabilityContext context) {
			throw new AssertionError("Discovery creates no capability");
		}
	}

	private interface PlayerFeature extends PlayerCapability { }

	private interface ProcessFeature extends ProcessCapability { }
}
