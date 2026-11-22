package me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.player.HandPreference;
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility;
import com.github.steveice10.mc.protocol.data.game.setting.SkinPart;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientSettingsPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * The client information a client of the MCProtocolLib 1.16.5 release sends when the game starts: the player's language
 * and view distance, full chat with colours, every skin part and the right hand.
 */
final class ReleaseClientInformation implements ClientInformation {
	private static final boolean CHAT_COLORS = true;

	@Override
	public @NotNull Packet packet(@NotNull ClientSettings settings) {
		return new ClientSettingsPacket(settings.getLocale(), settings.getViewDistance(), ChatVisibility.FULL,
				CHAT_COLORS, Arrays.asList(SkinPart.values()), HandPreference.RIGHT_HAND);
	}
}
