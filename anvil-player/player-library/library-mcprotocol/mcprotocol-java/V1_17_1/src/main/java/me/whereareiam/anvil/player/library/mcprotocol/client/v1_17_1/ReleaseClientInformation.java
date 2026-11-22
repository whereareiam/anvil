package me.whereareiam.anvil.player.library.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.entity.player.HandPreference;
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility;
import com.github.steveice10.mc.protocol.data.game.setting.SkinPart;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientSettingsPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientSettings;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.ClientInformation;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * The client information a client of the MCProtocolLib 1.17.1 release sends when the game starts, which from Minecraft
 * 1.17 also tells whether the client filters text, which it does not.
 */
final class ReleaseClientInformation implements ClientInformation {
	private static final boolean CHAT_COLORS = true;
	private static final boolean TEXT_FILTERING = false;

	@Override
	public @NotNull Packet packet(@NotNull ClientSettings settings) {
		return new ClientSettingsPacket(settings.getLocale(), settings.getViewDistance(), ChatVisibility.FULL,
				CHAT_COLORS, Arrays.asList(SkinPart.values()), HandPreference.RIGHT_HAND, TEXT_FILTERING);
	}
}
