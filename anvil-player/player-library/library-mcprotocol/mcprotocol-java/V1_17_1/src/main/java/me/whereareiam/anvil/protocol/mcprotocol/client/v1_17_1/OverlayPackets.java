package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.packet.ingame.server.title.ServerClearTitlesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.title.ServerSetActionBarTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.title.ServerSetSubtitleTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.title.ServerSetTitleTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.title.ServerSetTitlesAnimationPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.overlay.ClientboundClearTitles;
import me.whereareiam.anvil.protocol.java.api.packet.overlay.ClientboundSetActionBarText;
import me.whereareiam.anvil.protocol.java.api.packet.overlay.ClientboundSetSubtitleText;
import me.whereareiam.anvil.protocol.java.api.packet.overlay.ClientboundSetTitleText;
import me.whereareiam.anvil.protocol.java.api.packet.overlay.ClientboundSetTitlesAnimation;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.OverlayTranslation;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.ValueTranslator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Titles of the MCProtocolLib 1.17.1 release, whose titles, subtitles, title times and action bars have the packets of
 * their own that Minecraft 1.17 gave them.
 */
final class OverlayPackets extends OverlayTranslation {
	OverlayPackets(@NotNull ComponentTranslator text) {
		super(text);
	}

	@Override
	protected @Nullable ClientboundPacket decodeTitle(@NotNull Packet packet) {
		return switch (packet) {
			case ServerSetTitleTextPacket title -> new ClientboundSetTitleText(text.translate(title.getText()).plain());
			case ServerSetSubtitleTextPacket subtitle ->
					new ClientboundSetSubtitleText(text.translate(subtitle.getText()).plain());
			case ServerSetTitlesAnimationPacket times ->
					new ClientboundSetTitlesAnimation(times.getFadeIn(), times.getStay(), times.getFadeOut());
			case ServerClearTitlesPacket clear -> new ClientboundClearTitles(clear.isResetTimes());
			case ServerSetActionBarTextPacket actionBar ->
					new ClientboundSetActionBarText(text.translate(actionBar.getText()).plain());
			default -> null;
		};
	}
}
