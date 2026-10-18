package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.command.CommandParser;
import com.github.steveice10.mc.protocol.data.game.command.properties.CommandProperties;
import com.github.steveice10.mc.protocol.data.game.command.properties.LongProperties;
import me.whereareiam.anvil.protocol.java.api.model.command.CommandNode;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.CommandTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Commands of the MCProtocolLib 1.17.1 release, whose Brigadier parsers include the long argument that Minecraft 1.17
 * added, with its range.
 */
final class CommandPackets extends CommandTranslation {
	CommandPackets() {
		super(Set.of(CommandParser.BOOL, CommandParser.FLOAT, CommandParser.DOUBLE, CommandParser.INTEGER,
				CommandParser.LONG, CommandParser.STRING));
	}

	@Override
	protected void properties(
			@NotNull CommandNode.CommandNodeBuilder builder,
			@Nullable CommandProperties properties
	) {
		if (properties instanceof LongProperties range) {
			builder.min((double) range.getMin()).max((double) range.getMax());
			return;
		}

		super.properties(builder, properties);
	}
}
