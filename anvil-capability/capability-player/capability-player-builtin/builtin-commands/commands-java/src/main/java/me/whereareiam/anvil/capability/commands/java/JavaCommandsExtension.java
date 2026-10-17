package me.whereareiam.anvil.capability.commands.java;

import me.whereareiam.anvil.capability.commands.channel.CommandsChannel;
import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.commands.model.CommandTreeNode;
import me.whereareiam.anvil.capability.commands.type.CommandNodeType;
import me.whereareiam.anvil.capability.commands.type.StringArgumentMode;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.command.CommandNode;
import me.whereareiam.anvil.protocol.java.api.packet.command.ClientboundCommands;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Installs the built-in commands capability for players that speak the Java Edition protocol: it emits each command
 * tree the server declares to the player's current connection, and a reset whenever a new connection starts, which
 * holds no tree until the server sends one. Commands register no operations; the host checks commands against the
 * trees it receives.
 */
public final class JavaCommandsExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return CommandsChannel.ID;
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		JavaProtocol protocol = player.protocol(JavaProtocol.class);
		Subscription connections = player.bindConnection(() -> {
			player.emit(CommandsChannel.RESET, null);
			return () -> { };
		});
		Subscription binding = Subscription.all(
				protocol.listen(ClientboundCommands.class,
						commands -> player.emit(CommandsChannel.TREE, tree(commands))),
				connections
		);

		return binding::close;
	}

	private static CommandTree tree(ClientboundCommands commands) {
		List<CommandTreeNode> nodes = commands.getNodes().stream()
				.map(JavaCommandsExtension::node)
				.toList();

		return new CommandTree(nodes, commands.getRootIndex());
	}
	private static CommandTreeNode node(CommandNode node) {
		return CommandTreeNode.builder()
				.type(type(node))
				.name(node.getName())
				.children(node.getChildren())
				.redirect(node.getRedirect())
				.executable(node.isExecutable())
				.restricted(node.isRestricted())
				.parser(node.getParser())
				.stringMode(stringMode(node.getStringMode()))
				.min(node.getMin())
				.max(node.getMax())
				.build();
	}

	private static CommandNodeType type(CommandNode node) {
		return switch (node.getType()) {
			case ROOT -> CommandNodeType.ROOT;
			case LITERAL -> CommandNodeType.LITERAL;
			case ARGUMENT -> CommandNodeType.ARGUMENT;
		};
	}

	private static @Nullable StringArgumentMode stringMode(
			me.whereareiam.anvil.protocol.java.api.type.@Nullable StringArgumentMode mode
	) {
		if (mode == null) return null;

		return switch (mode) {
			case SINGLE_WORD -> StringArgumentMode.SINGLE_WORD;
			case QUOTABLE_PHRASE -> StringArgumentMode.QUOTABLE_PHRASE;
			case GREEDY_PHRASE -> StringArgumentMode.GREEDY_PHRASE;
		};
	}
}
