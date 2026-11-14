package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.type.ClientCommandAction;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.ComponentTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.jetbrains.annotations.NotNull;

/**
 * The player's health of the MCProtocolLib releases from 1.20.5 until 1.21.11, whose client commands keep their short
 * names.
 */
public final class HealthPackets extends HealthTranslation {
	/**
	 * Creates the translation.
	 *
	 * @param text translates the release's text components
	 */
	public HealthPackets(@NotNull ComponentTranslator text) {
		super(text);
	}

	@Override
	protected @NotNull ClientCommand command(@NotNull ClientCommandAction action) {
		return switch (action) {
			case PERFORM_RESPAWN -> ClientCommand.RESPAWN;
			case REQUEST_STATS -> ClientCommand.STATS;
		};
	}
}
