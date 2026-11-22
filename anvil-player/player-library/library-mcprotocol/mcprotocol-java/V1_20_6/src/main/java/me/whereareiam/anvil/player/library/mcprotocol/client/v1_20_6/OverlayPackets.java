package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.edition.java.protocol.model.overlay.BossEventAdd;
import me.whereareiam.anvil.edition.java.protocol.model.overlay.BossEventOperation;
import me.whereareiam.anvil.edition.java.protocol.model.overlay.BossEventRemove;
import me.whereareiam.anvil.edition.java.protocol.model.overlay.BossEventUpdateName;
import me.whereareiam.anvil.edition.java.protocol.model.overlay.BossEventUpdateProgress;
import me.whereareiam.anvil.edition.java.protocol.model.overlay.BossEventUpdateProperties;
import me.whereareiam.anvil.edition.java.protocol.model.overlay.BossEventUpdateStyle;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundBossEvent;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundClearTitles;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundSetActionBarText;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundSetSubtitleText;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundSetTitleText;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundSetTitlesAnimation;
import me.whereareiam.anvil.edition.java.protocol.type.overlay.BossEventColor;
import me.whereareiam.anvil.edition.java.protocol.type.overlay.BossEventOverlay;
import me.whereareiam.anvil.player.library.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTranslation;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.BossBarColor;
import org.geysermc.mcprotocollib.protocol.data.game.BossBarDivision;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundClearTitlesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetActionBarTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetSubtitleTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetTitleTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetTitlesAnimationPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Titles, action bars and boss bars of the MCProtocolLib releases from 1.20.5 on, which keep their packets. Titles,
 * subtitles, title times and action bars have packets of their own, and boss bars share one packet whose action says
 * which part it changes.
 */
public final class OverlayPackets implements PacketTranslation<Packet> {
	private final ComponentTranslator text;

	/**
	 * Creates the overlay translation of the release.
	 *
	 * @param text translates the release's text components
	 */
	public OverlayPackets(@NotNull ComponentTranslator text) {
		this.text = text;
	}

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
			return new ClientboundSetTitleText(text.translate(title.getText()).plain());
		if (packet instanceof ClientboundSetSubtitleTextPacket subtitle)
			return new ClientboundSetSubtitleText(text.translate(subtitle.getText()).plain());
		if (packet instanceof ClientboundSetTitlesAnimationPacket times)
			return new ClientboundSetTitlesAnimation(times.getFadeIn(), times.getStay(), times.getFadeOut());
		if (packet instanceof ClientboundClearTitlesPacket clear) return new ClientboundClearTitles(clear.isResetTimes());
		if (packet instanceof ClientboundSetActionBarTextPacket actionBar)
			return new ClientboundSetActionBarText(text.translate(actionBar.getText()).plain());
		if (!(packet instanceof ClientboundBossEventPacket bossEvent)) return null;

		return new ClientboundBossEvent(bossEvent.getUuid(), operation(bossEvent));
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	private BossEventOperation operation(ClientboundBossEventPacket packet) {
		return switch (packet.getAction()) {
			case ADD -> BossEventAdd.builder()
					.name(text.translate(packet.getTitle()).plain())
					.progress(packet.getHealth())
					.color(color(packet.getColor()))
					.overlay(overlay(packet.getDivision()))
					.darkenScreen(packet.isDarkenSky())
					.playMusic(packet.isPlayEndMusic())
					.createWorldFog(packet.isShowFog())
					.build();
			case REMOVE -> new BossEventRemove();
			case UPDATE_HEALTH -> new BossEventUpdateProgress(packet.getHealth());
			case UPDATE_TITLE -> new BossEventUpdateName(text.translate(packet.getTitle()).plain());
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
