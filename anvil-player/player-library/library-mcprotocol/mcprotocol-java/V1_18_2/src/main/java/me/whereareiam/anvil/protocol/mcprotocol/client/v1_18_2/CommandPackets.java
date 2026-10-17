package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.command.CommandParser;
import com.github.steveice10.mc.protocol.data.game.command.SuggestionType;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Commands of Minecraft 1.18.2, whose nodes carry no permission flag and mark a node without a redirect with the
 * index {@code -1}. A parser's and a suggestion's ID is its vanilla ID: {@code brigadier:} for the Brigadier types
 * and {@code minecraft:} for the others.
 */
final class CommandPackets implements PacketTranslation<Packet> {
	private static final Set<CommandParser> BRIGADIER = Set.of(
			CommandParser.BOOL, CommandParser.FLOAT, CommandParser.DOUBLE, CommandParser.INTEGER, CommandParser.LONG, CommandParser.STRING
	);

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundCommands.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (!(packet instanceof ClientboundCommandsPacket commands)) return null;

		List<CommandNode> nodes = new ArrayList<>();
		for (var node : commands.getNodes()) nodes.add(node(node));
		return new ClientboundCommands(nodes, commands.getFirstNodeIndex());
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	private static CommandNode node(com.github.steveice10.mc.protocol.data.game.command.CommandNode node) {
		CommandNode.CommandNodeBuilder builder = CommandNode.builder()
				.type(CommandNodeType.valueOf(node.getType().name()))
				.name(node.getName())
				.redirect(node.getRedirectIndex() < 0 ? null : node.getRedirectIndex())
				.executable(node.isExecutable())
				.restricted(false)
				.parser(parser(node.getParser()))
				.suggestions(suggestions(node.getSuggestionType()));
		for (int child : node.getChildIndices()) builder.child(child);
		properties(builder, node.getProperties());
		return builder.build();
	}

	private static @Nullable String parser(@Nullable CommandParser parser) {
		if (parser == null) return null;

		return (BRIGADIER.contains(parser) ? "brigadier:" : "minecraft:") + parser.name().toLowerCase(Locale.ROOT);
	}

	private static @Nullable String suggestions(@Nullable SuggestionType suggestions) {
		if (suggestions == null) return null;

		return "minecraft:" + suggestions.name().toLowerCase(Locale.ROOT);
	}

	private static void properties(CommandNode.CommandNodeBuilder builder, CommandProperties properties) {
		switch (properties) {
			case StringProperties mode -> builder.stringMode(StringArgumentMode.valueOf(mode.name()));
			case IntegerProperties range -> builder.min((double) range.getMin()).max((double) range.getMax());
			case LongProperties range -> builder.min((double) range.getMin()).max((double) range.getMax());
			case FloatProperties range -> builder.min((double) range.getMin()).max((double) range.getMax());
			case DoubleProperties range -> builder.min(range.getMin()).max(range.getMax());
			case null, default -> {
			}
		}
	}
}
