package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConfigurationStart;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundGameProfilePacket;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * The configuration start of the MCProtocolLib 1.21.1 release. MCProtocolLib 1.21 reports the login acknowledgement
 * written before it switches the session to the configuration phase, so a brand sent once the acknowledgement was
 * written would leave in the login phase, where the library refuses it. The release's listener answers the login's
 * success with the acknowledgement and switches the phase right after writing it, so the configuration starts for that
 * same packet, after the library's listener handled it. MCProtocolLib answers keep-alives in both phases itself.
 */
final class ClientConfiguration extends ConfigurationStart {
	ClientConfiguration(@NotNull ClientSettings settings) {
		super(settings);
	}

	@Override
	protected boolean startsAfterReceiving(@NotNull Packet packet) {
		return packet instanceof ClientboundGameProfilePacket;
	}

	@Override
	protected @NotNull Packet information(@NotNull ClientSettings settings) {
		return new ServerboundClientInformationPacket(settings.getLocale(), settings.getViewDistance(), ChatVisibility.FULL,
				CHAT_COLORS, Arrays.asList(SkinPart.values()), HandPreference.RIGHT_HAND, TEXT_FILTERING, SERVER_LISTING);
	}
}
