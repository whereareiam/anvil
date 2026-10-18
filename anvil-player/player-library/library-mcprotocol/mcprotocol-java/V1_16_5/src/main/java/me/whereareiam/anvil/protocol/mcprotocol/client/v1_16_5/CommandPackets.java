package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.command.CommandParser;

import java.util.Set;

/**
 * Commands of the MCProtocolLib 1.16.5 release, whose Brigadier parsers have no long argument yet.
 */
final class CommandPackets extends CommandTranslation {
	CommandPackets() {
		super(Set.of(CommandParser.BOOL, CommandParser.FLOAT, CommandParser.DOUBLE, CommandParser.INTEGER,
				CommandParser.STRING));
	}
}
