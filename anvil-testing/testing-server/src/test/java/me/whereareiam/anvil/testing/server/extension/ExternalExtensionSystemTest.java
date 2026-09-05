package me.whereareiam.anvil.testing.server.extension;

import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.engine.AnvilEngine;
import me.whereareiam.anvil.engine.model.EngineOptions;
import me.whereareiam.anvil.testing.fixture.extension.ExternalExtensionFixture;
import me.whereareiam.anvil.testing.server.scenario.Paper12111SystemScenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests an external backend and capabilities calling extensions installed in a real Paper agent.
 */
class ExternalExtensionSystemTest {
	@TempDir
	Path temporary;

	@Test
	void combinesExternalSessionAndMovementWithRealAgentOperations() throws Exception {
		try (var fixture = new ExternalExtensionFixture(temporary, "FixtureProtocolProvider", true)) {
			SimulatedPlayer player;
			Session session;
			try (AnvilEngine engine = new AnvilEngine(options().toBuilder().protocolId("fixture").build());
				 var context = engine.start(scenario(fixture.artifact()))) {
				player = context.players().create("FixturePlayer");
				session = player.capability(Session.class);
				assertFalse(session.state().connected());
				session.connect();
				session.connected();
				player.capability(Movement.class).move(Position.builder().x(1).y(2).z(3).build());
				assertEquals("move:1.0:2.0:3.0", probe(fixture, player, "lastSent", new Class<?>[0]));
				assertEquals("fixture:hello", probe(fixture, player, "echo", new Class<?>[] {String.class, String.class}, "server", "hello"));
				assertThrows(CapabilityUnavailableException.class, () -> player.capability(Messages.class));
				session.disconnect();
				session.disconnected();
				session.rejoin();
				assertTrue(session.state().connected());
			}
			assertTrue(player.state().destroyed());
			assertFalse(session.state().connected());
			assertEquals(List.of("created", "closed"), Files.readAllLines(temporary.resolve("cache/fixture.lifecycle")));
		}
	}

	@Test
	void usesServerObservationAndAgentOperationsWithoutASessionAdapter() throws Exception {
		try (var fixture = new ExternalExtensionFixture(temporary, "ObservationProtocolProvider", false);
			 var engine = new AnvilEngine(options())) {
			try (var context = engine.start(scenario(fixture.artifact()))) {
				var player = context.players().create("Observer");
				assertFalse(player.hasCapability(Session.class));
				assertEquals("Observer", player.capability(Server.class).identity().getUsername());
				assertEquals("fixture:agent-only", probe(fixture, player, "echo", new Class<?>[] {String.class, String.class}, "server", "agent-only"));
			}
		}
		assertEquals(List.of("created", "closed"), Files.readAllLines(temporary.resolve("cache/fixture-observer.lifecycle")));
	}

	private EngineOptions options() {
		return EngineOptions.builder().eulaAccepted(true).cacheDirectory(temporary.resolve("cache"))
				.workDirectory(Path.of("build/anvil/external-extension")).build();
	}

	private AnvilScenario scenario(Path extension) {
		AnvilScenario base = new Paper12111SystemScenario().define();
		var server = base.getServers().getFirst();
		var workspace = server.getWorkspace().toBuilder().asset(WorkspaceAsset.builder().group("external-agent")
				.source(AssetSource.path(extension))
				.target(Path.of("plugins/anvil-agent-extensions/external.jar")).build()).build();
		return base.toBuilder().name("external-extension").clearServers()
				.server(server.toBuilder().workspace(workspace).build()).build();
	}

	private Object probe(ExternalExtensionFixture fixture, SimulatedPlayer player, String method,
			Class<?>[] parameters, Object... arguments) throws Exception {
		Class<? extends PlayerCapability> type = fixture.load("external.fixture.capability.FixtureProbeProvider$Probe")
				.asSubclass(PlayerCapability.class);
		return type.getMethod(method, parameters).invoke(player.capability(type), arguments);
	}
}
