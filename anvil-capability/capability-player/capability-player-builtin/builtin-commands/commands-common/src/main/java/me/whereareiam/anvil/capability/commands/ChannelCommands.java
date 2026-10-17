package me.whereareiam.anvil.capability.commands;

import me.whereareiam.anvil.capability.commands.channel.CommandsChannel;
import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.commands.parse.CommandVerifier;
import me.whereareiam.anvil.capability.commands.type.CommandCheck;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Commands capability driven over a player's typed channel: it keeps the last command tree the worker reports for
 * the player's current connection and checks commands against it. The channel releases the event subscriptions
 * together with the player.
 */
final class ChannelCommands implements Commands {
	private final ProtocolChannel channel;
	private volatile @Nullable CommandTree tree;

	ChannelCommands(@NotNull ProtocolChannel channel) {
		this.channel = channel;
		channel.subscribe(CommandsChannel.TREE, received -> tree = received);
		channel.subscribe(CommandsChannel.RESET, ignored -> tree = null);
	}

	@Override
	public @NotNull Optional<CommandTree> tree() {
		return Optional.ofNullable(tree);
	}

	@Override
	public @NotNull CommandTree tree(@NotNull Predicate<CommandTree> matcher, @NotNull Duration timeout) {
		channel.await(() -> matching(matcher) != null, "receive a matching command tree", timeout);
		return Objects.requireNonNull(matching(matcher));
	}

	@Override
	public @NotNull CommandCheck check(@NotNull String command) {
		return CommandVerifier.check(tree, command.startsWith("/") ? command.substring(1) : command);
	}

	private @Nullable CommandTree matching(Predicate<CommandTree> matcher) {
		CommandTree current = tree;
		return current != null && matcher.test(current) ? current : null;
	}
}
