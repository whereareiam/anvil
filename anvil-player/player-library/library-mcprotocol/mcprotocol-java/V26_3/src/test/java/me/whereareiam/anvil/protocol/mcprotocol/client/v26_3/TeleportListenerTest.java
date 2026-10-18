package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportListenerTest {
	private final List<String> events = new ArrayList<>();
	private final List<Packet> sent = new ArrayList<>();
	private final ClientSession session = session();
	private final TeleportListener listener = new TeleportListener(session, new RecordingListener());

	@Test
	void reportsATeleportBeforeAcceptingItWithItsTargetAndAnnouncingThatThePlayerLoaded() {
		receive(new ClientboundPlayerPositionPacket(7, 1, 64, -2, 0, 0, 0, 90, -15));

		assertEquals(List.of("teleported 90.0 -15.0", "sent ServerboundAcceptTeleportationPacket", "sent ServerboundPlayerLoadedPacket"), events);
		assertEquals(new ServerboundAcceptTeleportationPacket(7, 1, 64, -2, 90, -15), sent.getFirst());
		assertEquals(ServerboundPlayerLoadedPacket.INSTANCE, sent.get(1));
	}

	@Test
	void acceptsARelativeTeleportWithThePositionAndViewItResultsInFromTheLastMovement() {
		receive(new ClientboundPlayerPositionPacket(1, 1, 64, -2, 0, 0, 0, 90, -15));
		send(new ServerboundMovePlayerPosRotPacket(true, false, 10, 65, 20, 45, 10));
		send(new ServerboundMovePlayerPosPacket(true, false, 12, 65, 20));
		sent.clear();
		events.clear();

		receive(new ClientboundPlayerPositionPacket(2, 0, 2, -4, 0, 0, 0, 5, 30,
				PositionElement.X, PositionElement.Y, PositionElement.Z, PositionElement.Y_ROT));

		assertEquals(List.of("teleported 5.0 30.0", "sent ServerboundAcceptTeleportationPacket", "sent ServerboundPlayerLoadedPacket"), events);
		assertEquals(new ServerboundAcceptTeleportationPacket(2, 12, 67, 16, 50, 30), sent.getFirst());
	}

	@Test
	void followsATurnWithoutMoving() {
		receive(new ClientboundPlayerPositionPacket(1, 1, 64, -2, 0, 0, 0, 90, -15));
		send(new ServerboundMovePlayerRotPacket(true, false, 45, 10));
		sent.clear();

		receive(new ClientboundPlayerPositionPacket(2, 0, 0, 0, 0, 0, 0, 0, 0,
				PositionElement.X, PositionElement.Y, PositionElement.Z, PositionElement.Y_ROT, PositionElement.X_ROT));

		assertEquals(new ServerboundAcceptTeleportationPacket(2, 1, 64, -2, 45, 10), sent.getFirst());
	}

	private void receive(Packet packet) {
		listener.packetReceived(session, packet);
	}

	private void send(Packet packet) {
		listener.packetSent(session, packet);
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
