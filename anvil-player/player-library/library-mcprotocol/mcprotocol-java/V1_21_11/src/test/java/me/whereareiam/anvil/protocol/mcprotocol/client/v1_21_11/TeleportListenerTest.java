package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TeleportListenerTest {
	private final List<String> events = new ArrayList<>();
	private final List<Packet> sent = new ArrayList<>();
	private final ClientSession session = session();
	private final TeleportListener listener = new TeleportListener(session, new RecordingListener());

	@Test
	void reportsATeleportBeforeAcceptingItAndAnnouncingThatThePlayerLoaded() {
		listener.packetReceived(session, new ClientboundPlayerPositionPacket(7, 1, 64, -2, 0, 0, 0, 90, -15));

		assertEquals(List.of("teleported 90.0 -15.0", "sent ServerboundAcceptTeleportationPacket", "sent ServerboundPlayerLoadedPacket"), events);
		assertEquals(7, assertInstanceOf(ServerboundAcceptTeleportationPacket.class, sent.getFirst()).getId());
		assertEquals(ServerboundPlayerLoadedPacket.INSTANCE, sent.get(1));
	}

	@Test
	void leavesEveryOtherPacketAlone() {
		listener.packetReceived(session, new ClientboundPingPacket(42));

		assertEquals(List.of(), events);
	}

	private ClientSession session() {
		Class<?>[] types = {ClientSession.class};
		return (ClientSession) Proxy.newProxyInstance(ClientSession.class.getClassLoader(), types, (proxy, method, arguments) -> {
			if (!method.getName().equals("send")) throw new UnsupportedOperationException(method.getName());

			Packet packet = (Packet) arguments[0];
			sent.add(packet);
			events.add("sent " + packet.getClass().getSimpleName());
			return null;
		});
	}

	private final class RecordingListener implements ClientListener<ClientSession> {
		@Override
		public void loggedIn(@NotNull ClientSession session, @NotNull ClientProfile granted) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void teleported(@NotNull ClientSession session, float yaw, float pitch) {
			events.add("teleported " + yaw + " " + pitch);
		}

		@Override
		public void disconnected(@NotNull ClientSession session, @NotNull DisconnectCause cause, @NotNull String reason) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void received(@NotNull ClientSession session, @NotNull ClientboundPacket packet) {
			throw new UnsupportedOperationException();
		}
	}
}
