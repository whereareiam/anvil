package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundLoginAcknowledgedPacket;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * The configuration start of the MCProtocolLib 1.20.6 release, which switches the session to the configuration phase
 * once it has written the login acknowledgement, so the configuration starts after that packet was sent.
 */
final class ClientConfiguration extends ConfigurationStart {
	ClientConfiguration(@NotNull ClientSettings settings) {
		super(settings);
	}

	@Override
	protected boolean startsAfterSending(@NotNull Packet packet) {
		return packet instanceof ServerboundLoginAcknowledgedPacket;
	}

	@Override
	protected @NotNull Packet information(@NotNull ClientSettings settings) {
		return new ServerboundClientInformationPacket(settings.getLocale(), settings.getViewDistance(), ChatVisibility.FULL,
				CHAT_COLORS, Arrays.asList(SkinPart.values()), HandPreference.RIGHT_HAND, TEXT_FILTERING, SERVER_LISTING);
	}
}
