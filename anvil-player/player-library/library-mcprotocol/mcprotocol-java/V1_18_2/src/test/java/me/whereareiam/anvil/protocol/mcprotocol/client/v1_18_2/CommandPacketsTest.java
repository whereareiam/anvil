package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.command.CommandParser;
import com.github.steveice10.mc.protocol.data.game.command.CommandType;
import com.github.steveice10.mc.protocol.data.game.command.SuggestionType;
import com.github.steveice10.mc.protocol.data.game.command.properties.CommandProperties;
import com.github.steveice10.mc.protocol.data.game.command.properties.IntegerProperties;
import com.github.steveice10.mc.protocol.data.game.command.properties.StringProperties;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundCommandsPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import me.whereareiam.anvil.protocol.java.api.model.command.CommandNode;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.command.ClientboundCommands;
import me.whereareiam.anvil.protocol.java.api.type.CommandNodeType;
import me.whereareiam.anvil.protocol.java.api.type.StringArgumentMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandPacketsTest {
	private final CommandPackets packets = new CommandPackets();

	@Test
	void decodesTheCommandTreeWithVanillaIdsAndNoPermissionFlag() {
		ClientboundCommandsPacket declared = declared(
				node(CommandType.ROOT, false, new int[]{1}, -1, null, null, null, null),
				node(CommandType.LITERAL, false, new int[]{2}, -1, "give", null, null, null),
				node(CommandType.ARGUMENT, true, new int[0], 0, "amount", CommandParser.INTEGER, new IntegerProperties(1, 64),
						SuggestionType.ASK_SERVER),
				node(CommandType.ARGUMENT, true, new int[0], -1, "text", CommandParser.STRING, StringProperties.GREEDY_PHRASE, null),
				node(CommandType.ARGUMENT, true, new int[0], -1, "message", CommandParser.MESSAGE, null, null)
		);

		ClientboundCommands tree = assertInstanceOf(ClientboundCommands.class, packets.decode(declared));

		assertEquals(0, tree.getRootIndex());
		assertEquals(CommandNode.builder().type(CommandNodeType.ROOT).child(1).build(), tree.getNodes().get(0));
		assertEquals(CommandNode.builder().type(CommandNodeType.LITERAL).name("give").child(2).build(), tree.getNodes().get(1));
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
	 * Builds a node of the release, which marks a node without a redirect with the index {@code -1}.
	 */
	private static com.github.steveice10.mc.protocol.data.game.command.CommandNode node(
			CommandType type,
			boolean executable,
			int[] children,
			int redirect,
			String name,
			CommandParser parser,
			CommandProperties properties,
			SuggestionType suggestions
	) {
		return new com.github.steveice10.mc.protocol.data.game.command.CommandNode(type, executable, children, redirect, name, parser,
				properties, suggestions);
	}

	private static ClientboundCommandsPacket declared(com.github.steveice10.mc.protocol.data.game.command.CommandNode... nodes) {
		return new ClientboundCommandsPacket(nodes, 0);
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
