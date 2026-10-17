package me.whereareiam.anvil.capability.sounds.mcprotocol.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.level.sound.BuiltinSound;
import com.github.steveice10.mc.protocol.data.game.level.sound.CustomSound;
import com.github.steveice10.mc.protocol.data.game.level.sound.SoundCategory;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundSoundEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundCustomSoundPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.SessionListener;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.capability.sounds.model.SoundPosition;
import me.whereareiam.anvil.capability.sounds.packet.SoundsPackets;
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
	void reportsBuiltInAndNamedSoundsAtAPositionByTheirNamespacedKey() {
		NativeSession session = new NativeSession();
		List<HeardSound> heard = new ArrayList<>();
		packets.listen(session.proxy, heard::add);

		session.receive(new ClientboundSoundPacket(BuiltinSound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYER, 1.5D, 64D, -2D, 1F, 2F));
		session.receive(new ClientboundKeepAlivePacket(7));
		session.receive(new ClientboundCustomSoundPacket(new CustomSound("example:alert"), SoundCategory.RECORD, 0D, 70D, 0D, 0.5F, 1F));
		session.receive(new ClientboundCustomSoundPacket(new CustomSound("ui.toast.in"), SoundCategory.MASTER, 0D, 70D, 0D, 1F, 1F));

		assertEquals(List.of(
				positioned("minecraft:entity.player.levelup", me.whereareiam.anvil.capability.sounds.type.SoundCategory.PLAYERS, 1F, 2F,
						new SoundPosition(1.5D, 64D, -2D)),
				positioned("example:alert", me.whereareiam.anvil.capability.sounds.type.SoundCategory.RECORDS, 0.5F, 1F,
						new SoundPosition(0D, 70D, 0D)),
				positioned("minecraft:ui.toast.in", me.whereareiam.anvil.capability.sounds.type.SoundCategory.MASTER, 1F, 1F,
						new SoundPosition(0D, 70D, 0D))
		), heard);
	}

	@Test
	void resolvesTheRegistryIdOfASoundFollowingAnEntity() {
		NativeSession session = new NativeSession();
		List<HeardSound> heard = new ArrayList<>();
		packets.listen(session.proxy, heard::add);

		session.receive(new ClientboundSoundEntityPacket(BuiltinSound.BLOCK_NOTE_BLOCK_PLING.ordinal(), SoundCategory.BLOCK, 42, 1F, 1F));
		session.receive(new ClientboundSoundEntityPacket(BuiltinSound.VALUES.length, SoundCategory.HOSTILE, 42, 1F, 1F));

		assertEquals(List.of("minecraft:block.note_block.pling", Integer.toString(BuiltinSound.VALUES.length)),
				heard.stream().map(HeardSound::getKey).toList());
		assertEquals(me.whereareiam.anvil.capability.sounds.type.SoundCategory.BLOCKS, heard.getFirst().getCategory());
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
		session.receive(new ClientboundSoundPacket(BuiltinSound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYER, 0D, 0D, 0D, 1F, 1F));
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

	private static HeardSound positioned(
			String key,
			me.whereareiam.anvil.capability.sounds.type.SoundCategory category,
			float volume,
			float pitch,
			SoundPosition position
	) {
		return HeardSound.builder().key(key).category(category).volume(volume).pitch(pitch).position(position).build();
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
