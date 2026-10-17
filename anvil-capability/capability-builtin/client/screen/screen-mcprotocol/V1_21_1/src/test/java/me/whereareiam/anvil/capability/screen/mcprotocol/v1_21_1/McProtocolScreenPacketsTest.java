package me.whereareiam.anvil.capability.screen.mcprotocol.v1_21_1;

import me.whereareiam.anvil.capability.screen.model.BossBar;
import me.whereareiam.anvil.capability.screen.packet.BossBarPacketListener;
import me.whereareiam.anvil.capability.screen.packet.ScreenPackets;
import me.whereareiam.anvil.capability.screen.packet.TitlePacketListener;
import me.whereareiam.anvil.capability.screen.type.BossBarOverlay;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.BossBarColor;
import org.geysermc.mcprotocollib.protocol.data.game.BossBarDivision;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundClearTitlesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetActionBarTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetSubtitleTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetTitleTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetTitlesAnimationPacket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McProtocolScreenPacketsTest {
	private static final UUID BAR = new UUID(0, 1);

	private final McProtocolScreenPackets packets = new McProtocolScreenPackets();

	@Test
	void reportsTitlesSubtitlesTimesClearsAndActionBarsAsPlainText() {
		NativeSession session = new NativeSession();
		RecordingListener listener = new RecordingListener();
		listener.listen(packets, session.proxy);

		session.receive(new ClientboundSetTitlesAnimationPacket(5, 40, 5));
		session.receive(new ClientboundSetSubtitleTextPacket(Component.text("by ").append(Component.text("Alice"))));
		session.receive(new ClientboundSetTitleTextPacket(Component.translatable("example.announcement", Component.text("Alice"))));
		session.receive(new ClientboundKeepAlivePacket(7));
		session.receive(new ClientboundSetActionBarTextPacket(Component.text("press ").append(Component.keybind("key.jump"))));
		session.receive(new ClientboundClearTitlesPacket(true));

		assertEquals(List.of("times 5 40 5", "subtitle by Alice", "title example.announcement Alice", "action bar press key.jump",
				"cleared true"), listener.reported);
	}

	@Test
	void reportsEachBossBarActionWithTheVanillaColorAndOverlay() {
		NativeSession session = new NativeSession();
		RecordingListener listener = new RecordingListener();
		listener.listen(packets, session.proxy);

		session.receive(new ClientboundBossEventPacket(BAR, Component.text("Event"), 1F, BossBarColor.CYAN, BossBarDivision.NONE,
				false, true, false));
		session.receive(new ClientboundBossEventPacket(BAR, 0.5F));
		session.receive(new ClientboundBossEventPacket(BAR, Component.text("Event ends soon")));
		session.receive(new ClientboundBossEventPacket(BAR, BossBarColor.LIME, BossBarDivision.NOTCHES_12));
		session.receive(new ClientboundBossEventPacket(BAR, true, false, true));
		session.receive(new ClientboundBossEventPacket(BAR));

		assertEquals(List.of(
				"added " + BossBar.builder().id(BAR).text("Event").progress(1F)
						.color(me.whereareiam.anvil.capability.screen.type.BossBarColor.BLUE).overlay(BossBarOverlay.PROGRESS)
						.playsBossMusic(true).build(),
				"progress 0.5",
				"text Event ends soon",
				"style GREEN NOTCHED_12",
				"flags true false true",
				"removed"
		), listener.reported);
	}

	@Test
	void reportsNothingWhileDisconnectedReportsTheEndToBossBarsAndRemovesItsListenersWhenDetached() {
		NativeSession session = new NativeSession();
		RecordingListener listener = new RecordingListener();
		Runnable detach = listener.listen(packets, session.proxy);
		assertEquals(3, session.listeners.size());

		session.connected = false;
		session.receive(new ClientboundSetActionBarTextPacket(Component.text("late")));
		session.disconnect();
		detach.run();

		assertEquals(List.of("closed"), listener.reported);
		assertEquals(List.of(), session.listeners);
	}

	@Test
	void isTheOnlyScreenPortOfItsRelease() {
		List<Class<?>> ports = ServiceLoader.load(ScreenPackets.class).stream()
				.map(ServiceLoader.Provider::type)
				.<Class<?>>map(type -> type)
				.toList();

		assertEquals(List.of(McProtocolScreenPackets.class), ports);
		assertEquals(Session.class, packets.sessionType());
	}

	/**
	 * Records each packet reported to any of the three listeners as one line.
	 */
	private static final class RecordingListener implements TitlePacketListener, BossBarPacketListener {
		private final List<String> reported = new ArrayList<>();

		private Runnable listen(McProtocolScreenPackets packets, Session session) {
			Runnable titles = packets.listenTitles(session, this);
			Runnable actionBars = packets.listenActionBars(session, text -> reported.add("action bar " + text));
			Runnable bossBars = packets.listenBossBars(session, this);
			return () -> {
				titles.run();
				actionBars.run();
				bossBars.run();
			};
		}

		public void title(@NotNull String text) { reported.add("title " + text); }
		public void subtitle(@NotNull String text) { reported.add("subtitle " + text); }
		public void times(int fadeInTicks, int stayTicks, int fadeOutTicks) { reported.add("times " + fadeInTicks + " " + stayTicks + " " + fadeOutTicks); }
		public void cleared(boolean resetTimes) { reported.add("cleared " + resetTimes); }
		public void added(@NotNull BossBar bar) { reported.add("added " + bar); }
		public void removed(@NotNull UUID id) { reported.add("removed"); }
		public void progress(@NotNull UUID id, float progress) { reported.add("progress " + progress); }
		public void text(@NotNull UUID id, @NotNull String text) { reported.add("text " + text); }
		public void style(@NotNull UUID id, @NotNull me.whereareiam.anvil.capability.screen.type.BossBarColor color, @NotNull BossBarOverlay overlay) { reported.add("style " + color + " " + overlay); }
		public void flags(@NotNull UUID id, boolean darkensSky, boolean playsBossMusic, boolean createsFog) { reported.add("flags " + darkensSky + " " + playsBossMusic + " " + createsFog); }
		public void closed() { reported.add("closed"); }
	}

	/**
	 * A packetlib session that records listeners and delivers received packets and its disconnection synchronously.
	 */
	private static final class NativeSession {
		private final List<SessionListener> listeners = new ArrayList<>();
		private final Session proxy = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
				(instance, method, arguments) -> handle(method, arguments));
		private boolean connected = true;

		private void receive(Packet packet) {
			List.copyOf(listeners).forEach(listener -> listener.packetReceived(proxy, packet));
		}

		private void disconnect() {
			DisconnectedEvent event = new DisconnectedEvent(proxy, Component.text("Disconnected"), null);
			List.copyOf(listeners).forEach(listener -> listener.disconnected(event));
		}

		private Object handle(Method method, Object[] arguments) {
			switch (method.getName()) {
				case "addListener" -> listeners.add((SessionListener) arguments[0]);
				case "removeListener" -> listeners.remove((SessionListener) arguments[0]);
				case "isConnected" -> {
					return connected;
				}
				default -> throw new UnsupportedOperationException(method.getName());
			}
			return null;
		}
	}
}
