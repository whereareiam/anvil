package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.command.CommandParser;
import com.github.steveice10.mc.protocol.data.game.command.CommandType;
import com.github.steveice10.mc.protocol.data.game.command.properties.CommandProperties;
import com.github.steveice10.mc.protocol.data.game.command.properties.DoubleProperties;
import com.github.steveice10.mc.protocol.data.game.command.properties.FloatProperties;
import com.github.steveice10.mc.protocol.data.game.command.properties.IntegerProperties;
import com.github.steveice10.mc.protocol.data.game.command.properties.LongProperties;
import com.github.steveice10.mc.protocol.data.game.command.properties.StringProperties;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundCommandsPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.command.CommandNode;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.command.ClientboundCommands;
import me.whereareiam.anvil.protocol.java.api.type.CommandNodeType;
import me.whereareiam.anvil.protocol.java.api.type.StringArgumentMode;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Commands of the MCProtocolLib releases from 1.18.2 until 1.19.4, whose nodes carry no permission flag. Each release's
 * {@code CommandPackets} reads a node's redirect and its suggestions, which MCProtocolLib 1.19 changed from a negative
 * index and a constant to an optional index and a key. A parser's ID is its vanilla registry key, which the library's
 * constant names: in the {@code brigadier} namespace for the Brigadier types and in the {@code minecraft} namespace for
 * the others.
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
	 * Reads the index of the node a node redirects to.
	 *
	 * @param node the library's node
	 * @return the index, or {@code null} when the node redirects nowhere
	 */
	protected abstract @Nullable Integer redirect(
			com.github.steveice10.mc.protocol.data.game.command.@NotNull CommandNode node
	);

	/**
	 * Reads the key of the suggestions the server offers for a node's argument.
	 *
	 * @param node the library's node
	 * @return the namespaced key, or {@code null} when the node asks for no suggestions
	 */
	protected abstract @Nullable String suggestions(
			com.github.steveice10.mc.protocol.data.game.command.@NotNull CommandNode node
	);

	private CommandNode node(com.github.steveice10.mc.protocol.data.game.command.CommandNode node) {
		CommandNode.CommandNodeBuilder builder = CommandNode.builder()
				.type(type(node.getType()))
				.name(node.getName())
				.redirect(redirect(node))
				.executable(node.isExecutable())
				.restricted(false)
				.parser(parser(node.getParser()))
				.suggestions(suggestions(node));
		for (int child : node.getChildIndices()) builder.child(child);
		properties(builder, node.getProperties());

		return builder.build();
	}

	private static @Nullable String parser(@Nullable CommandParser parser) {
		if (parser == null) return null;

		String namespace = BRIGADIER.contains(parser) ? BRIGADIER_NAMESPACE : Key.MINECRAFT_NAMESPACE;
		return Key.key(namespace, parser.name().toLowerCase(Locale.ROOT)).asString();
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
