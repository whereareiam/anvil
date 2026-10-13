package me.whereareiam.anvil.capability.commands;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.commands.type.CommandCheck;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Observes the command tree the server sends a player, which tells the client the commands the player may run, and
 * checks commands against it as the client does.
 *
 * <p>The tree is the last one the player's current connection received; a reconnect starts without one until the
 * server sends it again.</p>
 *
 * <pre>{@code
 * Commands commands = alice.capability(Commands.class);
 * commands.tree(tree -> tree.literal("login").isPresent(), Duration.ofSeconds(10));
 * assertEquals(CommandCheck.NO_ISSUES, commands.check("login Secret123"));
 * }</pre>
 */
public interface Commands extends PlayerCapability {
	/**
	 * Returns the command tree the player holds now, without waiting.
	 *
	 * @return the last tree the server sent, or empty before it sent one
	 */
	@NotNull Optional<CommandTree> tree();

	/**
	 * Waits until the player holds a command tree that satisfies a condition, such as one with a command the server
	 * sends only after a login.
	 *
	 * @param matcher condition on the tree
	 * @param timeout maximum wait
	 * @return the matching tree
	 */
	@NotNull CommandTree tree(@NotNull Predicate<CommandTree> matcher, @NotNull Duration timeout);

	/**
	 * Checks a command as the client does before it runs one on its own, for a dialog button or a clicked message:
	 * it parses the command against the player's command tree as Brigadier does, once with every node and once
	 * without the nodes that need permissions. Without a tree, no command parses.
	 *
	 * <p>Literals, the Brigadier argument types and the {@code minecraft:message} argument are parsed exactly;
	 * coordinates as their two or three parts, entity selectors with their options, and any other argument as one
	 * word in which quotes and brackets may hold spaces, without checking that its text is valid for its type.</p>
	 *
	 * @param command the command, with or without its leading slash
	 * @return what the client finds
	 */
	@NotNull CommandCheck check(@NotNull String command);
}
