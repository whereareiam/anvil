package me.whereareiam.anvil.testkit.tests.server.capability;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.capability.console.Console;
import me.whereareiam.anvil.capability.interaction.Interaction;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import me.whereareiam.anvil.capability.inventory.Inventory;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.engine.config.EngineProperties;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Drives the Messages, Movement, Inventory and Interaction capabilities of a Bedrock player on the direct Lumi
 * scenario of each Bedrock version in the live matrix. The server's console sets the scene and observes most effects
 * through the Lumi agent: a console command reports whether it succeeded, which turns the vanilla {@code testfor},
 * {@code testforblock} and {@code clear} commands into assertions on the server's own state. What no vanilla command
 * shows, an entity's runtime ID and what the player did to the entity, the Lumi fixture plugin reports to the player.
 * The {@code anvil.matrix.filter} system property, set from {@code -PanvilMatrixFilter}, selects scenarios by name;
 * the {@code capabilities} tag shares the Java journey's CI shard.
 */
@Tag("capabilities")
class BedrockPlayerCapabilitiesSystemTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final int FIRST_HOTBAR_SLOT = 36;
	private static final int MAIN_INVENTORY_SLOT = 9;
	/**
	 * The player stands on a three-block stone platform at x {@code 0.5}, y {@code 200} and z {@code 0.5}, with the
	 * platform's east end at x {@code 2}. Lumi seeds every new world differently, and some worlds reach this height
	 * there, with mountains or trees, so the console clears the space above the platform up to the build limit.
	 */
	private static final int FLOOR = 199;
	private static final int BUILD_LIMIT = 319;

	@TestFactory
	Stream<DynamicTest> controlsABedrockPlayerOnEveryLumiVersion() {
		String filter = System.getProperty("anvil.matrix.filter", ".*");

		return CompatibilityScenarioFactory.lumiScenarios().stream()
				.filter(scenario -> scenario.getName().matches(filter))
				.map(scenario -> DynamicTest.dynamicTest(scenario.getName(), () -> run(scenario)));
	}

	private void run(AnvilScenario scenario) {
		try (ScenarioEngine engine = AnvilLauncher.create(EngineProperties.fromSystemProperties());
		     ScenarioContext anvil = engine.start(scenario)) {
			try {
				verify(anvil, clientMovement(scenario));
			} catch (Throwable failure) {
				finishFailed(anvil, failure);
				throw failure;
			}
			anvil.finish(true);
		}
	}

	/**
	 * Finishes a failed run so it keeps its workspaces, without letting a cleanup failure hide the test's failure.
	 */
	private static void finishFailed(ScenarioContext anvil, Throwable failure) {
		try {
			anvil.finish(false);
		} catch (RuntimeException cleanup) {
			failure.addSuppressed(cleanup);
		}
	}

	/**
	 * Tests whether the scenario's server lets the client move its player.
	 */
	private static boolean clientMovement(AnvilScenario scenario) {
		return "client-auth".equals(scenario.getServers().getFirst().getSettings().get("general.server-authoritative-movement"));
	}

	private void verify(ScenarioContext anvil, boolean clientMovement) {
		Console console = anvil.processes().server("server").capability(Console.class);
		SimulatedPlayer alice = anvil.players().create("Alice");
		Session session = alice.capability(Session.class);
		session.connect();
		session.connected(Duration.ofSeconds(30));
		alice.capability(Server.class).joined("server", TIMEOUT);

		exchangesMessages(console, alice.capability(Messages.class));
		movesOnAPlatform(console, alice.capability(Movement.class), clientMovement);
		selectsAndClicksItems(console, alice.capability(Inventory.class));
		usesItemsAndBlocks(console, alice.capability(Inventory.class), alice.capability(Interaction.class));
		interactsWithAnEntity(alice.capability(Messages.class), alice.capability(Interaction.class));
	}

	/**
	 * Chat comes back as the server's broadcast, a command's output as the player's command output and a console
	 * message as a raw text message.
	 */
	private void exchangesMessages(Console console, Messages messages) {
		messages.chat("hello-from-anvil");
		messages.received("hello-from-anvil", TIMEOUT);

		messages.command("anvil-unknown-command");
		messages.received("commands.generic.unknown anvil-unknown-command", TIMEOUT);

		assertTrue(console.execute("tellraw Alice {\"rawtext\":[{\"text\":\"anvil:tellraw\"}]}"));
		messages.received("anvil:tellraw", TIMEOUT);
	}

	/**
	 * The console teleports the player onto the platform it builds and clears; the server then sees each move at the
	 * position the player sent. Lumi ignores moves until it completed the teleport. Under server authority the player repeats its
	 * position in the input it sends on every tick, so one move takes effect once Lumi completed the teleport; under
	 * client authority a player sends a position only when it moves, so the player moves again until Lumi takes it.
	 */
	private void movesOnAPlatform(Console console, Movement movement, boolean clientMovement) {
		assertTrue(console.execute("tp Alice 0.5 200 0.5"));
		assertTrue(console.execute("fill 0 " + FLOOR + " 0 2 " + FLOOR + " 0 stone"));
		assertTrue(console.execute("fill 0 " + (FLOOR + 1) + " 0 2 " + BUILD_LIMIT + " 0 air"));
		awaitCommand(console, "testfor @a[name=Alice,x=0.5,y=200,z=0.5,r=0.1]");

		move(console, movement, 0.75D, clientMovement);
		move(console, movement, 0.5D, clientMovement);
	}

	private static void move(Console console, Movement movement, double x, boolean clientMovement) {
		String observed = "testfor @a[name=Alice,x=" + x + ",y=200,z=0.5,r=0.1]";
		Position target = Position.builder()
				.x(x)
				.y(200)
				.z(0.5D)
				.onGround(true)
				.build();
		movement.move(target);
		if (!clientMovement) {
			awaitCommand(console, observed);
			return;
		}

		long deadline = System.nanoTime() + TIMEOUT.toNanos();
		while (!console.execute(observed)) {
			if (System.nanoTime() > deadline) fail("The server did not see the player at x " + x + " within " + TIMEOUT);

			pause(Duration.ofMillis(250));
			movement.move(target);
		}
	}

	/**
	 * The player observes what the console gives it, takes the stack from the hotbar, places it into the main
	 * inventory and drops it from there; the server confirms the drop, which it accepts only for the stack it moved
	 * there, by no longer finding the item on the player.
	 */
	private void selectsAndClicksItems(Console console, Inventory inventory) {
		assertTrue(console.execute("clear Alice"));
		assertTrue(console.execute("give Alice diamond 3"));
		InventoryItem diamonds = item(inventory, "minecraft:diamond");
		assertEquals(3, diamonds.getAmount());
		assertTrue(diamonds.getSlot() >= FIRST_HOTBAR_SLOT, "a given item fills the hotbar first: " + diamonds);

		inventory.click(diamonds.getSlot(), InventoryClick.LEFT, 0);
		inventory.click(MAIN_INVENTORY_SLOT, InventoryClick.LEFT, 0);
		inventory.click(MAIN_INVENTORY_SLOT, InventoryClick.DROP_STACK, 1);
		awaitCommand(console, "testfor @e[type=item]");
		assertFalse(console.execute("clear Alice diamond 0 0"), "the dropped diamonds left the player's inventory");
	}

	/**
	 * The player throws a snowball, which the server consumes, and places the dirt it selects on the platform's
	 * east end, a block it knows from the world the server sent it.
	 */
	private void usesItemsAndBlocks(Console console, Inventory inventory, Interaction interaction) {
		assertTrue(console.execute("give Alice snowball 1"));
		inventory.selectSlot(item(inventory, "minecraft:snowball").getSlot() - FIRST_HOTBAR_SLOT);
		interaction.useItem(Hand.MAIN);
		awaitCommandFails(console, "clear Alice snowball 0 0");

		assertTrue(console.execute("give Alice dirt 1"));
		inventory.selectSlot(item(inventory, "minecraft:dirt").getSlot() - FIRST_HOTBAR_SLOT);
		interaction.block(BlockPosition.builder().x(2).y(FLOOR).z(0).build(), BlockFace.UP, Hand.MAIN);
		awaitCommand(console, "testforblock 2 " + (FLOOR + 1) + " 0 dirt");
		assertFalse(console.execute("clear Alice dirt 0 0"), "placing the dirt used it up");
	}

	/**
	 * The fixture plugin spawns an armor stand where the player stands and reports its runtime ID; the server then
	 * reports the interaction and the attack the player sends at it, the attack last, as it breaks the armor stand.
	 */
	private void interactsWithAnEntity(Messages messages, Interaction interaction) {
		messages.command("anvil-fixture entity");
		String reported = messages.received("anvil:entity-id:", TIMEOUT);
		long entityId = Long.parseLong(reported.substring(reported.indexOf("anvil:entity-id:") + "anvil:entity-id:".length()));

		interaction.entity(entityId, EntityInteraction.INTERACT, Hand.MAIN);
		messages.received("anvil:entity:" + entityId, TIMEOUT);

		interaction.entity(entityId, EntityInteraction.ATTACK, Hand.MAIN);
		messages.received("anvil:attack:" + entityId, TIMEOUT);
	}

	private static InventoryItem item(Inventory inventory, String identifier) {
		InventorySnapshot snapshot = inventory.matching(current -> find(current, identifier).isPresent(), TIMEOUT);
		return find(snapshot, identifier).orElseThrow();
	}

	private static Optional<InventoryItem> find(InventorySnapshot snapshot, String identifier) {
		return snapshot.getItems().stream()
				.filter(item -> identifier.equals(item.getIdentifier()))
				.findFirst();
	}

	/**
	 * Repeats a console command until it succeeds, as the server applies what the player sent on its next tick.
	 */
	private static void awaitCommand(Console console, String command) {
		await(console, command, true);
	}

	private static void awaitCommandFails(Console console, String command) {
		await(console, command, false);
	}

	private static void await(Console console, String command, boolean success) {
		long deadline = System.nanoTime() + TIMEOUT.toNanos();
		while (console.execute(command) != success) {
			if (System.nanoTime() > deadline)
				fail("The console command '" + command + "' did not " + (success ? "succeed" : "fail") + " within " + TIMEOUT);

			pause(Duration.ofMillis(100));
		}
	}

	private static void pause(Duration duration) {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting", interrupted);
		}
	}
}
