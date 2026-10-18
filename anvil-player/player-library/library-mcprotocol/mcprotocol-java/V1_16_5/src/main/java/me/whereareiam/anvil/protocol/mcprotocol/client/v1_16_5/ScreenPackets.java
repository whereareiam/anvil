package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.packet.ingame.server.ServerTitlePacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundClearTitles;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetActionBarText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetSubtitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitlesAnimation;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.AdventureText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Titles of the MCProtocolLib 1.16.5 release, which shows, times, clears and resets titles and shows action bars
 * through one title packet whose action says what it does; the translation reports each action as the packet
 * Minecraft 1.17 gave it. Clearing keeps the title times, resetting restores the default ones.
 */
final class ScreenPackets extends ScreenTranslation {
	private static final boolean KEEP_TIMES = false;
	private static final boolean RESET_TIMES = true;

	@Override
	protected @Nullable ClientboundPacket decodeTitle(@NotNull Packet packet) {
		if (!(packet instanceof ServerTitlePacket title)) return null;

		return switch (title.getAction()) {
			case TITLE -> new ClientboundSetTitleText(AdventureText.plain(title.getTitle()));
			case SUBTITLE -> new ClientboundSetSubtitleText(AdventureText.plain(title.getTitle()));
			case ACTION_BAR -> new ClientboundSetActionBarText(AdventureText.plain(title.getTitle()));
			case TIMES -> new ClientboundSetTitlesAnimation(title.getFadeIn(), title.getStay(), title.getFadeOut());
			case CLEAR -> new ClientboundClearTitles(KEEP_TIMES);
			case RESET -> new ClientboundClearTitles(RESET_TIMES);
		};
	}
}
