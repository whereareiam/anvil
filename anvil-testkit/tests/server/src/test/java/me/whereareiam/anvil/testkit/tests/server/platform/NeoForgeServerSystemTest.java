package me.whereareiam.anvil.testkit.tests.server.platform;

import me.whereareiam.anvil.api.model.Condition;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.capability.dispatch.Dispatch;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.capability.ticks.Ticks;
import me.whereareiam.anvil.capability.world.DimensionWatch;
import me.whereareiam.anvil.capability.world.World;
import me.whereareiam.anvil.capability.worlds.WorldChangeWatch;
import me.whereareiam.anvil.capability.worlds.Worlds;
import me.whereareiam.anvil.capability.worlds.model.ServerWorld;
import me.whereareiam.anvil.capability.worlds.model.WorldCreation;
import me.whereareiam.anvil.capability.worlds.model.WorldLocation;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.player.api.model.text.SpanStyle;
import me.whereareiam.anvil.player.api.model.text.Text;
import me.whereareiam.anvil.player.api.model.text.TextColor;
import me.whereareiam.anvil.player.api.model.text.TextSpan;
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
 * part of the worlds capability. It also has the server wait for its ticks and sprint through them.
 */
class NeoForgeServerSystemTest {
	@TestFactory
	Stream<DynamicTest> runsEveryNeoForgeRelease() {
		return PlatformSelection.select(CompatibilityScenarioFactory.neoForgeScenarios()).stream()
				.map(scenario -> DynamicTest.dynamicTest(scenario.name(), () -> verify(scenario)));
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
			assertEquals("Alice", arrivals.joined(alice, Duration.ofSeconds(10)).profile().username());

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
			List<String> keys = worlds.worlds().stream().map(ServerWorld::key).toList();
			assertEquals("minecraft:overworld", keys.getFirst());
			assertEquals(Set.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"), Set.copyOf(keys));
			assertEquals(Optional.of("minecraft:overworld"), world.dimension());
			WorldChangeWatch changes = worlds.watch();
			DimensionWatch dimensions = world.dimensions();
			worlds.send(alice, WorldLocation.builder().world("minecraft:the_nether").x(0.5).y(140).z(0.5).build());
			changes.changed(alice, "minecraft:the_nether", Duration.ofSeconds(10));
			dimensions.entered("minecraft:the_nether", Duration.ofSeconds(10));
			assertEquals("minecraft:the_nether", worlds.location(alice).orElseThrow().world());
			assertThrows(UnsupportedOperationException.class,
					() -> worlds.create(WorldCreation.builder().key("anvil_neoforge").build()));
			assertThrows(UnsupportedOperationException.class, () -> worlds.load("anvil_neoforge"));
			assertThrows(UnsupportedOperationException.class, () -> worlds.unload("the_end"));
			assertThrows(UnsupportedOperationException.class, () -> worlds.delete("the_end"));
			worlds.send(alice, "minecraft:overworld");
			dimensions.entered("minecraft:overworld", Duration.ofSeconds(10));

			verifyTicks(context.processes().server("server").capability(Ticks.class));

			assertTrue(dispatch.execute("kick Alice anvil:kicked"));
			assertEquals("anvil:kicked", session.kicked());
			assertFalse(session.state().connected());
			arrivals.left(alice, Duration.ofSeconds(10));
			assertEquals(List.of(), players.online());
		}
	}

	/**
	 * Waits for the server's ticks, sprints a minute of game time, which takes far less than a minute, sprints until
	 * a condition holds and gives up on one that never does, naming what it saw last.
	 */
	private void verifyTicks(Ticks ticks) {
		long before = ticks.tick();
		ticks.await(20, Duration.ofSeconds(10));
		assertTrue(ticks.tick() >= before + 20);

		long started = System.nanoTime();
		ticks.sprint(1_200, Duration.ofSeconds(60));
		assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(30)) < 0,
				"a sprint of 1200 ticks took less than half of the minute they last");

		long target = ticks.tick() + 600;
		long reached = ticks.sprintUntil(Condition.of("the server ran 600 more ticks", ticks::tick, tick -> tick >= target),
				6_000, Duration.ofSeconds(60));
		assertTrue(reached >= target);
		IllegalStateException never = assertThrows(IllegalStateException.class, () -> ticks.until(
				Condition.of("the impossible happens", () -> "nothing yet", seen -> false), Duration.ofSeconds(1)));
		assertTrue(never.getMessage().endsWith("waiting until the impossible happens; last seen: nothing yet"),
				never.getMessage());
	}
}
