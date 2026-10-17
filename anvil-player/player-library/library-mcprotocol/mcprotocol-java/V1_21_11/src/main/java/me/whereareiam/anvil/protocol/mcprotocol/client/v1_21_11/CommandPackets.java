package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

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
 * Commands of the MCProtocolLib 1.21.11 release, which also serves 26.1.2. Its nodes carry the flag that marks a node
 * as needing permissions, which MCProtocolLib reads as {@code allowsRestricted}. A parser's ID is its vanilla ID:
 * {@code brigadier:} for the Brigadier types and {@code minecraft:} for the others.
 */
public final class CommandPackets implements PacketTranslation<Packet> {
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

	private static CommandNode node(org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode node) {
		CommandNode.CommandNodeBuilder builder = CommandNode.builder()
				.type(CommandNodeType.valueOf(node.getType().name()))
				.name(node.getName())
				.redirect(node.getRedirectIndex().isPresent() ? node.getRedirectIndex().getAsInt() : null)
				.executable(node.isExecutable())
				.restricted(node.isAllowsRestricted())
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

	private static @Nullable String suggestions(@Nullable Key suggestions) {
		return suggestions == null ? null : suggestions.asString();
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
