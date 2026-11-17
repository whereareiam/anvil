package me.whereareiam.anvil.testkit.tests.server.fixture;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.dispatch.Dispatch;
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
		return join(PlayerOptions.DEFAULT_LANGUAGE);
	}

	/**
	 * Creates a player whose game is set to a language, with a name no earlier test of this run used, connects it and
	 * waits for the fixture's welcome.
	 *
	 * @param language the Minecraft language code of the player's game, such as {@code ru_ru}
	 * @return the connected player
	 */
	public @NotNull SimulatedPlayer join(@NotNull String language) {
		SimulatedPlayer player = connect(language);
		player.capability(Messages.class).received("anvil:welcome", TIMEOUT);

		return player;
	}

	/**
	 * Creates a player with a name no earlier test of this run used and connects it, without waiting for anything the
	 * server sends after the login.
	 *
	 * @return the connected player
	 */
	public @NotNull SimulatedPlayer connect() {
		return connect(PlayerOptions.DEFAULT_LANGUAGE);
	}

	private @NotNull SimulatedPlayer connect(@NotNull String language) {
		SimulatedPlayer player = anvil.players().create(PlayerOptions.builder()
				.name("Player" + PLAYERS.incrementAndGet())
				.language(language)
				.build());
		player.capability(Session.class).connect();
		player.capability(Session.class).connected(TIMEOUT);

		return player;
	}

	/**
	 * Runs a command as the server console and returns once the server ran it, so what the command changed, such as
	 * the player's dimension after a teleport, holds when the test's next step reaches the server. Typing into the
	 * console instead returns before the server reads the line.
	 *
	 * @param command the command, without a slash
	 * @throws IllegalStateException if the server did not accept the command
	 */
	public void console(@NotNull String command) {
		if (!anvil.processes().server("server").capability(Dispatch.class).execute(command))
			throw new IllegalStateException("The server did not accept the console command '" + command + "'");
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
		return player.capability(Messages.class).received(prefix, TIMEOUT).plain().substring(prefix.length());
	}
}
