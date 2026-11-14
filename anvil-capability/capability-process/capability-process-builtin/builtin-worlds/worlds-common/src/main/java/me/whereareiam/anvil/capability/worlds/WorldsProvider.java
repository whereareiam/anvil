package me.whereareiam.anvil.capability.worlds;

import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.capability.agent.api.model.AgentExtension;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.worlds.agent.WorldsOperations;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Connects the public worlds capability to the worlds agent extension of its server's platform. It serves the
 * platforms the worlds family has a platform side for, and installs into their processes the agent channel, which
 * every platform side loads, together with that platform side: on NeoForge, every part of it, of which the part for
 * the server's Minecraft release installs.
 */
public final class WorldsProvider implements AgentProcessCapabilityProvider<Worlds> {
	private static final Set<String> BUKKIT = Set.of(Platforms.PAPER, Platforms.SPIGOT);
	private static final Set<String> NEOFORGE = Set.of(Platforms.NEOFORGE);
	private static final Set<String> SERVERS = Set.of(Platforms.PAPER, Platforms.SPIGOT, Platforms.NEOFORGE);
	private static final String BUKKIT_OPERATIONS = "me.whereareiam.anvil.capability.worlds.bukkit.BukkitWorldsOperations";
	private static final List<String> NEOFORGE_OPERATIONS = List.of(
			"me.whereareiam.anvil.capability.worlds.neoforge.v1_21_1.NeoForgeWorldsOperations",
			"me.whereareiam.anvil.capability.worlds.neoforge.v1_21_11.NeoForgeWorldsOperations"
	);

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("anvil.worlds").build();
	}

	@Override
	public @NotNull Class<Worlds> capability() {
		return Worlds.class;
	}

	@Override
	public boolean supportsPlatform(@NotNull String platformId) {
		return SERVERS.contains(platformId);
	}

	@Override
	public @NotNull Set<AgentExtension> agentExtensions() {
		Set<AgentExtension> extensions = new LinkedHashSet<>();
		extensions.add(AgentExtension.builder().platforms(SERVERS).entrypoint(WorldsOperations.class.getName()).build());
		extensions.add(AgentExtension.builder().platforms(BUKKIT).entrypoint(BUKKIT_OPERATIONS).build());
		for (String operations : NEOFORGE_OPERATIONS)
			extensions.add(AgentExtension.builder().platforms(NEOFORGE).entrypoint(operations).build());

		return Set.copyOf(extensions);
	}

	@Override
	public @NotNull Worlds create(@NotNull AgentProcessCapabilityContext context) {
		return new AgentWorlds(context.processName(), context.channel());
	}
}
