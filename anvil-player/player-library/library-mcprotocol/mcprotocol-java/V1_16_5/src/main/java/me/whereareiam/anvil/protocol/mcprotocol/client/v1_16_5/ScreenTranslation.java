package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.BossBarColor;
import com.github.steveice10.mc.protocol.data.game.BossBarDivision;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerBossBarPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventAdd;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventOperation;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventRemove;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateName;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateProgress;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateProperties;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateStyle;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundBossEvent;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundClearTitles;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetActionBarText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetSubtitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitlesAnimation;
import me.whereareiam.anvil.protocol.java.api.type.BossEventColor;
import me.whereareiam.anvil.protocol.java.api.type.BossEventOverlay;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Titles, action bars and boss bars of the MCProtocolLib releases from 1.16.5 until 1.17.1. Boss bars share one packet
 * whose action says which part it changes; each release's {@code ScreenPackets} reads the titles, which share one
 * packet before Minecraft 1.17 and have packets of their own since.
 */
public abstract class ScreenTranslation implements PacketTranslation<Packet> {
	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundSetTitleText.class,
				ClientboundSetSubtitleText.class,
				ClientboundSetTitlesAnimation.class,
				ClientboundClearTitles.class,
				ClientboundSetActionBarText.class,
				ClientboundBossEvent.class
		);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (!(packet instanceof ServerBossBarPacket bossBar)) return decodeTitle(packet);

		return new ClientboundBossEvent(bossBar.getUuid(), operation(bossBar));
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Reads a packet that shows, times or clears a title or shows an action bar.
	 *
	 * @param packet a packet the release received
	 * @return the protocol's packet, or {@code null} when the packet concerns no title
	 */
	protected abstract @Nullable ClientboundPacket decodeTitle(@NotNull Packet packet);

	private static BossEventOperation operation(ServerBossBarPacket packet) {
		return switch (packet.getAction()) {
			case ADD -> BossEventAdd.builder()
					.name(ValueTranslator.plain(packet.getTitle()))
					.progress(packet.getHealth())
					.color(color(packet.getColor()))
					.overlay(overlay(packet.getDivision()))
					.darkenScreen(packet.isDarkenSky())
					.playMusic(packet.isPlayEndMusic())
					.createWorldFog(packet.isShowFog())
					.build();
			case REMOVE -> new BossEventRemove();
			case UPDATE_HEALTH -> new BossEventUpdateProgress(packet.getHealth());
			case UPDATE_TITLE -> new BossEventUpdateName(ValueTranslator.plain(packet.getTitle()));
			case UPDATE_STYLE -> new BossEventUpdateStyle(color(packet.getColor()), overlay(packet.getDivision()));
			case UPDATE_FLAGS ->
					new BossEventUpdateProperties(packet.isDarkenSky(), packet.isPlayEndMusic(), packet.isShowFog());
		};
	}

	/**
	 * Maps the library's colors, which name blue and green by their shades, to the game's.
	 */
	private static BossEventColor color(BossBarColor color) {
		return switch (color) {
			case PINK -> BossEventColor.PINK;
			case CYAN -> BossEventColor.BLUE;
			case RED -> BossEventColor.RED;
			case LIME -> BossEventColor.GREEN;
			case YELLOW -> BossEventColor.YELLOW;
			case PURPLE -> BossEventColor.PURPLE;
			case WHITE -> BossEventColor.WHITE;
		};
	}

	private static BossEventOverlay overlay(BossBarDivision division) {
		return switch (division) {
			case NONE -> BossEventOverlay.PROGRESS;
			case NOTCHES_6 -> BossEventOverlay.NOTCHED_6;
			case NOTCHES_10 -> BossEventOverlay.NOTCHED_10;
			case NOTCHES_12 -> BossEventOverlay.NOTCHED_12;
			case NOTCHES_20 -> BossEventOverlay.NOTCHED_20;
		};
	}
}
