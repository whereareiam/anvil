package me.whereareiam.anvil.capability.dialogs.mcprotocol.v1_21_11;

import me.whereareiam.anvil.capability.dialogs.packet.DialogsPacketListener;
import me.whereareiam.anvil.capability.dialogs.packet.DialogsPackets;
import me.whereareiam.anvil.capability.dialogs.packet.RegistryDialogEntry;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundClearDialogPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomClickActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundShowDialogConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundShowDialogGamePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class McProtocolDialogsPacketsTest {
	private static final NbtMap NOTICE = NbtMap.builder().putString("type", "minecraft:notice").putString("title", "Terms").build();

	private final McProtocolDialogsPackets packets = new McProtocolDialogsPackets();
	private final NativeSession session = new NativeSession();
	private final List<String> events = new ArrayList<>();
	private final List<Object> reported = new ArrayList<>();

	@Test
	void reportsTheCompoundOfADialogShownInFullAndTheIdOfARegisteredOne() {
		packets.listen(session.proxy, recording());

		session.receive(new ClientboundShowDialogGamePacket(Holder.ofCustom(NOTICE)));
		session.receive(new ClientboundShowDialogGamePacket(Holder.ofId(3)));

		assertEquals(List.of("shown in the game", "shown from registry 3"), events);
		assertSame(NOTICE, reported.getFirst());
	}

	@Test
	void reportsConfigurationDialogsClearedDialogsAndEveryScreenReset() {
		packets.listen(session.proxy, recording());

		session.receive(new ClientboundKeepAlivePacket(7));
		session.receive(new ClientboundShowDialogConfigurationPacket(NOTICE));
		session.receive(new ClientboundClearDialogPacket());
		session.receive(new ClientboundStartConfigurationPacket());
		session.receive(new ClientboundRespawnPacket(null, false, false));

		assertEquals(List.of("shown during configuration", "cleared", "reset", "reset"), events);
		assertSame(NOTICE, reported.getFirst());
	}

	@Test
	void reportsTheEntriesOfTheDialogRegistryOnly() {
		packets.listen(session.proxy, recording());

		session.receive(new ClientboundRegistryDataPacket(Key.key("minecraft:dimension_type"), List.of()));
		session.receive(new ClientboundRegistryDataPacket(Key.key("minecraft:dialog"), List.of(
				new RegistryEntry(Key.key("minecraft:server_links"), null),
				new RegistryEntry(Key.key("example:rules"), NOTICE)
		)));

		assertEquals(List.of("registry"), events);
		assertEquals(List.of(new RegistryDialogEntry("minecraft:server_links", null), new RegistryDialogEntry("example:rules", NOTICE)),
				reported.getFirst());
	}

	@Test
	void reportsNothingWhileDisconnectedAndRemovesItsListenerWhenDetached() {
		Runnable detach = packets.listen(session.proxy, recording());

		session.connected = false;
		session.receive(new ClientboundClearDialogPacket());
		detach.run();

		assertEquals(List.of(), events);
		assertEquals(List.of(), session.listeners);
	}

	@Test
	void sendsCommandsThroughTheUnsignedCommandPacket() {
		packets.command(session.proxy, "login secret");

		assertEquals("login secret", assertInstanceOf(ServerboundChatCommandPacket.class, session.sent.getFirst()).getCommand());
	}

	@Test
	void sendsCustomClicksWithTheirPayloadTreeAsCompound() {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("source", "dialog");
		payload.put("remember", (byte) 1);
		payload.put("amount", 2.5F);
		payload.put("pages", List.of(1, 2));
		payload.put("nested", Map.of("empty", List.of()));

		packets.customClick(session.proxy, "example:submit", payload);
		packets.customClick(session.proxy, "example:ping", Map.of());

		ServerboundCustomClickActionPacket submit = assertInstanceOf(ServerboundCustomClickActionPacket.class, session.sent.get(0));
		assertEquals(Key.key("example:submit"), submit.getId());
		assertEquals(NbtMap.builder()
				.putString("source", "dialog")
				.putBoolean("remember", true)
				.putFloat("amount", 2.5F)
				.putList("pages", NbtType.INT, 1, 2)
				.putCompound("nested", NbtMap.builder().putList("empty", NbtType.END).build())
				.build(), submit.getPayload());
		assertEquals(new NbtList<>(NbtType.INT, 1, 2), submit.getPayload().get("pages"));
		assertEquals(NbtMap.EMPTY, assertInstanceOf(ServerboundCustomClickActionPacket.class, session.sent.get(1)).getPayload());
	}

	@Test
	void isTheOnlyDialogsPortOfItsRelease() {
		List<Class<?>> ports = ServiceLoader.load(DialogsPackets.class).stream()
				.map(ServiceLoader.Provider::type)
				.<Class<?>>map(type -> type)
				.toList();

		assertEquals(List.of(McProtocolDialogsPackets.class), ports);
		assertEquals(Session.class, packets.sessionType());
	}

	private DialogsPacketListener recording() {
		return new DialogsPacketListener() {
			@Override
			public void shown(@NotNull Map<String, Object> dialog, boolean configuration) {
				events.add(configuration ? "shown during configuration" : "shown in the game");
				reported.add(dialog);
			}

			@Override
			public void shownFromRegistry(int registryId) {
				events.add("shown from registry " + registryId);
			}

			@Override
			public void cleared() {
				events.add("cleared");
			}

			@Override
			public void reset() {
				events.add("reset");
			}

			@Override
			public void registry(@NotNull List<RegistryDialogEntry> entries) {
				events.add("registry");
				reported.add(entries);
			}
		};
	}

	/**
	 * An MCProtocolLib session that records sent packets and listeners and delivers received packets synchronously.
	 */
	private static final class NativeSession {
		private final List<Packet> sent = new ArrayList<>();
		private final List<SessionListener> listeners = new ArrayList<>();
		private final Session proxy = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
				(instance, method, arguments) -> handle(method, arguments));
		private boolean connected = true;

		private void receive(Packet packet) {
			List.copyOf(listeners).forEach(listener -> listener.packetReceived(proxy, packet));
		}

		private Object handle(Method method, Object[] arguments) {
			switch (method.getName()) {
				case "send" -> sent.add((Packet) arguments[0]);
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
