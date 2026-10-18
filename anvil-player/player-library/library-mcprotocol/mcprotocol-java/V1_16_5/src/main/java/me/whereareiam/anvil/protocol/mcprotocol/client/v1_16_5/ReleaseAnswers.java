package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.player.HandPreference;
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility;
import com.github.steveice10.mc.protocol.data.game.setting.SkinPart;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientSettingsPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.window.ClientConfirmTransactionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerConfirmTransactionPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * What a client of the MCProtocolLib 1.16.5 release sends on its own. The server confirms every container click, and
 * after it rejects one, for example because the client did not predict the stack the click picked up, it ignores
 * further clicks in that container until the client acknowledges the rejection, which a vanilla client does at once.
 */
final class ReleaseAnswers implements ClientAnswers {
	/**
	 * Whether the client acknowledges a rejected transaction, which it always does.
	 */
	private static final boolean ACKNOWLEDGED = true;
	private static final boolean CHAT_COLORS = true;

	@Override
	public @NotNull Packet clientInformation(@NotNull ClientSettings settings) {
		return new ClientSettingsPacket(settings.getLocale(), settings.getViewDistance(), ChatVisibility.FULL,
				CHAT_COLORS, Arrays.asList(SkinPart.values()), HandPreference.RIGHT_HAND);
	}

	@Override
	public @Nullable Packet answer(@NotNull Packet packet) {
		if (!(packet instanceof ServerConfirmTransactionPacket confirmation) || confirmation.isAccepted()) return null;

		return new ClientConfirmTransactionPacket(confirmation.getWindowId(), confirmation.getActionId(), ACKNOWLEDGED);
	}
}
