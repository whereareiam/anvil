package me.whereareiam.anvil.capability.sounds.mcprotocol.v1_21_1;

import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.capability.sounds.model.SoundPosition;
import me.whereareiam.anvil.capability.sounds.packet.SoundsPackets;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.CustomSound;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.SoundCategory;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSoundEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class McProtocolSoundsPacketsTest {
	private final McProtocolSoundsPackets packets = new McProtocolSoundsPackets();

	@Test
	void reportsBuiltInAndNamedSoundsByTheirNamespacedKey() {
		NativeSession session = new NativeSession();
		List<HeardSound> heard = new ArrayList<>();
		packets.listen(session.proxy, heard::add);

		session.receive(new ClientboundSoundPacket(BuiltinSound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYER, 1.5D, 64D, -2D, 1F, 2F, 0L));
		session.receive(new ClientboundKeepAlivePacket(7));
		session.receive(new ClientboundSoundPacket(new CustomSound("example:alert", false, 16F), SoundCategory.RECORD, 0D, 70D, 0D, 0.5F, 1F, 0L));

		assertEquals(List.of(
				HeardSound.builder().key("minecraft:entity.player.levelup")
						.category(me.whereareiam.anvil.capability.sounds.type.SoundCategory.PLAYERS)
						.volume(1F).pitch(2F).position(new SoundPosition(1.5D, 64D, -2D)).build(),
				HeardSound.builder().key("example:alert")
						.category(me.whereareiam.anvil.capability.sounds.type.SoundCategory.RECORDS)
						.volume(0.5F).pitch(1F).position(new SoundPosition(0D, 70D, 0D)).build()
		), heard);
	}

	@Test
	void reportsASoundFollowingAnEntityWithEveryCategoryMappedByName() {
		NativeSession session = new NativeSession();
		List<HeardSound> heard = new ArrayList<>();
		packets.listen(session.proxy, heard::add);

		for (SoundCategory category : SoundCategory.values())
			session.receive(new ClientboundSoundEntityPacket(new CustomSound("ui.toast.in", false, 16F), category, 42, 1F, 1F, 0L));

		assertEquals(List.of("MASTER", "MUSIC", "RECORDS", "WEATHER", "BLOCKS", "HOSTILE", "NEUTRAL", "PLAYERS", "AMBIENT", "VOICE"),
				heard.stream().map(sound -> sound.getCategory().name()).toList());
		assertEquals("minecraft:ui.toast.in", heard.getFirst().getKey());
		assertEquals(42, heard.getFirst().getEntityId());
		assertNull(heard.getFirst().getPosition());
	}

	@Test
	void reportsNothingWhileDisconnectedAndRemovesItsListenerWhenDetached() {
		NativeSession session = new NativeSession();
		List<HeardSound> heard = new ArrayList<>();
		Runnable detach = packets.listen(session.proxy, heard::add);
		assertEquals(1, session.listeners.size());

		session.connected = false;
		session.receive(new ClientboundSoundPacket(BuiltinSound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYER, 0D, 0D, 0D, 1F, 1F, 0L));
		detach.run();

		assertEquals(List.of(), heard);
		assertEquals(List.of(), session.listeners);
	}

	@Test
	void isTheOnlySoundsPortOfItsRelease() {
		List<Class<?>> ports = ServiceLoader.load(SoundsPackets.class).stream()
				.map(ServiceLoader.Provider::type)
				.<Class<?>>map(type -> type)
				.toList();

		assertEquals(List.of(McProtocolSoundsPackets.class), ports);
		assertEquals(Session.class, packets.sessionType());
	}

	/**
	 * A packetlib session that records listeners and delivers received packets synchronously.
	 */
	private static final class NativeSession {
		private final List<SessionListener> listeners = new ArrayList<>();
		private final Session proxy = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
				(instance, method, arguments) -> handle(method, arguments));
		private boolean connected = true;

		private void receive(Packet packet) {
			List.copyOf(listeners).forEach(listener -> listener.packetReceived(proxy, packet));
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
