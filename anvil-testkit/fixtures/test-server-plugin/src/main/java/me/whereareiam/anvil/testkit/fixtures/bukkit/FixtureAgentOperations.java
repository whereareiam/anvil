package me.whereareiam.anvil.testkit.fixtures.bukkit;

import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationProvider;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationRegistry;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;

/**
 * External-style extension compiled against public agent and Bukkit APIs only.
 */
public final class FixtureAgentOperations implements AgentOperationProvider {
	@Override
	public @NotNull String id() {
		return "anvil.testing.fixture";
	}

	@Override
	public boolean supports(@NotNull AgentInfo platform) {
		return platform.getRole() == AgentRole.SERVER;
	}

	@Override
	public void install(@NotNull AgentOperationRegistry registry) {
		registry.register(AgentOperation.<String, String>builder().name("anvil.testing.fixture.echo")
				.requestType(String.class).responseType(String.class).build(), (platform, request) -> platform.call(() -> {
			Server server = platform.requireService(Server.class);
			if (!Bukkit.isPrimaryThread() || !server.getPluginManager().isPluginEnabled("AnvilSystemFixture"))
				throw new IllegalStateException("External operation did not run with the expected platform services");
			return "external:" + request;
		}));
	}
}
