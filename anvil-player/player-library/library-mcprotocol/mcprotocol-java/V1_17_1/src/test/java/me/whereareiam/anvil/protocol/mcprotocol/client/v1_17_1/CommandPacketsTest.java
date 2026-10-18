package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.command.CommandNode;
import com.github.steveice10.mc.protocol.data.game.command.CommandParser;
import com.github.steveice10.mc.protocol.data.game.command.CommandType;
import com.github.steveice10.mc.protocol.data.game.command.properties.LongProperties;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerDeclareCommandsPacket;
import me.whereareiam.anvil.protocol.java.api.packet.command.ClientboundCommands;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CommandPacketsTest {
	private final CommandPackets packets = new CommandPackets();

	@Test
	void decodesTheLongArgumentAsABrigadierParserWithItsRange() {
		ServerDeclareCommandsPacket declared = new ServerDeclareCommandsPacket(new CommandNode[]{
				new CommandNode(CommandType.ROOT, false, new int[]{1}, -1, null, null, null, null),
				new CommandNode(CommandType.ARGUMENT, true, new int[0], -1, "ticks", CommandParser.LONG,
						new LongProperties(0L, 100L), null)
		}, 0);

		ClientboundCommands tree = assertInstanceOf(ClientboundCommands.class, packets.decode(declared));

		assertEquals("brigadier:long", tree.getNodes().get(1).getParser());
		assertEquals(0D, tree.getNodes().get(1).getMin());
		assertEquals(100D, tree.getNodes().get(1).getMax());
	}
}
