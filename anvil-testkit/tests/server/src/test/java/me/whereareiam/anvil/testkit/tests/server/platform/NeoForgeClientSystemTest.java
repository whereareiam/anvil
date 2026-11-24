package me.whereareiam.anvil.testkit.tests.server.platform;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.player.PayloadChannel;
import me.whereareiam.anvil.api.model.player.PlayerClient;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.type.network.ChannelPhase;
import me.whereareiam.anvil.capability.dispatch.Dispatch;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.network.Network;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads every NeoForge release's channels through its Network capability, which lists NeoForge's own, and joins a
 * NeoForge 1.21.1 server as a NeoForge client that claims them: it goes through NeoForge's configuration (registry
 * sync, configuration files, data maps, extensible enums and feature flags, each acknowledged) and plays. On the
 * releases whose client does not present itself as NeoForge yet, such a player is refused when it is created.
 */
class NeoForgeClientSystemTest {
	private static final String NEOFORGE_CLIENT_RELEASE = "neoforge-1.21.1";

	@TestFactory
	Stream<DynamicTest> joinsEveryNeoForgeReleaseAsANeoForgeClientWhereItCan() {
		return PlatformSelection.select(CompatibilityScenarioFactory.neoForgeScenarios()).stream()
				.map(scenario -> DynamicTest.dynamicTest(scenario.getName(), () -> verify(scenario)));
	}

	private void verify(AnvilScenario scenario) {
		EngineOptions options = EngineOptions.builder().eulaAccepted(true).build();
		try (ScenarioEngine engine = AnvilLauncher.create(options); var context = engine.start(scenario)) {
			List<PayloadChannel> channels = context.processes().server("server").capability(Network.class).channels();
			assertTrue(channels.stream().anyMatch(channel -> channel.getId().equals("neoforge:frozen_registry_sync_completed")
					&& channel.getPhase() == ChannelPhase.CONFIGURATION), "NeoForge's own channels: " + channels);
			PlayerOptions neo = PlayerOptions.builder().name("Neo").client(PlayerClient.neoforge(channels)).build();
			if (!scenario.getName().equals(NEOFORGE_CLIENT_RELEASE)) {
				assertThrows(ScenarioValidationException.class, () -> context.players().create(neo));
				return;
			}

			SimulatedPlayer player = context.players().create(neo);
			Session session = player.capability(Session.class);
			session.connect();
			session.connected(Duration.ofSeconds(30));
			assertTrue(context.processes().server("server").capability(Dispatch.class).execute("say anvil:neoforge-client"));
			player.capability(Messages.class).received("anvil:neoforge-client", Duration.ofSeconds(10));
			assertTrue(session.state().connected());
		}
	}
}
