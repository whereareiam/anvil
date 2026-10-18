package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.packet.ingame.server.title.ServerClearTitlesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.title.ServerSetActionBarTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.title.ServerSetSubtitleTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.title.ServerSetTitleTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.title.ServerSetTitlesAnimationPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundClearTitles;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetActionBarText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetSubtitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitlesAnimation;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.ScreenTranslation;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.ValueTranslator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Titles of the MCProtocolLib 1.17.1 release, whose titles, subtitles, title times and action bars have the packets of
 * their own that Minecraft 1.17 gave them.
 */
final class ScreenPackets extends ScreenTranslation {
	@Override
	protected @Nullable ClientboundPacket decodeTitle(@NotNull Packet packet) {
		return switch (packet) {
			case ServerSetTitleTextPacket title -> new ClientboundSetTitleText(ValueTranslator.plain(title.getText()));
			case ServerSetSubtitleTextPacket subtitle ->
					new ClientboundSetSubtitleText(ValueTranslator.plain(subtitle.getText()));
			case ServerSetTitlesAnimationPacket times ->
					new ClientboundSetTitlesAnimation(times.getFadeIn(), times.getStay(), times.getFadeOut());
			case ServerClearTitlesPacket clear -> new ClientboundClearTitles(clear.isResetTimes());
			case ServerSetActionBarTextPacket actionBar ->
					new ClientboundSetActionBarText(ValueTranslator.plain(actionBar.getText()));
			default -> null;
		};
	}
}
