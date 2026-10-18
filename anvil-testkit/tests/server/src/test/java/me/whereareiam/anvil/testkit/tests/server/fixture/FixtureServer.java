package me.whereareiam.anvil.testkit.tests.server.fixture;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.session.Session;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The release's server as a test drives it: players join under names of their own, because the server outlives each
 * test, and the fixture plugin's console commands act on a named player without counting towards the spam limit that
 * the player's own commands are under.
 */
@RequiredArgsConstructor
public final class FixtureServer {
	private static final AtomicInteger PLAYERS = new AtomicInteger();
	private static final Duration TIMEOUT = Duration.ofSeconds(20);

	private final @NotNull ScenarioContext anvil;

	/**
	 * Creates a player with a name no earlier test of this run used, connects it and waits for the fixture's
	 * welcome.
	 *
	 * @return the connected player
	 */
	public @NotNull SimulatedPlayer join() {
		SimulatedPlayer player = anvil.players().create("Player" + PLAYERS.incrementAndGet());
		player.capability(Session.class).connect();
		player.capability(Session.class).connected(TIMEOUT);
		player.capability(Messages.class).received("anvil:welcome", TIMEOUT);

		return player;
	}

	/**
	 * Runs a command on the server console.
	 *
	 * @param command the command, without a slash
	 */
	public void console(@NotNull String command) {
		anvil.processes().server("server").console().sendCommand(command);
	}

	/**
	 * Runs one of the fixture's console commands, such as {@code anvil-entity}, for a player.
	 *
	 * @param command the fixture command
	 * @param player the player it acts on
	 * @param arguments the command's arguments after the player's name
	 */
	public void fixture(@NotNull String command, @NotNull SimulatedPlayer player, @NotNull String arguments) {
		console(command + " " + player.name() + " " + arguments);
	}

	/**
	 * Waits for a chat message the fixture sends a player and returns what follows the prefix.
	 *
	 * @param player the player
	 * @param prefix the message's prefix
	 * @return the rest of the message
	 */
	public @NotNull String reply(@NotNull SimulatedPlayer player, @NotNull String prefix) {
		return player.capability(Messages.class).received(prefix, TIMEOUT).substring(prefix.length());
	}
}
