package me.whereareiam.anvil.capability.ticks;

import me.whereareiam.anvil.capability.agent.api.model.AgentExtension;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicksProviderTest {
	private final TicksProvider provider = new TicksProvider();

	@Test
	void describesTheTicksCapabilityWithoutDependencies() {
		assertEquals("anvil.ticks", provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Ticks.class, provider.capability());
	}

	@Test
	void servesTheServersOfThePlatformsItHasAPlatformSideFor() {
		assertTrue(provider.supportsPlatform("neoforge"));
		assertFalse(provider.supportsPlatform("paper"));
		assertFalse(provider.supportsPlatform("velocity"));
	}

	@Test
	void installsTheAgentChannelTogetherWithThePlatformSide() {
		assertEquals(Set.of(
				AgentExtension.builder().platform("neoforge")
						.entrypoint("me.whereareiam.anvil.capability.ticks.agent.TicksOperations").build(),
				AgentExtension.builder().platform("neoforge")
						.entrypoint("me.whereareiam.anvil.capability.ticks.neoforge.NeoForgeTicksOperations").build()
		), provider.agentExtensions());
	}
}
