package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.entity.player.HandPreference;
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility;
import com.github.steveice10.mc.protocol.data.game.setting.SkinPart;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientPongPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientSettingsPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerPingPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.ClientAnswers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * What a client of the MCProtocolLib 1.17.1 release sends on its own: client information that tells whether the
 * client filters text, which it does not, and the answer to every ping, as a vanilla client gives. Containers confirm
 * nothing since Minecraft 1.17 introduced state IDs.
 */
final class ReleaseAnswers implements ClientAnswers {
	private static final boolean CHAT_COLORS = true;
	private static final boolean TEXT_FILTERING = false;

	@Override
	public @NotNull Packet clientInformation(@NotNull ClientSettings settings) {
		return new ClientSettingsPacket(settings.getLocale(), settings.getViewDistance(), ChatVisibility.FULL,
				CHAT_COLORS, Arrays.asList(SkinPart.values()), HandPreference.RIGHT_HAND, TEXT_FILTERING);
	}

	@Override
	public @Nullable Packet answer(@NotNull Packet packet) {
		return packet instanceof ServerPingPacket ping ? new ClientPongPacket(ping.getId()) : null;
	}
}
