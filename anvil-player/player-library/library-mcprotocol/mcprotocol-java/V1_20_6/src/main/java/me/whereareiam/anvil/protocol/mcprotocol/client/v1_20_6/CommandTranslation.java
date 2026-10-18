package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.command.CommandNode;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.command.ClientboundCommands;
import me.whereareiam.anvil.protocol.java.api.type.CommandNodeType;
import me.whereareiam.anvil.protocol.java.api.type.StringArgumentMode;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandType;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.CommandProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.DoubleProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.FloatProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.IntegerProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.LongProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.StringProperties;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCommandsPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Commands of the MCProtocolLib releases from 1.20.5 on, whose trees keep their shape; each release's
 * {@code CommandPackets} tells whether a node needs permissions, which Minecraft 1.21.6 added. A parser's ID is its
 * vanilla registry key, which the library's constant names: in the {@code brigadier} namespace for the Brigadier types
 * and in the {@code minecraft} namespace for the others.
 */
public abstract class CommandTranslation implements PacketTranslation<Packet> {
	/**
	 * Namespace of the parsers Brigadier itself defines.
	 */
	private static final String BRIGADIER_NAMESPACE = "brigadier";
	private static final Set<CommandParser> BRIGADIER = Set.of(
			CommandParser.BOOL,
			CommandParser.FLOAT,
			CommandParser.DOUBLE,
			CommandParser.INTEGER,
			CommandParser.LONG,
			CommandParser.STRING
	);

	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundCommands.class);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (!(packet instanceof ClientboundCommandsPacket commands)) return null;

		List<CommandNode> nodes = new ArrayList<>();
		for (var node : commands.getNodes()) nodes.add(node(node));

		return new ClientboundCommands(nodes, commands.getFirstNodeIndex());
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Tells whether a node needs permissions, so the client asks before it runs a command using it on its own.
	 *
	 * @param node the library's node
	 * @return whether the node is marked as needing permissions
	 */
	protected abstract boolean restricted(
			@NotNull org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode node
	);

	private CommandNode node(org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode node) {
		CommandNode.CommandNodeBuilder builder = CommandNode.builder()
				.type(type(node.getType()))
				.name(node.getName())
				.redirect(node.getRedirectIndex().isPresent() ? node.getRedirectIndex().getAsInt() : null)
				.executable(node.isExecutable())
				.restricted(restricted(node))
				.parser(parser(node.getParser()))
				.suggestions(suggestions(node.getSuggestionType()));
		for (int child : node.getChildIndices()) builder.child(child);
		properties(builder, node.getProperties());

		return builder.build();
	}

	private static @Nullable String parser(@Nullable CommandParser parser) {
		if (parser == null) return null;

		String namespace = BRIGADIER.contains(parser) ? BRIGADIER_NAMESPACE : Key.MINECRAFT_NAMESPACE;
		return Key.key(namespace, parser.name().toLowerCase(Locale.ROOT)).asString();
	}

	private static @Nullable String suggestions(@Nullable Key suggestions) {
		return suggestions == null ? null : suggestions.asString();
	}

	private static void properties(CommandNode.CommandNodeBuilder builder, CommandProperties properties) {
		switch (properties) {
			case StringProperties mode -> builder.stringMode(stringMode(mode));
			case IntegerProperties range -> builder.min((double) range.getMin()).max((double) range.getMax());
			case LongProperties range -> builder.min((double) range.getMin()).max((double) range.getMax());
			case FloatProperties range -> builder.min((double) range.getMin()).max((double) range.getMax());
			case DoubleProperties range -> builder.min(range.getMin()).max(range.getMax());
			case null, default -> {
			}
		}
	}

	private static CommandNodeType type(CommandType type) {
		return switch (type) {
			case ROOT -> CommandNodeType.ROOT;
			case LITERAL -> CommandNodeType.LITERAL;
			case ARGUMENT -> CommandNodeType.ARGUMENT;
		};
	}

	private static StringArgumentMode stringMode(StringProperties mode) {
		return switch (mode) {
			case SINGLE_WORD -> StringArgumentMode.SINGLE_WORD;
			case QUOTABLE_PHRASE -> StringArgumentMode.QUOTABLE_PHRASE;
			case GREEDY_PHRASE -> StringArgumentMode.GREEDY_PHRASE;
		};
	}
}
