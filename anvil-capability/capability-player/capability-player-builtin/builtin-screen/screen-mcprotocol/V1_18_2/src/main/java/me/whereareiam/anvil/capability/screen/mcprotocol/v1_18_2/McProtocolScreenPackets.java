package me.whereareiam.anvil.capability.screen.mcprotocol.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.BossBarColor;
import com.github.steveice10.mc.protocol.data.game.BossBarDivision;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.title.ClientboundClearTitlesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.title.ClientboundSetActionBarTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.title.ClientboundSetSubtitleTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.title.ClientboundSetTitleTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.title.ClientboundSetTitlesAnimationPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.event.session.SessionListener;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.capability.screen.model.BossBar;
import me.whereareiam.anvil.capability.screen.packet.BossBarPacketListener;
import me.whereareiam.anvil.capability.screen.packet.ScreenPackets;
import me.whereareiam.anvil.capability.screen.packet.TitlePacketListener;
import me.whereareiam.anvil.capability.screen.type.BossBarOverlay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Screen packets of the MCProtocolLib release for Minecraft 1.18.2, the first one with serverbound and clientbound
 * packet names. Titles, subtitles, title times and action bars have packets of their own since Minecraft 1.17, and
 * boss bars share one packet whose action says which part it changes. Listeners follow packetlib 2.1 and components
 * are adventure 4.9, which this release ships without a plain-text serializer.
 */
public final class McProtocolScreenPackets implements ScreenPackets<Session> {
	@Override
	public @NotNull Class<Session> sessionType() {
		return Session.class;
	}

	@Override
	public @NotNull Runnable listenTitles(@NotNull Session session, @NotNull TitlePacketListener listener) {
		return attach(session, new SessionAdapter() {
			@Override
			public void packetReceived(Session received, Packet packet) {
				if (!session.isConnected()) return;

				if (packet instanceof ClientboundSetTitleTextPacket title) listener.title(flatten(title.getText()));
				if (packet instanceof ClientboundSetSubtitleTextPacket subtitle) listener.subtitle(flatten(subtitle.getText()));
				if (packet instanceof ClientboundSetTitlesAnimationPacket times)
					listener.times(times.getFadeIn(), times.getStay(), times.getFadeOut());
				if (packet instanceof ClientboundClearTitlesPacket clear) listener.cleared(clear.isResetTimes());
			}
		});
	}

	@Override
	public @NotNull Runnable listenActionBars(@NotNull Session session, @NotNull Consumer<String> actionBars) {
		return attach(session, new SessionAdapter() {
			@Override
			public void packetReceived(Session received, Packet packet) {
				if (session.isConnected() && packet instanceof ClientboundSetActionBarTextPacket actionBar)
					actionBars.accept(flatten(actionBar.getText()));
			}
		});
	}

	@Override
	public @NotNull Runnable listenBossBars(@NotNull Session session, @NotNull BossBarPacketListener listener) {
		return attach(session, new SessionAdapter() {
			@Override
			public void packetReceived(Session received, Packet packet) {
				if (session.isConnected() && packet instanceof ClientboundBossEventPacket bossBar) report(bossBar, listener);
			}

			@Override
			public void disconnected(DisconnectedEvent event) {
				listener.closed();
			}
		});
	}

	private static Runnable attach(Session session, SessionListener listener) {
		session.addListener(listener);
		return () -> session.removeListener(listener);
	}

	private static void report(ClientboundBossEventPacket packet, BossBarPacketListener listener) {
		switch (packet.getAction()) {
			case ADD -> listener.added(BossBar.builder()
					.id(packet.getUuid())
					.text(flatten(packet.getTitle()))
					.progress(packet.getHealth())
					.color(color(packet.getColor()))
					.overlay(overlay(packet.getDivision()))
					.darkensSky(packet.isDarkenSky())
					.playsBossMusic(packet.isPlayEndMusic())
					.createsFog(packet.isShowFog())
					.build());
			case REMOVE -> listener.removed(packet.getUuid());
			case UPDATE_HEALTH -> listener.progress(packet.getUuid(), packet.getHealth());
			case UPDATE_TITLE -> listener.text(packet.getUuid(), flatten(packet.getTitle()));
			case UPDATE_STYLE -> listener.style(packet.getUuid(), color(packet.getColor()), overlay(packet.getDivision()));
			case UPDATE_FLAGS -> listener.flags(packet.getUuid(), packet.isDarkenSky(), packet.isPlayEndMusic(), packet.isShowFog());
		}
	}

	private static me.whereareiam.anvil.capability.screen.type.BossBarColor color(BossBarColor color) {
		return switch (color) {
			case PINK -> me.whereareiam.anvil.capability.screen.type.BossBarColor.PINK;
			case CYAN -> me.whereareiam.anvil.capability.screen.type.BossBarColor.BLUE;
			case RED -> me.whereareiam.anvil.capability.screen.type.BossBarColor.RED;
			case LIME -> me.whereareiam.anvil.capability.screen.type.BossBarColor.GREEN;
			case YELLOW -> me.whereareiam.anvil.capability.screen.type.BossBarColor.YELLOW;
			case PURPLE -> me.whereareiam.anvil.capability.screen.type.BossBarColor.PURPLE;
			case WHITE -> me.whereareiam.anvil.capability.screen.type.BossBarColor.WHITE;
		};
	}

	private static BossBarOverlay overlay(BossBarDivision division) {
		return switch (division) {
			case NONE -> BossBarOverlay.PROGRESS;
			case NOTCHES_6 -> BossBarOverlay.NOTCHED_6;
			case NOTCHES_10 -> BossBarOverlay.NOTCHED_10;
			case NOTCHES_12 -> BossBarOverlay.NOTCHED_12;
			case NOTCHES_20 -> BossBarOverlay.NOTCHED_20;
		};
	}

	private static String flatten(Component component) {
		StringBuilder text = new StringBuilder();
		append(component, text);
		return text.toString();
	}

	/**
	 * Appends the content of text parts, the key name of keybind parts and the pattern of selector parts and, for a
	 * translated part, its key followed by each argument after a space. Score and NBT parts contribute nothing.
	 */
	private static void append(Component component, StringBuilder text) {
		if (component instanceof TextComponent value) text.append(value.content());
		if (component instanceof KeybindComponent value) text.append(value.keybind());
		if (component instanceof SelectorComponent value) text.append(value.pattern());
		if (component instanceof TranslatableComponent value) {
			text.append(value.key());
			for (Component argument : value.args()) {
				text.append(' ');
				append(argument, text);
			}
		}
		for (Component child : component.children()) append(child, text);
	}
}
