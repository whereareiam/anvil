package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.mcprotocol.client.TestClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchStartPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundChunkBatchReceivedPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPacketListenerTest {
	private static final UUID GRANTED = new UUID(0, 2);

	private final List<String> events = new ArrayList<>();
	private final List<Packet> sent = new ArrayList<>();
	private final ClientSession session = session();
	private final ClientPacketListener listener = new ClientPacketListener(session, new TestClientListener<>(events), McProtocolClientAdapter.packets(PacketDemand.EVERY),
			new TextTranslator());

	@Test
	void reportsTheLoginWithTheGrantedProfile() {
		receive(new ClientboundLoginFinishedPacket(new GameProfile(GRANTED, "Alice_Renamed")));
		receive(new ClientboundKeepAlivePacket(7));
		receive(new ClientboundRegistryDataPacket(Key.key("minecraft", "dimension_type"),
				List.of(new RegistryEntry(Key.key("minecraft", "overworld"), null))));
		receive(new ClientboundLoginPacket(42, false, new Key[0], 20, 10, 10, false, true, false,
				new PlayerSpawnInfo(0, Key.key("minecraft", "overworld"), 0, GameMode.SURVIVAL, GameMode.SURVIVAL, false, false, null, 0,
						63),
				false));

		assertEquals(List.of("granted Alice_Renamed " + GRANTED, "logged in as Alice_Renamed " + GRANTED), events);
	}

	@Test
	void acknowledgesEveryChunkBatchWithTheRateItWantsNext() {
		receive(new ClientboundChunkBatchStartPacket());
		receive(new ClientboundChunkBatchFinishedPacket(9));
		receive(new ClientboundChunkBatchStartPacket());
		receive(new ClientboundChunkBatchFinishedPacket(0));

		assertEquals(List.of("sent ServerboundChunkBatchReceivedPacket", "sent ServerboundChunkBatchReceivedPacket"),
				events);
		float first = assertInstanceOf(ServerboundChunkBatchReceivedPacket.class, sent.getFirst()).getDesiredChunksPerTick();
		assertTrue(first > 0);
		assertEquals(first, assertInstanceOf(ServerboundChunkBatchReceivedPacket.class, sent.get(1))
				.getDesiredChunksPerTick());
	}

	@Test
	void answersAPingWithAPongCarryingItsIdentifier() {
		receive(new ClientboundPingPacket(42));

		assertEquals(List.of("sent ServerboundPongPacket"), events);
		assertEquals(42, assertInstanceOf(ServerboundPongPacket.class, sent.getFirst()).getId());
	}

	@Test
	void flattensDisconnectReasonsToPlainTextWithTranslationArguments() {
		receive(new ClientboundDisconnectPacket(Component.translatable("multiplayer.disconnect.kicked", Component.text("Alice"))
				.append(Component.text(" by Anvil"))));
		receive(new ClientboundLoginDisconnectPacket(Component.text("Outdated: ").append(Component.translatable("version", Component.text("1.21.11")))));

		assertEquals(List.of("disconnected SERVER multiplayer.disconnect.kicked Alice by Anvil", "disconnected SERVER Outdated: version 1.21.11"), events);
	}

	@Test
	void reportsTheGrantedProfileAsSoonAsTheLoginSucceeds() {
		receive(new ClientboundLoginFinishedPacket(new GameProfile(GRANTED, "Alice_Renamed")));

		assertEquals(List.of("granted Alice_Renamed " + GRANTED), events);
	}

	private void receive(Packet packet) {
		listener.packetReceived(session, packet);
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
}
