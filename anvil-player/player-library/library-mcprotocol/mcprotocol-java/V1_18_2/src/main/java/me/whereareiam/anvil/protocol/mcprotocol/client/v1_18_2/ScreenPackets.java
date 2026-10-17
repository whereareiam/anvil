package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.BossBarColor;
import com.github.steveice10.mc.protocol.data.game.BossBarDivision;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.title.ClientboundClearTitlesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.title.ClientboundSetActionBarTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.title.ClientboundSetSubtitleTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.title.ClientboundSetTitleTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.title.ClientboundSetTitlesAnimationPacket;
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
import me.whereareiam.anvil.protocol.mcprotocol.adventure.AdventureText;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Titles, action bars and boss bars of the MCProtocolLib 1.18.2 release. Titles, subtitles, title times and action
 * bars have packets of their own since Minecraft 1.17, and boss bars share one packet whose action says which part it
 * changes.
 */
final class ScreenPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
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
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ClientboundSetTitleTextPacket title)
			return new ClientboundSetTitleText(AdventureText.plain(title.getText()));
		if (packet instanceof ClientboundSetSubtitleTextPacket subtitle)
			return new ClientboundSetSubtitleText(AdventureText.plain(subtitle.getText()));
		if (packet instanceof ClientboundSetTitlesAnimationPacket times)
			return new ClientboundSetTitlesAnimation(times.getFadeIn(), times.getStay(), times.getFadeOut());
		if (packet instanceof ClientboundClearTitlesPacket clear) return new ClientboundClearTitles(clear.isResetTimes());
		if (packet instanceof ClientboundSetActionBarTextPacket actionBar)
			return new ClientboundSetActionBarText(AdventureText.plain(actionBar.getText()));
		if (!(packet instanceof ClientboundBossEventPacket bossEvent)) return null;

		return new ClientboundBossEvent(bossEvent.getUuid(), operation(bossEvent));
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	private static BossEventOperation operation(ClientboundBossEventPacket packet) {
		return switch (packet.getAction()) {
			case ADD -> BossEventAdd.builder()
					.name(AdventureText.plain(packet.getTitle()))
					.progress(packet.getHealth())
					.color(color(packet.getColor()))
					.overlay(overlay(packet.getDivision()))
					.darkenScreen(packet.isDarkenSky())
					.playMusic(packet.isPlayEndMusic())
					.createWorldFog(packet.isShowFog())
					.build();
			case REMOVE -> new BossEventRemove();
			case UPDATE_HEALTH -> new BossEventUpdateProgress(packet.getHealth());
			case UPDATE_TITLE -> new BossEventUpdateName(AdventureText.plain(packet.getTitle()));
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
