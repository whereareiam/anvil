package me.whereareiam.anvil.testkit.tests.server.platform;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.capability.dispatch.Dispatch;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.capability.world.DimensionWatch;
import me.whereareiam.anvil.capability.world.World;
import me.whereareiam.anvil.capability.worlds.WorldChangeWatch;
import me.whereareiam.anvil.capability.worlds.Worlds;
import me.whereareiam.anvil.capability.worlds.model.ServerWorld;
import me.whereareiam.anvil.capability.worlds.model.WorldCreation;
import me.whereareiam.anvil.capability.worlds.model.WorldLocation;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.protocol.api.model.text.SpanStyle;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.api.model.text.TextColor;
import me.whereareiam.anvil.protocol.api.model.text.TextSpan;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Logs a player in on every NeoForge release, which loads no fixture plugin, drives it with vanilla commands,
 * including a styled message that must arrive with its styles, and moves it to the nether through the release's own
 * part of the worlds capability.
 */
class NeoForgeServerSystemTest {
	@TestFactory
	Stream<DynamicTest> runsEveryNeoForgeRelease() {
		return PlatformSelection.select(CompatibilityScenarioFactory.neoForgeScenarios()).stream()
				.map(scenario -> DynamicTest.dynamicTest(scenario.getName(), () -> verify(scenario)));
	}

	private void verify(AnvilScenario scenario) {
		EngineOptions options = EngineOptions.builder().eulaAccepted(true).build();
		try (ScenarioEngine engine = AnvilLauncher.create(options); var context = engine.start(scenario)) {
			SimulatedPlayer alice = context.players().create("Alice");
			Session session = alice.capability(Session.class);
			Messages messages = alice.capability(Messages.class);
			Players players = context.processes().server("server").capability(Players.class);
			Dispatch dispatch = context.processes().server("server").capability(Dispatch.class);
			PlayerWatch arrivals = players.watch();

			session.connect();
			session.connected(Duration.ofSeconds(30));
			assertEquals("Alice", arrivals.joined(alice, Duration.ofSeconds(10)).getProfile().getUsername());

			assertTrue(dispatch.execute("tellraw Alice [\"anvil:\",{\"text\":\"neoforge\",\"color\":\"gold\","
					+ "\"bold\":true,\"insertion\":\"neoforge\"}]"));
			Text message = messages.received("anvil:neoforge", Duration.ofSeconds(10));
			assertEquals(List.of(
					new TextSpan("anvil:", SpanStyle.NONE),
					new TextSpan("neoforge", SpanStyle.builder().color(TextColor.GOLD).bold(true).insertion("neoforge").build())
			), message.spans());

			Worlds worlds = context.processes().server("server").capability(Worlds.class);
			World world = alice.capability(World.class);
			// NeoForge lists its levels in an order of its own after the main world, which changed in 26.1.
			List<String> keys = worlds.worlds().stream().map(ServerWorld::getKey).toList();
			assertEquals("minecraft:overworld", keys.getFirst());
			assertEquals(Set.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"), Set.copyOf(keys));
			assertEquals(Optional.of("minecraft:overworld"), world.dimension());
			WorldChangeWatch changes = worlds.watch();
			DimensionWatch dimensions = world.watch();
			worlds.send(alice, WorldLocation.builder().world("minecraft:the_nether").x(0.5).y(140).z(0.5).build());
			changes.changed(alice, "minecraft:the_nether", Duration.ofSeconds(10));
			dimensions.entered("minecraft:the_nether", Duration.ofSeconds(10));
			assertEquals("minecraft:the_nether", worlds.location(alice).orElseThrow().getWorld());
			assertThrows(UnsupportedOperationException.class,
					() -> worlds.create(WorldCreation.builder().key("anvil_neoforge").build()));
			assertThrows(UnsupportedOperationException.class, () -> worlds.unload("the_end"));
			assertThrows(UnsupportedOperationException.class, () -> worlds.delete("the_end"));

			assertTrue(dispatch.execute("kick Alice anvil:kicked"));
			assertEquals("anvil:kicked", session.kicked());
			assertFalse(session.state().connected());
			arrivals.left(alice, Duration.ofSeconds(10));
			assertEquals(List.of(), players.online());
		}
	}
}
