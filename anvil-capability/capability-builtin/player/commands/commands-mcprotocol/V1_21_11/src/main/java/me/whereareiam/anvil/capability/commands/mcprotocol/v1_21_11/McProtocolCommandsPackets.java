package me.whereareiam.anvil.capability.commands.mcprotocol.v1_21_11;

import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.commands.model.CommandTreeNode;
import me.whereareiam.anvil.capability.commands.packet.CommandsPackets;
import me.whereareiam.anvil.capability.commands.type.CommandNodeType;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.CommandProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.DoubleProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.FloatProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.IntegerProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.LongProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.StringProperties;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCommandsPacket;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Commands packets of the MCProtocolLib releases from Minecraft 1.21.11, whose nodes carry the flag that marks a
 * node as needing permissions; MCProtocolLib reads it as {@code allowsRestricted}. A parser's ID is its vanilla ID:
 * {@code brigadier:} for the Brigadier types and {@code minecraft:} for the others.
 */
public final class McProtocolCommandsPackets implements CommandsPackets<Session> {
	private static final Set<CommandParser> BRIGADIER = Set.of(
			CommandParser.BOOL, CommandParser.FLOAT, CommandParser.DOUBLE, CommandParser.INTEGER, CommandParser.LONG, CommandParser.STRING
	);

	@Override
	public @NotNull Class<Session> sessionType() {
		return Session.class;
	}

	@Override
	public @NotNull Runnable listen(@NotNull Session session, @NotNull Consumer<CommandTree> trees) {
		SessionListener listener = new SessionAdapter() {
			@Override
			public void packetReceived(Session received, Packet packet) {
				if (session.isConnected() && packet instanceof ClientboundCommandsPacket commands) trees.accept(tree(commands));
			}
		};
		session.addListener(listener);
		return () -> session.removeListener(listener);
	}

	private static CommandTree tree(ClientboundCommandsPacket packet) {
		List<CommandTreeNode> nodes = new ArrayList<>();
		for (CommandNode node : packet.getNodes()) nodes.add(node(node));
		return new CommandTree(nodes, packet.getFirstNodeIndex());
	}

	private static CommandTreeNode node(CommandNode node) {
		CommandTreeNode.CommandTreeNodeBuilder builder = CommandTreeNode.builder()
				.type(CommandNodeType.valueOf(node.getType().name()))
				.name(node.getName() == null ? "" : node.getName())
				.redirect(node.getRedirectIndex().isPresent() ? node.getRedirectIndex().getAsInt() : null)
				.executable(node.isExecutable())
				.restricted(node.isAllowsRestricted())
				.parser(parser(node.getParser()));
		for (int child : node.getChildIndices()) builder.child(child);
		properties(builder, node.getProperties());
		return builder.build();
	}

	private static String parser(CommandParser parser) {
		if (parser == null) return "";
		return (BRIGADIER.contains(parser) ? "brigadier:" : "minecraft:") + parser.name().toLowerCase(Locale.ROOT);
	}

	private static void properties(CommandTreeNode.CommandTreeNodeBuilder builder, CommandProperties properties) {
		switch (properties) {
			case StringProperties mode -> builder.stringMode(mode.name());
			case IntegerProperties range -> builder.min((double) range.getMin()).max((double) range.getMax());
			case LongProperties range -> builder.min((double) range.getMin()).max((double) range.getMax());
			case FloatProperties range -> builder.min((double) range.getMin()).max((double) range.getMax());
			case DoubleProperties range -> builder.min(range.getMin()).max(range.getMax());
			case null, default -> {
			}
		}
	}
}
