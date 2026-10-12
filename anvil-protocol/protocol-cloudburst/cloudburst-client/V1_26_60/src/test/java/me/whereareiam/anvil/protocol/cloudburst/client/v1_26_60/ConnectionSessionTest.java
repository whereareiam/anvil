package me.whereareiam.anvil.protocol.cloudburst.client.v1_26_60;

import io.netty.channel.embedded.EmbeddedChannel;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.cloudburst.client.ClientListener;
import me.whereareiam.anvil.protocol.cloudburst.client.PacketSubscription;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.cloudburst.client.model.GameStart;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.codec.v766.Bedrock_v766;
import org.cloudburstmc.protocol.bedrock.codec.v800.Bedrock_v800;
import org.cloudburstmc.protocol.bedrock.codec.v818.Bedrock_v818;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v3;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetMovementAuthorityPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionSessionTest {
	private final List<String> received = new ArrayList<>();

	@Test
	void dispatchesEveryPacketToTheSubscriptionsOfItsTypeInOrderUntilTheyClose() throws Exception {
		Connection connection = connection(Bedrock_v2193.CODEC);
		PacketSubscription text = connection.session.subscribe(TextPacket.class, packet -> received.add("text " + packet.getMessage()));
		connection.session.subscribe(BedrockPacket.class, packet -> received.add("any " + packet.getClass().getSimpleName()));
		connection.session.subscribe(StartGamePacket.class, packet -> received.add("start"));

		connection.handler.handlePacket(text("Hello"));
		text.close();
		text.close();
		connection.handler.handlePacket(text("Again"));

		assertEquals(List.of("text Hello", "any TextPacket", "any TextPacket"), received);
	}

	@Test
	void keepsDeliveringToTheOthersWhenOneSubscriptionFails() throws Exception {
		Connection connection = connection(Bedrock_v2193.CODEC);
		connection.session.subscribe(TextPacket.class, packet -> {
			throw new IllegalStateException("broken listener");
		});
		connection.session.subscribe(TextPacket.class, packet -> received.add(packet.getMessage()));

		connection.handler.handlePacket(text("Hello"));

		assertEquals(List.of("Hello"), received);
	}

	@Test
	void sendsThroughTheClientSessionOnlyWhileConnected() throws Exception {
		Connection connection = connection(Bedrock_v2193.CODEC);
		TextPacket packet = text("Hello");

		connection.session.send(packet);
		assertSame(packet, connection.client.sent.getLast());

		connection.handler.onDisconnect("closed");
		assertFalse(connection.session.connected());
		assertThrows(IllegalStateException.class, () -> connection.session.send(packet));
	}

	@Test
	void endsItsSubscriptionsWhenTheConnectionClosesSoAReconnectNeverReachesThem() throws Exception {
		Connection first = connection(Bedrock_v2193.CODEC);
		first.session.subscribe(TextPacket.class, packet -> received.add("first " + packet.getMessage()));

		first.handler.onDisconnect("closed");
		first.session.subscribe(TextPacket.class, packet -> received.add("late " + packet.getMessage()));
		first.handler.handlePacket(text("Stale"));
		Connection second = connection(Bedrock_v2193.CODEC);
		second.session.subscribe(TextPacket.class, packet -> received.add("second " + packet.getMessage()));
		second.handler.handlePacket(text("Fresh"));

		assertEquals(List.of("second Fresh"), received);
	}

	@Test
	void recordsTheGameStartAndCountsTicksFromIt() throws Exception {
		Connection connection = connection(Bedrock_v2193.CODEC);
		assertTrue(connection.session.start().isEmpty());
		assertThrows(IllegalStateException.class, connection.session::tick);

		connection.handler.handle(start(List.of()));

		GameStart start = connection.session.start().orElseThrow();
		assertEquals(7, start.getRuntimeEntityId());
		assertEquals(70, start.getUniqueEntityId());
		assertEquals(1, start.getX());
		assertEquals(2, start.getY());
		assertEquals(3, start.getZ());
		assertEquals(90, start.getYaw());
		assertEquals(10, start.getPitch());
		assertEquals(1000, start.getTick());
		assertTrue(connection.session.tick() >= 1000, String.valueOf(connection.session.tick()));
		assertEquals(2193, connection.session.protocol());
		assertEquals("Alice", connection.session.profile().getName());
	}

	@Test
	@SuppressWarnings("deprecation")
	void takesTheMovementAuthorityOfOlderProtocolsFromTheServerAndKnowsOnlyServerAuthorityFromProtocol818() throws Exception {
		Connection older = connection(Bedrock_v800.CODEC);
		assertThrows(IllegalStateException.class, older.session::serverAuthoritativeMovement);
		StartGamePacket clientMovement = start(List.of());
		clientMovement.setAuthoritativeMovementMode(AuthoritativeMovementMode.CLIENT);
		older.handler.handle(clientMovement);
		assertFalse(older.session.serverAuthoritativeMovement());
		SetMovementAuthorityPacket authority = new SetMovementAuthorityPacket();
		authority.setMovementMode(AuthoritativeMovementMode.SERVER);
		older.handler.handle(authority);
		assertTrue(older.session.serverAuthoritativeMovement());

		Connection newer = connection(Bedrock_v818.CODEC);
		newer.handler.handle(start(List.of()));
		assertTrue(newer.session.serverAuthoritativeMovement());
	}

	@Test
	void registersTheItemDefinitionsOfTheGameStartForOlderProtocolsAndOfTheItemRegistryForNewerOnes() throws Exception {
		ItemDefinition stone = new SimpleItemDefinition("minecraft:stone", 1, false);
		Connection older = connection(Bedrock_v766.CODEC);
		older.handler.handle(start(List.of(stone)));
		Connection newer = connection(Bedrock_v2193.CODEC);
		newer.handler.handle(start(List.of(stone)));
		assertTrue(newer.session.itemDefinitions().isEmpty());
		ItemComponentPacket registry = new ItemComponentPacket();
		registry.getItems().add(stone);
		newer.handler.handle(registry);

		assertEquals(List.of("minecraft:stone", "minecraft:empty"), identifiers(older.session.itemDefinitions()));
		assertEquals(List.of("minecraft:stone", "minecraft:empty"), identifiers(newer.session.itemDefinitions()));
	}

	private static List<String> identifiers(List<ItemDefinition> definitions) {
		return definitions.stream().map(ItemDefinition::getIdentifier).toList();
	}

	private static StartGamePacket start(List<ItemDefinition> items) {
		StartGamePacket start = new StartGamePacket();
		start.setRuntimeEntityId(7);
		start.setUniqueEntityId(70);
		start.setPlayerPosition(Vector3f.from(1, 2, 3));
		start.setRotation(Vector2f.from(10, 90));
		start.setCurrentTick(1000);
		start.getItemDefinitions().addAll(items);
		return start;
	}

	private static TextPacket text(String message) {
		TextPacket packet = new TextPacket();
		packet.setType(TextPacket.Type.RAW);
		packet.setMessage(message);
		return packet;
	}

	private static Connection connection(BedrockCodec codec) throws Exception {
		BedrockPacketCodec_v3 packets = new BedrockPacketCodec_v3();
		packets.setCodec(codec);
		RecordingSession client = new RecordingSession(new BedrockPeer(new EmbeddedChannel(packets), (peer, subClientId) -> null));
		ClientLogin login = ClientLogin.builder()
				.profile(ClientProfile.builder().name("Alice").uniqueId(new UUID(0, 1)).build())
				.identity(new TestIdentity(null))
				.connection(ClientConnection.builder().host("127.0.0.1").port(19132).protocol(codec.getProtocolVersion()).build())
				.build();
		ConnectionSession session = new ConnectionSession(client.getPeer(), codec, login.getProfile());
		session.attach(client);

		return new Connection(client, session, new ClientPacketHandler(client, session, codec, login, new IgnoringListener()));
	}

	/**
	 * A connection's client session, connection session and packet handler.
	 */
	private record Connection(RecordingSession client, ConnectionSession session, ClientPacketHandler handler) {
	}

	/**
	 * Ignores every callback.
	 */
	private static final class IgnoringListener implements ClientListener<ConnectionSession> {
		@Override
		public void loggedIn(@NotNull ConnectionSession session, @NotNull ClientProfile granted) {
		}

		@Override
		public void teleported(@NotNull ConnectionSession session, float yaw, float pitch) {
		}

		@Override
		public void disconnected(@NotNull ConnectionSession session, @NotNull DisconnectCause cause, @NotNull String reason) {
		}
	}

	/**
	 * A client session that records what is sent instead of writing it.
	 */
	private static final class RecordingSession extends BedrockClientSession {
		private final List<BedrockPacket> sent = new ArrayList<>();

		private RecordingSession(BedrockPeer peer) {
			super(peer, 0);
		}

		@Override
		public void sendPacket(@NotNull BedrockPacket packet) {
			sent.add(packet);
		}

		@Override
		public void sendPacketImmediately(@NotNull BedrockPacket packet) {
			sent.add(packet);
		}

		@Override
		public void disconnect(CharSequence reason, boolean hideReason) {
		}
	}
}
