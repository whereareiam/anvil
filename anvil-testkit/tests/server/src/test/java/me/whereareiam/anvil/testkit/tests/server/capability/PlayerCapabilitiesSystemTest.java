package me.whereareiam.anvil.testkit.tests.server.capability;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.capability.commands.Commands;
import me.whereareiam.anvil.capability.commands.type.CommandCheck;
import me.whereareiam.anvil.capability.cookies.CookieWatch;
import me.whereareiam.anvil.capability.cookies.Cookies;
import me.whereareiam.anvil.capability.cookies.model.CookieRequest;
import me.whereareiam.anvil.capability.dialogs.DialogWatch;
import me.whereareiam.anvil.capability.dialogs.Dialogs;
import me.whereareiam.anvil.capability.dialogs.model.click.CommandConfirmation;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogButton;
import me.whereareiam.anvil.capability.interaction.Interaction;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import me.whereareiam.anvil.capability.inventory.Inventory;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.capability.messages.MessageWatch;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.screen.ActionBarWatch;
import me.whereareiam.anvil.capability.screen.BossBarWatch;
import me.whereareiam.anvil.capability.screen.Screen;
import me.whereareiam.anvil.capability.screen.TitleWatch;
import me.whereareiam.anvil.capability.screen.model.BossBar;
import me.whereareiam.anvil.capability.screen.model.Title;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.engine.config.EngineProperties;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.tests.server.extension.FixtureAgentProbeProvider;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives every built-in player capability through the fixture plugin on the direct Paper scenario of each Minecraft
 * version in the live matrix, so the capability code of every protocol library release meets a real server. The
 * {@code anvil.matrix.filter} system property, set from {@code -PanvilMatrixFilter}, selects scenarios by name. Its
 * {@code capabilities} tag gives the journey across versions its own CI shard.
 */
@Tag("capabilities")
class PlayerCapabilitiesSystemTest {
	/**
	 * Scenarios whose Minecraft version predates dialogs, which arrived in 1.21.6.
	 */
	private static final Set<String> RELEASES_WITHOUT_DIALOGS = Set.of("paper-1.18.2", "paper-1.21.1");
	/**
	 * Scenarios whose Minecraft version predates cookies, which arrived in 1.20.5.
	 */
	private static final Set<String> RELEASES_WITHOUT_COOKIES = Set.of("paper-1.18.2");

	@TestFactory
	Stream<DynamicTest> controlsAProtocolPlayerOnEveryPaperRelease() {
		String filter = System.getProperty("anvil.matrix.filter", ".*");

		return CompatibilityScenarioFactory.paperReleaseScenarios().stream()
				.filter(scenario -> scenario.getName().matches(filter))
				.map(scenario -> DynamicTest.dynamicTest(scenario.getName(), () -> run(scenario)));
	}

	private void run(AnvilScenario scenario) {
		try (ScenarioEngine engine = AnvilLauncher.create(EngineProperties.fromSystemProperties());
		     ScenarioContext anvil = engine.start(scenario)) {
			try {
				verify(anvil, !RELEASES_WITHOUT_DIALOGS.contains(scenario.getName()), !RELEASES_WITHOUT_COOKIES.contains(scenario.getName()));
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

	private void verify(ScenarioContext anvil, boolean dialogs, boolean cookies) {
		SimulatedPlayer alice = anvil.players().create("Alice");
		assertEquals("external:hello", alice.capability(FixtureAgentProbeProvider.Probe.class).echo("server", "hello"));
		Session session = alice.capability(Session.class);
		Messages messages = alice.capability(Messages.class);
		Players server = anvil.processes().server("server").capability(Players.class);
		PlayerWatch arrivals = server.watch();
		Movement movement = alice.capability(Movement.class);
		Inventory inventory = alice.capability(Inventory.class);
		Interaction interaction = alice.capability(Interaction.class);

		session.connect();
		session.connected(Duration.ofSeconds(20));
		messages.received("anvil:welcome", Duration.ofSeconds(20));

		Commands commands = alice.capability(Commands.class);
		commands.tree(tree -> tree.literal("anvil-fixture").isPresent(), Duration.ofSeconds(10));
		assertEquals(CommandCheck.NO_ISSUES, commands.check("anvil-fixture ping"));
		assertEquals(CommandCheck.PARSE_ERRORS, commands.check("anvil-unknown-command"));

		messages.command("anvil-fixture ping");
		messages.received("anvil:pong", Duration.ofSeconds(10));

		messages.chat("hello-from-anvil");
		messages.received("hello-from-anvil", Duration.ofSeconds(10));

		assertEquals("Alice", alice.requestedProfile().getUsername());
		assertEquals("Alice", arrivals.joined(alice, Duration.ofSeconds(10)).getProfile().getUsername());
		assertEquals("Alice", server.profile(alice).orElseThrow().getUsername());

		messages.command("anvil-fixture position");
		String[] position = message(messages, "anvil:position:").split(":");
		movement.move(Position.builder()
				.x(Double.parseDouble(position[2]) + 0.15D)
				.y(Double.parseDouble(position[3]))
				.z(Double.parseDouble(position[4]))
				.yaw(Float.parseFloat(position[5]))
				.pitch(Float.parseFloat(position[6]))
				.onGround(true)
				.build());
		messages.received("anvil:move", Duration.ofSeconds(10));

		messages.command("anvil-fixture item");
		messages.received("anvil:item:diamond", Duration.ofSeconds(10));
		inventory.selectSlot(0);
		interaction.useItem(Hand.MAIN);

		messages.command("anvil-fixture block");
		String[] block = message(messages, "anvil:block-position:").split(":");
		interaction.block(
				BlockPosition.builder()
						.x(Integer.parseInt(block[2]))
						.y(Integer.parseInt(block[3]))
						.z(Integer.parseInt(block[4]))
						.build(),
				BlockFace.UP,
				Hand.MAIN
		);
		messages.received("anvil:block:minecraft:diamond_block", Duration.ofSeconds(10));

		messages.command("anvil-fixture entity");
		int entityId = Integer.parseInt(message(messages, "anvil:entity-id:")
				.substring("anvil:entity-id:".length()));
		interaction.entity(entityId, EntityInteraction.INTERACT, Hand.MAIN);
		messages.received("anvil:entity:" + entityId, Duration.ofSeconds(10));

		messages.command("anvil-fixture gui");
		inventory.matching(snapshot -> snapshot.getContainerId() != 0, Duration.ofSeconds(10));
		inventory.click(4, InventoryClick.LEFT, 0);
		messages.received("anvil:click:4", Duration.ofSeconds(10));

		verifyDialogs(alice, messages, dialogs);
		verifyCookies(anvil, alice, messages, cookies);
		verifyScreen(anvil, alice);

		messages.command("anvil-fixture kick");
		assertEquals("anvil:requested-kick", session.kicked(Duration.ofSeconds(10)));
		assertTrue(session.state().kickReason().contains("anvil:requested-kick"));
		if (cookies) alice.capability(Cookies.class).store("anvil:arrival", "carried".getBytes(StandardCharsets.UTF_8));
		session.rejoin();
		session.connected(Duration.ofSeconds(20));
		verifyCookiesOfANewConnection(anvil, alice, messages, cookies);
		alice.destroy();
		assertTrue(alice.state().destroyed());
		assertTrue(anvil.players().all().isEmpty());
	}

	/**
	 * Lets the server store a cookie and ask for it, and gives and takes away a cookie the server then asks for, or
	 * checks that a player of a release without cookies has no Cookies capability.
	 */
	private void verifyCookies(ScenarioContext anvil, SimulatedPlayer alice, Messages messages, boolean supported) {
		assertEquals(supported, alice.hasCapability(Cookies.class));
		if (!supported) return;

		Cookies cookies = alice.capability(Cookies.class);
		CookieWatch asked = cookies.watch();
		cookie(anvil, "store anvil:ticket day-pass");
		messages.received("anvil:cookie-stored:anvil:ticket", Duration.ofSeconds(10));
		assertEquals("day-pass", new String(cookies.stored("anvil:ticket", Duration.ofSeconds(10)), StandardCharsets.UTF_8));

		cookie(anvil, "request anvil:ticket");
		messages.received("anvil:cookie:anvil:ticket:day-pass", Duration.ofSeconds(10));
		CookieRequest request = asked.requested("anvil:ticket", Duration.ofSeconds(10));
		assertEquals("day-pass", new String(request.getAnswer(), StandardCharsets.UTF_8));

		cookies.store("anvil:gift", "ribbon".getBytes(StandardCharsets.UTF_8));
		cookie(anvil, "request anvil:gift");
		messages.received("anvil:cookie:anvil:gift:ribbon", Duration.ofSeconds(10));
		cookies.remove("anvil:gift");
		cookie(anvil, "request anvil:gift");
		messages.received("anvil:cookie:anvil:gift:none", Duration.ofSeconds(10));
		asked.neverRequested("anvil:unasked", Duration.ofSeconds(1));
	}

	/**
	 * Checks that a new connection starts without the cookies of the previous one, holding only the cookie given
	 * while the player was disconnected.
	 */
	private void verifyCookiesOfANewConnection(ScenarioContext anvil, SimulatedPlayer alice, Messages messages, boolean supported) {
		if (!supported) return;

		Cookies cookies = alice.capability(Cookies.class);
		cookie(anvil, "request anvil:ticket");
		messages.received("anvil:cookie:anvil:ticket:none", Duration.ofSeconds(10));
		cookie(anvil, "request anvil:arrival");
		messages.received("anvil:cookie:anvil:arrival:carried", Duration.ofSeconds(10));
		assertEquals(List.of("anvil:arrival"), List.copyOf(cookies.stored().keySet()));
	}

	/**
	 * Shows Alice a title with a subtitle, an action bar and a boss bar through the vanilla commands, and checks what
	 * reaches her screen, including the boss bar's progress and its removal.
	 */
	private void verifyScreen(ScenarioContext anvil, SimulatedPlayer alice) {
		Screen screen = alice.capability(Screen.class);
		TitleWatch titles = screen.watchTitles();
		ActionBarWatch actionBars = screen.watchActionBars();
		BossBarWatch bossBars = screen.watchBossBars();

		console(anvil, "title Alice times 5 40 5");
		console(anvil, "title Alice subtitle \"anvil:subtitle\"");
		console(anvil, "title Alice title \"anvil:title\"");
		Title title = titles.shown(shown -> shown.getTitle().equals("anvil:title") && shown.getSubtitle().equals("anvil:subtitle"),
				Duration.ofSeconds(10));
		assertEquals(40, title.getStayTicks());

		console(anvil, "title Alice actionbar \"anvil:action-bar\"");
		actionBars.shown("anvil:action-bar", Duration.ofSeconds(10));

		console(anvil, "bossbar add anvil:screen \"anvil:boss-bar\"");
		console(anvil, "bossbar set anvil:screen players Alice");
		console(anvil, "bossbar set anvil:screen value 50");
		BossBar bar = bossBars.shown(shown -> shown.getText().equals("anvil:boss-bar") && shown.getProgress() == 0.5F,
				Duration.ofSeconds(10));
		assertEquals(List.of(bar), screen.bossBars());
		console(anvil, "bossbar remove anvil:screen");
		bossBars.removed(removed -> removed.getId().equals(bar.getId()), Duration.ofSeconds(10));
		assertTrue(screen.bossBars().isEmpty());
	}

	private static void console(ScenarioContext anvil, String command) {
		anvil.processes().server("server").console().sendCommand(command);
	}

	/**
	 * Drives the fixture's cookie command for Alice from the server console, which does not count towards the
	 * spam limit her own commands are under.
	 */
	private static void cookie(ScenarioContext anvil, String arguments) {
		anvil.processes().server("server").console().sendCommand("anvil-cookie Alice " + arguments);
	}

	/**
	 * Fills and presses a dialog the server shows through the vanilla command, including commands the client asks
	 * about and a nested dialog, or checks that a player of a release without dialogs has no Dialogs capability.
	 */
	private void verifyDialogs(SimulatedPlayer alice, Messages messages, boolean supported) {
		assertEquals(supported, alice.hasCapability(Dialogs.class));
		if (!supported) return;

		Dialogs dialogs = alice.capability(Dialogs.class);
		messages.command("anvil-fixture op");
		messages.received("anvil:op", Duration.ofSeconds(10));
		messages.command("anvil-fixture dialog");
		Dialog dialog = dialogs.shown("anvil:dialog", Duration.ofSeconds(10));
		assertEquals(List.of("anvil:dialog-body"), dialog.getBody());
		assertEquals(List.of("Echo", "Nested", "Unknown", "Signed", "Seed", "Clear"),
				dialog.getButtons().stream().map(DialogButton::getLabel).toList());

		dialogs.press("Echo", Map.of("word", "hello", "flag", "true"));
		messages.received("anvil:dialog-echo:hello:on", Duration.ofSeconds(10));
		assertTrue(dialogs.current().isPresent());

		dialogs.press("Unknown");
		assertEquals(new CommandConfirmation("anvil-unknown-command", CommandCheck.PARSE_ERRORS), dialogs.confirmation().orElseThrow());
		dialogs.decline();
		assertEquals("anvil:dialog", dialogs.current().orElseThrow().getTitle());
		dialogs.press("Unknown");
		dialogs.confirm();
		messages.received("command.unknown.command", Duration.ofSeconds(10));

		dialogs.press("Signed");
		assertEquals(CommandCheck.SIGNATURE_REQUIRED, dialogs.confirmation().orElseThrow().getReason());
		MessageWatch afterSigned = messages.watch();
		dialogs.confirm();
		afterSigned.neverReceived("anvil:signed", Duration.ofSeconds(2));

		dialogs.press("Seed");
		assertEquals(CommandCheck.PERMISSIONS_REQUIRED, dialogs.confirmation().orElseThrow().getReason());
		dialogs.confirm();
		messages.received("commands.seed.success", Duration.ofSeconds(10));

		dialogs.press("Nested");
		assertEquals("anvil:nested", dialogs.current().orElseThrow().getTitle());
		dialogs.close();
		dialogs.closed(Duration.ofSeconds(10));

		DialogWatch again = dialogs.watch();
		messages.command("anvil-fixture dialog");
		again.shown(shown -> shown.getTitle().equals("anvil:dialog"), Duration.ofSeconds(10));
		dialogs.press("Clear");
		dialogs.closed(Duration.ofSeconds(10));
	}

	private String message(Messages messages, String prefix) {
		return messages.received(prefix, Duration.ofSeconds(10));
	}
}
