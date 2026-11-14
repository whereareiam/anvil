package me.whereareiam.anvil.capability.worlds;

import me.whereareiam.anvil.capability.agent.api.model.AgentExtension;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldsProviderTest {
	private final WorldsProvider provider = new WorldsProvider();

	@Test
	void describesTheWorldsCapabilityWithoutDependencies() {
		assertEquals("anvil.worlds", provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Worlds.class, provider.capability());
	}

	@Test
	void servesTheServersOfThePlatformsItHasAPlatformSideFor() {
		assertTrue(provider.supportsPlatform("paper"));
		assertTrue(provider.supportsPlatform("spigot"));
		assertTrue(provider.supportsPlatform("neoforge"));
		assertFalse(provider.supportsPlatform("velocity"));
		assertFalse(provider.supportsPlatform("bungeecord"));
	}

	@Test
	void installsTheAgentChannelTogetherWithEachPlatformSide() {
		assertEquals(Set.of(
				AgentExtension.builder().platform("paper").platform("spigot").platform("neoforge")
						.entrypoint("me.whereareiam.anvil.capability.worlds.agent.WorldsOperations").build(),
				AgentExtension.builder().platform("paper").platform("spigot")
						.entrypoint("me.whereareiam.anvil.capability.worlds.bukkit.BukkitWorldsOperations").build(),
				AgentExtension.builder().platform("neoforge")
						.entrypoint("me.whereareiam.anvil.capability.worlds.neoforge.v1_21_1.NeoForgeWorldsOperations").build(),
				AgentExtension.builder().platform("neoforge")
						.entrypoint("me.whereareiam.anvil.capability.worlds.neoforge.v1_21_11.NeoForgeWorldsOperations").build()
		), provider.agentExtensions());
	}
}
