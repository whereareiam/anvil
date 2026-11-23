package me.whereareiam.anvil.player.library.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.data.game.command.CommandParser;
import com.github.steveice10.mc.protocol.data.game.command.CommandType;
import com.github.steveice10.mc.protocol.data.game.command.properties.CommandProperties;
import com.github.steveice10.mc.protocol.data.game.command.properties.IntegerProperties;
import com.github.steveice10.mc.protocol.data.game.command.properties.StringProperties;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundCommandsPacket;
import me.whereareiam.anvil.edition.java.protocol.model.command.CommandNode;
import me.whereareiam.anvil.edition.java.protocol.packet.command.ClientboundCommands;
import me.whereareiam.anvil.edition.java.protocol.type.chat.CommandNodeType;
import me.whereareiam.anvil.edition.java.protocol.type.chat.StringArgumentMode;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CommandPacketsTest {
	private final CommandPackets packets = new CommandPackets();

	@Test
	void decodesTheCommandTreeWithOptionalRedirectsAndSuggestionKeys() {
		ClientboundCommandsPacket declared = declared(
				node(CommandType.ROOT, false, new int[]{1}, OptionalInt.empty(), null, null, null, null),
				node(CommandType.LITERAL, false, new int[]{2}, OptionalInt.empty(), "give", null, null, null),
				node(CommandType.ARGUMENT, true, new int[0], OptionalInt.of(0), "amount", CommandParser.INTEGER,
						new IntegerProperties(1, 64), "minecraft:ask_server"),
				node(CommandType.ARGUMENT, true, new int[0], OptionalInt.empty(), "text", CommandParser.STRING,
						StringProperties.GREEDY_PHRASE, "summonable_entities")
		);

		ClientboundCommands tree = assertInstanceOf(ClientboundCommands.class, packets.decode(declared));

		assertEquals(0, tree.rootIndex());
		assertEquals(CommandNode.builder().type(CommandNodeType.ROOT).child(1).build(), tree.nodes().get(0));
		assertEquals(CommandNode.builder().type(CommandNodeType.LITERAL).name("give").child(2).build(),
				tree.nodes().get(1));
		assertEquals(CommandNode.builder()
				.type(CommandNodeType.ARGUMENT)
				.name("amount")
				.redirect(0)
				.executable(true)
				.parser("brigadier:integer")
				.min(1D)
				.max(64D)
				.suggestions("minecraft:ask_server")
				.build(), tree.nodes().get(2));
		assertEquals(StringArgumentMode.GREEDY_PHRASE, tree.nodes().get(3).stringMode());
		assertEquals("minecraft:summonable_entities", tree.nodes().get(3).suggestions());
	}

	@Test
	void carriesTheCommandTreeOfTheRelease() {
		assertEquals(Set.of(ClientboundCommands.class), packets.packets());
	}

	private static ClientboundCommandsPacket declared(
			com.github.steveice10.mc.protocol.data.game.command.CommandNode... nodes
	) {
		return new ClientboundCommandsPacket(nodes, 0);
	}

	private static com.github.steveice10.mc.protocol.data.game.command.CommandNode node(
			CommandType type,
			boolean executable,
			int[] children,
			OptionalInt redirect,
			String name,
			CommandParser parser,
			CommandProperties properties,
			String suggestions
	) {
		return new com.github.steveice10.mc.protocol.data.game.command.CommandNode(type, executable, children, redirect,
				name, parser, properties, suggestions);
	}
}
