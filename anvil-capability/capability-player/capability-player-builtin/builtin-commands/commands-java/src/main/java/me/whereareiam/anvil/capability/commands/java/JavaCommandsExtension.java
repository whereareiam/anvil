package me.whereareiam.anvil.capability.commands.java;

import me.whereareiam.anvil.capability.commands.channel.CommandsChannel;
import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.commands.model.CommandTreeNode;
import me.whereareiam.anvil.capability.commands.type.CommandNodeType;
import me.whereareiam.anvil.capability.commands.type.StringArgumentMode;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.edition.java.protocol.model.command.CommandNode;
import me.whereareiam.anvil.edition.java.protocol.packet.command.ClientboundCommands;
import me.whereareiam.anvil.player.api.channel.Subscription;
import me.whereareiam.anvil.player.api.worker.OperationRegistry;
import me.whereareiam.anvil.player.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.player.api.worker.WorkerBinding;
import me.whereareiam.anvil.player.api.worker.WorkerExtension;
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
		List<CommandTreeNode> nodes = commands.nodes().stream()
				.map(JavaCommandsExtension::node)
				.toList();

		return new CommandTree(nodes, commands.rootIndex());
	}
	private static CommandTreeNode node(CommandNode node) {
		return CommandTreeNode.builder()
				.type(type(node))
				.name(node.name())
				.children(node.children())
				.redirect(node.redirect())
				.executable(node.executable())
				.restricted(node.restricted())
				.parser(node.parser())
				.stringMode(stringMode(node.stringMode()))
				.min(node.min())
				.max(node.max())
				.build();
	}

	private static CommandNodeType type(CommandNode node) {
		return switch (node.type()) {
			case ROOT -> CommandNodeType.ROOT;
			case LITERAL -> CommandNodeType.LITERAL;
			case ARGUMENT -> CommandNodeType.ARGUMENT;
		};
	}

	private static @Nullable StringArgumentMode stringMode(
			me.whereareiam.anvil.edition.java.protocol.type.chat.@Nullable StringArgumentMode mode
	) {
		if (mode == null) return null;

		return switch (mode) {
			case SINGLE_WORD -> StringArgumentMode.SINGLE_WORD;
			case QUOTABLE_PHRASE -> StringArgumentMode.QUOTABLE_PHRASE;
			case GREEDY_PHRASE -> StringArgumentMode.GREEDY_PHRASE;
		};
	}
}
