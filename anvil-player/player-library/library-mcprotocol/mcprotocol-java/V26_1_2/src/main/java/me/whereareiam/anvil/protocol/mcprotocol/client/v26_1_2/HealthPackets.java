package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.protocol.java.api.type.ClientCommandAction;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.HealthTranslation;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.jetbrains.annotations.NotNull;

/**
 * The player's health of the MCProtocolLib releases from 26.1 on, which name the client commands as the game does.
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
			case PERFORM_RESPAWN -> ClientCommand.PERFORM_RESPAWN;
			case REQUEST_STATS -> ClientCommand.REQUEST_STATS;
		};
	}
}
