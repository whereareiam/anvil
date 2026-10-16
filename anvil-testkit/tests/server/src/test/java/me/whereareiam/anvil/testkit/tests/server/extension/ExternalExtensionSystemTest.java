package me.whereareiam.anvil.testkit.tests.server.extension;

import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.capability.console.Console;
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
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.testkit.support.FixtureArtifacts;
import me.whereareiam.anvil.testkit.support.TestExtensionLoader;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import me.whereareiam.anvil.testkit.tests.server.scenario.Paper12111SystemScenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests an external protocol library and capabilities calling extensions installed in a real Paper agent, and the
 * process capability on a real Lumi agent too.
 */
class ExternalExtensionSystemTest {
	@TempDir
	Path temporary;

	@Test
	void combinesExternalSessionAndMovementWithRealAgentOperations() throws Exception {
		try (var fixture = new TestExtensionLoader(FixtureArtifacts.extension(), true)) {
			SimulatedPlayer player;
			Session session;
			try (ScenarioEngine engine = AnvilLauncher.create(options().toBuilder().protocolLibrary("fixture").build());
				 var context = engine.start(scenario(fixture.artifact()))) {
				player = context.players().create("FixturePlayer");
				assertEquals("FixturePlayer@1.21.11", probe(fixture, player, "label", new Class<?>[0]));
				assertEquals("FixturePlayer@1.21.11", probe(fixture, player, "agentLabel", new Class<?>[0]));
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
	void usesServerObservationAndStructuredAgentPayloadsWithoutASessionAdapter() throws Exception {
		try (var fixture = new TestExtensionLoader(FixtureArtifacts.observationExtension(), false);
			 var engine = AnvilLauncher.create(options())) {
			try (var context = engine.start(scenario(fixture.artifact()))) {
				var player = context.players().create("Observer");
				assertEquals("Observer@1.21.11", probe(fixture, player, "label", new Class<?>[0]));
				assertEquals("Observer@1.21.11", probe(fixture, player, "agentLabel", new Class<?>[0]));
				assertFalse(player.hasCapability(Session.class));
				assertEquals("Observer", player.capability(Server.class).identity().getRequested().getUsername());
				assertEquals("fixture:agent-only", probe(fixture, player, "echo", new Class<?>[] {String.class, String.class}, "server", "agent-only"));
				Class<? extends ProcessCapability> echoType = fixture.load("external.fixture.capability.ProcessEcho")
						.asSubclass(ProcessCapability.class);
				var echo = context.processes().server("server").capability(echoType);
				assertEquals("fixture:Alice#2", echoType.getMethod("greet", String.class, int.class).invoke(echo, "Alice", 1));
			}
		}
		assertEquals(List.of("created", "closed"), Files.readAllLines(temporary.resolve("cache/fixture-observer.lifecycle")));
	}

	private EngineOptions options() {
		return EngineOptions.builder().eulaAccepted(true).cacheDirectory(temporary.resolve("cache"))
				.workDirectory(Path.of("build/anvil/external-extension")).build();
	}

	/**
	 * Runs on Paper and on Lumi, whose agent loads extensions in a class loader apart from Lumi's own Jackson, so the
	 * annotated greeting payload binds there as well.
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("processScenarios")
	void usesProcessCapabilitiesWithoutPlayersAcrossAProcessRestart(String name, AnvilScenario base) throws Exception {
		try (var fixture = new TestExtensionLoader(FixtureArtifacts.observationExtension(), false);
			 var engine = AnvilLauncher.create(options())) {
			RunningProcess process;
			Class<? extends ProcessCapability> echoType = fixture.load("external.fixture.capability.ProcessEcho")
					.asSubclass(ProcessCapability.class);
			try (var context = engine.start(scenario(base, fixture.artifact()))) {
				assertTrue(context.players().all().isEmpty());
				process = context.processes().server("server");
				var echo = process.capability(echoType);
				var console = process.capability(Console.class);
				assertTrue(console.execute("list"));
				assertEquals("fixture:before", echoType.getMethod("echo", String.class).invoke(echo, "before"));
				assertEquals("fixture:Alice#2", echoType.getMethod("greet", String.class, int.class).invoke(echo, "Alice", 1));
				echoType.getMethod("prefix", String.class).invoke(echo, "first-generation");
				assertEquals("first-generation", echoType.getMethod("prefix").invoke(echo));
				assertEquals("first-generation:changed", echoType.getMethod("echo", String.class).invoke(echo, "changed"));

				var after = context.processes().restart("server");
				assertNotSame(process, after);
				assertEquals(ProcessState.STOPPED, process.state());
				assertEquals(ProcessState.READY, after.state());
				assertSame(echo, after.capability(echoType));
				assertSame(echo, process.capability(echoType));
				assertSame(console, after.capability(Console.class));
				assertTrue(console.execute("list"));
				assertEquals("fixture:after", echoType.getMethod("echo", String.class).invoke(echo, "after"));
				assertEquals("fixture", echoType.getMethod("prefix").invoke(echo));
				echoType.getMethod("prefix", String.class).invoke(echo, "replacement-generation");
				assertEquals("replacement-generation", echoType.getMethod("prefix").invoke(echo));
				assertEquals("replacement-generation:changed", echoType.getMethod("echo", String.class).invoke(echo, "changed"));
				assertTrue(context.players().all().isEmpty());
			}
			assertFalse(process.hasCapability(Console.class));
			assertThrows(CapabilityUnavailableException.class, () -> process.capability(echoType));
		}
	}

	private static Stream<Arguments> processScenarios() {
		return Stream.of(
				Arguments.of("paper", new Paper12111SystemScenario().define()),
				Arguments.of("lumi", CompatibilityScenarioFactory.lumiScenarios().getLast())
		);
	}

	private AnvilScenario scenario(Path extension) {
		return scenario(new Paper12111SystemScenario().define(), extension);
	}

	private AnvilScenario scenario(AnvilScenario base, Path extension) {
		var server = base.getServers().getFirst();
		var workspace = server.getWorkspace().toBuilder().asset(WorkspaceAsset.builder().group("external-agent")
				.source(AssetSource.path(extension))
				.target(Path.of("plugins/anvil-agent-extensions/external.jar")).build()).build();
		return base.toBuilder().name("external-extension-" + base.getName()).clearServers()
				.server(server.toBuilder().workspace(workspace).build()).build();
	}

	private Object probe(TestExtensionLoader fixture, SimulatedPlayer player, String method,
			Class<?>[] parameters, Object... arguments) throws Exception {
		Class<? extends PlayerCapability> type = fixture.load("external.fixture.capability.FixtureProbeProvider$Probe")
				.asSubclass(PlayerCapability.class);
		return type.getMethod(method, parameters).invoke(player.capability(type), arguments);
	}
}
