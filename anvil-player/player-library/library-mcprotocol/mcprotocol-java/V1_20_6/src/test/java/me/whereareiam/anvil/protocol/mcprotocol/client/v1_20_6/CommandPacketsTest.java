package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.command.CommandNode;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.command.ClientboundCommands;
import me.whereareiam.anvil.protocol.java.api.type.CommandNodeType;
import me.whereareiam.anvil.protocol.java.api.type.StringArgumentMode;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandType;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.CommandProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.IntegerProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.StringProperties;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCommandsPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandPacketsTest {
	private final CommandPackets packets = new CommandPackets();

	@Test
	void decodesTheCommandTreeWithVanillaIdsAndNoPermissionFlag() {
		ClientboundCommandsPacket declared = declared(
				node(CommandType.ROOT, false, new int[]{1}, OptionalInt.empty(), null, null, null, null),
				node(CommandType.LITERAL, false, new int[]{2}, OptionalInt.empty(), "give", null, null, null),
				node(CommandType.ARGUMENT, true, new int[0], OptionalInt.of(0), "amount", CommandParser.INTEGER,
						new IntegerProperties(1, 64), Key.key("minecraft", "ask_server")),
				node(CommandType.ARGUMENT, true, new int[0], OptionalInt.empty(), "text", CommandParser.STRING,
						StringProperties.GREEDY_PHRASE, null),
				node(CommandType.ARGUMENT, true, new int[0], OptionalInt.empty(), "message", CommandParser.MESSAGE, null, null)
		);

		ClientboundCommands tree = assertInstanceOf(ClientboundCommands.class, packets.decode(declared));

		assertEquals(0, tree.getRootIndex());
		assertEquals(CommandNode.builder().type(CommandNodeType.ROOT).child(1).build(), tree.getNodes().get(0));
		assertEquals(CommandNode.builder().type(CommandNodeType.LITERAL).name("give").child(2).restricted(false).build(),
				tree.getNodes().get(1));
		assertEquals(CommandNode.builder()
				.type(CommandNodeType.ARGUMENT)
				.name("amount")
				.redirect(0)
				.executable(true)
				.parser("brigadier:integer")
				.min(1D)
				.max(64D)
				.suggestions("minecraft:ask_server")
				.build(), tree.getNodes().get(2));
		assertEquals(StringArgumentMode.GREEDY_PHRASE, tree.getNodes().get(3).getStringMode());
		assertEquals("brigadier:string", tree.getNodes().get(3).getParser());
		assertEquals("minecraft:message", tree.getNodes().get(4).getParser());
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesTheCommandTreeOfTheRelease() {
		assertEquals(Set.of(ClientboundCommands.class), packets.packets());
		assertEquals(List.of(), assertInstanceOf(ClientboundCommands.class, packets.decode(declared())).getNodes());
	}

	/**
	 * Builds a node of the release, which carries no permission flag.
	 */
	private static org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode node(
			CommandType type,
			boolean executable,
			int[] children,
			OptionalInt redirect,
			String name,
			CommandParser parser,
			CommandProperties properties,
			Key suggestions
	) {
		return new org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode(type, executable, children, redirect, name, parser,
				properties, suggestions);
	}

	private static ClientboundCommandsPacket declared(org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode... nodes) {
		return new ClientboundCommandsPacket(nodes, 0);
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
