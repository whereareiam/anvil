package me.whereareiam.anvil.testkit.tests.runtime.provider;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.RemoteEndpoint;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.support.FixtureArtifacts;
import me.whereareiam.anvil.testkit.support.TestExtensionLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a scenario of remotes alone starts and finishes through the default launcher without any process,
 * and that its players keep the capabilities the client owns. The in-process fixture library answers the remote
 * itself, so no network is needed: the remote's address names a port on this machine, which looks no SRV record up.
 */
class RemoteEndpointIntegrationTest {
	private static final String LIBRARY = "java-fixture";

	@TempDir
	Path temporary;

	@Test
	void aScenarioOfRemotesAloneStartsNoProcessAndItsPlayersUseTheirOwnCapabilities() throws Exception {
		try (var ignored = new TestExtensionLoader(FixtureArtifacts.javaLibrary(), false);
		     var engine = AnvilLauncher.create(options())) {
			ScenarioContext context = engine.start(scenario());
			try {
				assertTrue(context.processes().all().isEmpty());
				assertThrows(NoSuchElementException.class, () -> context.processes().get("network"));

				SimulatedPlayer alice = context.players().create("Alice");
				assertEquals("1.21.11", alice.clientVersion());
				Session session = alice.capability(Session.class);
				session.connect();
				session.connected();
				Messages messages = alice.capability(Messages.class);
				messages.chat("hello");
				assertEquals("<Alice> hello", messages.received("<Alice> hello"));
				session.disconnect();
				session.disconnected();
				assertFalse(session.state().connected());

				var refused = assertThrows(ScenarioValidationException.class, () -> context.players().create(
						PlayerOptions.builder().name("Bob")
								.connection(PlayerConnection.builder().sourceAddress("127.0.0.2").build()).build()));
				assertTrue(refused.getMessage().contains("is a remote that Anvil does not run"), refused.getMessage());
			} finally {
				context.finish(true);
			}
		}
	}

	private EngineOptions options() {
		return EngineOptions.builder()
				.cacheDirectory(temporary.resolve("cache"))
				.accountsDirectory(temporary.resolve("accounts"))
				.workDirectory(temporary.resolve("work"))
				.protocolLibrary(LIBRARY)
				.build();
	}

	private static AnvilScenario scenario() {
		return AnvilScenario.builder()
				.name("remote-only")
				.entrypoint("network")
				.remote(RemoteEndpoint.builder()
						.name("network")
						.address("localhost:25570")
						.minecraftVersion("1.21.11")
						.build())
				.build();
	}
}
