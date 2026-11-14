package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConfigurationStart;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ParticleStatus;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * The configuration start of the MCProtocolLib releases from 1.21.11 on. Their listener answers the login's success,
 * which these releases call the login finished, with the acknowledgement and switches the session to the configuration
 * phase once it is written, so the configuration starts for that same packet. Their client information also names the
 * particles the client shows, all of them for a vanilla client by default. MCProtocolLib answers keep-alives in both
 * phases itself.
 */
final class ClientConfiguration extends ConfigurationStart {
	ClientConfiguration(@NotNull ClientSettings settings) {
		super(settings);
	}

	@Override
	protected boolean startsAfterReceiving(@NotNull Packet packet) {
		return packet instanceof ClientboundLoginFinishedPacket;
	}

	@Override
	protected @NotNull Packet information(@NotNull ClientSettings settings) {
		return new ServerboundClientInformationPacket(settings.getLocale(), settings.getViewDistance(), ChatVisibility.FULL,
				CHAT_COLORS, Arrays.asList(SkinPart.values()), HandPreference.RIGHT_HAND, TEXT_FILTERING, SERVER_LISTING,
				ParticleStatus.ALL);
	}
}
