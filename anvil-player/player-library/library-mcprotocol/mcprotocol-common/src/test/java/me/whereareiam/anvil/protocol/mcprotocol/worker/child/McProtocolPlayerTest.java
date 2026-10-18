package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.PlayerConnectionEvent;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.JavaWorld;
import me.whereareiam.anvil.protocol.java.api.model.world.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.model.world.LevelChunkSection;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerConnection;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerCredentials;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerEvent;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerCapabilities;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerProfile;
import me.whereareiam.anvil.protocol.mcprotocol.type.WorkerControlOperation;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.RecordingClient;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageWriter;
import me.whereareiam.anvil.protocol.worker.WorkerExtensions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McProtocolPlayerTest {
	private final ByteArrayOutputStream output = new ByteArrayOutputStream();
	private final RecordingClient client = new RecordingClient();
	private final UUID uuid = UUID.randomUUID();

	@Test
	void opensSessionsThroughTheClientWithLoginAndClientSettings() {
		try (McProtocolPlayer player = player("token")) {
			player.connect();
			player.connect();

			ClientLogin login = client.logins().getFirst();
			assertEquals(1, client.logins().size());
			assertEquals("Alice", login.getProfile().getName());
			assertEquals(uuid, login.getProfile().getUniqueId());
			assertEquals("token", login.getCredentials().getAccessToken());
			assertEquals("en_us", login.getSettings().getLocale());
			assertEquals(8, login.getSettings().getViewDistance());
			assertEquals("localhost", login.getConnection().getHost());
			assertEquals(25565, login.getConnection().getPort());
			assertEquals("lobby.example.test", login.getConnection().getVirtualHost());
			assertEquals("127.0.0.2", login.getConnection().getSourceAddress());
			assertFalse(login.toString().contains("token"));
			assertSame(client.sessions().getFirst(), player.nativeSession());
		}
	}

	@Test
	void reportsConnectionEventsAndViewOnlyForTheCurrentSession() {
		try (McProtocolPlayer player = player(null)) {
			player.connect();
			Object first = client.sessions().getFirst();
			player.rejoin();
			Object second = client.sessions().get(1);

			client.listener(first).loggedIn(first, granted("Alice"));
			client.listener(first).teleported(first, 10, 20);
			client.listener(first).disconnected(first, DisconnectCause.SERVER, "replaced");
			client.listener(second).loggedIn(second, granted("Alice_Renamed"));
			client.listener(second).teleported(second, 30, 40);
			client.listener(second).disconnected(second, DisconnectCause.SERVER, "kicked anvil:requested-kick");
			client.listener(second).disconnected(second, DisconnectCause.CONNECTION_LOST, "closed");

			assertEquals(new ViewRotation(30, 40), player.viewRotation());
			assertEquals(List.of("Disconnected by Anvil"), client.disconnects());
			assertEquals(List.of(
					new PlayerConnectionEvent(true, null, null, new PlayerProfile("Alice_Renamed", uuid)),
					new PlayerConnectionEvent(false, "kicked anvil:requested-kick", DisconnectCause.SERVER, null)
			), events());
		}
	}

	@Test
	void reportsTheProfileGrantedAtTheLoginSuccessOnlyForTheCurrentSession() {
		try (McProtocolPlayer player = player(null)) {
			player.connect();
			Object first = client.sessions().getFirst();
			player.rejoin();
			Object second = client.sessions().get(1);

			client.listener(first).granted(first, granted("Alice"));
			client.listener(second).granted(second, granted("Alice_Renamed"));

			assertEquals(List.of(new PlayerProfile("Alice_Renamed", uuid)), grants());
		}
	}

	@Test
	void prefersTheServersReasonOverAConnectionThatClosedFirst() throws Exception {
		try (McProtocolPlayer player = player(null)) {
			player.connect();
			Object session = client.sessions().getFirst();

			client.listener(session).disconnected(session, DisconnectCause.CONNECTION_LOST, "disconnect.endOfStream");
			client.listener(session).disconnected(session, DisconnectCause.SERVER, "anvil:kicked");
			Thread.sleep(600);

			assertEquals(List.of(new PlayerConnectionEvent(false, "anvil:kicked", DisconnectCause.SERVER, null)), events());
		}
	}

	@Test
	void reportsAClosedConnectionOnceNoReasonFollowsAndARefusedEncryptionAtOnce() throws Exception {
		try (McProtocolPlayer player = player(null)) {
			player.connect();
			Object first = client.sessions().getFirst();
			client.listener(first).disconnected(first, DisconnectCause.CONNECTION_LOST, "disconnect.endOfStream");
			assertEquals(List.of(), events());
			Thread.sleep(600);
			assertEquals(DisconnectCause.CONNECTION_LOST, events().getFirst().getCause());

			player.rejoin();
			Object second = client.sessions().get(1);
			client.listener(second).disconnected(second, DisconnectCause.AUTHENTICATION_REQUIRED, "encryption");
			assertEquals(DisconnectCause.AUTHENTICATION_REQUIRED, events().get(1).getCause());
		}
	}

	@Test
	void endsTheSessionsBindingsBeforeReportingItsDisconnect() {
		try (McProtocolPlayer player = player(null)) {
			List<String> bindings = new ArrayList<>();
			player.bindNativeSession(nativeSession -> {
				bindings.add("attach");
				return () -> bindings.add("close with " + events().size() + " events");
			});
			player.connect();
			Object session = client.sessions().getFirst();

			client.listener(session).disconnected(session, DisconnectCause.SERVER, "anvil:kicked");

			assertEquals(List.of("attach", "close with 0 events"), bindings);
			assertEquals(1, events().size());
		}
	}

	@Test
	void refusesTheNativeSessionWhileDisconnected() {
		try (McProtocolPlayer player = player(null)) {
			assertThrows(IllegalStateException.class, player::nativeSession);
			player.connect();
			player.disconnect();
			assertThrows(IllegalStateException.class, player::nativeSession);
		}
	}

	@Test
	void deliversOnlyThePacketsOfTheCurrentSessionToProtocolListeners() {
		try (McProtocolPlayer player = player(null)) {
			List<ClientboundSystemChat> received = new ArrayList<>();
			player.protocol(JavaProtocol.class).listen(ClientboundSystemChat.class, received::add);
			player.connect();
			Object first = client.sessions().getFirst();
			player.rejoin();
			Object second = client.sessions().get(1);

			client.listener(first).received(first, new ClientboundSystemChat("replaced", false));
			client.listener(second).received(second, new ClientboundSystemChat("current", false));

			assertEquals(List.of(new ClientboundSystemChat("current", false)), received);
		}
	}

	@Test
	void sendsProtocolPacketsOnTheCurrentSession() {
		try (McProtocolPlayer player = player(null)) {
			player.connect();
			player.rejoin();
			Object current = client.sessions().get(1);

			player.protocol(JavaProtocol.class).send(new ServerboundChat("hello there"));

			assertEquals(List.of(), client.sent(client.sessions().getFirst()));
			assertEquals(List.of(new ServerboundChat("hello there")), client.sent(current));
		}
	}

	@Test
	void keepsTheWorldOfTheCurrentSessionAndForgetsItOnceTheSessionCloses() {
		try (McProtocolPlayer player = player(null)) {
			JavaWorld world = player.protocol(JavaWorld.class);
			player.connect();
			Object first = client.sessions().getFirst();
			player.rejoin();
			Object second = client.sessions().get(1);
			Dimension overworld = new Dimension("minecraft:overworld", -64, 384);
			ClientboundLevelChunkWithLight chunk = new ClientboundLevelChunkWithLight(new ChunkPosition(0, 0),
					List.of(LevelChunkSection.uniform(1)));

			client.listener(first).received(first, new ClientboundLogin(1, new Dimension("minecraft:the_end", 0, 256)));
			client.listener(second).received(second, new ClientboundLogin(2, overworld));
			client.listener(second).received(second, chunk);
			assertEquals(Optional.of(overworld), world.dimension());
			assertEquals(OptionalInt.of(1), world.blockState(0, -64, 0));

			client.listener(second).disconnected(second, DisconnectCause.SERVER, "kicked");
			assertEquals(Optional.empty(), world.dimension());
			assertEquals(OptionalInt.empty(), world.blockState(0, -64, 0));
		}
	}

	@Test
	void speaksOnlyTheJavaEditionProtocolAndItsWorld() {
		try (McProtocolPlayer player = player(null)) {
			ProtocolUnavailableException refused = assertThrows(ProtocolUnavailableException.class, () -> player.protocol(Runnable.class));

			assertEquals("the mcprotocol worker speaks " + JavaProtocol.class.getName() + ", " + JavaWorld.class.getName()
					+ ", not java.lang.Runnable", refused.getMessage());
		}
	}

	@Test
	void executesOperationsAndEmitsEventsThroughTheWorkerTransport() {
		var echo = new OperationDescriptor<>("example.echo", String.class, String.class);
		var changed = new EventDescriptor<>("example.changed", String.class);
		WorkerExtension<Object> extension = extension("example", (player, operations) -> {
			operations.register(echo, request -> {
				player.emit(changed, player.name() + ":" + request);
				return player.name() + ":" + request;
			});
			return () -> { };
		});
		var codec = new WorkerMessageCodec();

		try (McProtocolPlayer player = player(null, extension)) {
			JsonNode response = player.execute(echo.getId(), codec.envelope("hello"));

			assertEquals("Alice:hello", codec.open(response, String.class));
			String line = output.toString(StandardCharsets.UTF_8).strip();
			WorkerEvent event = (WorkerEvent) codec.decodeMessage(line).orElseThrow();
			assertEquals(changed.getId(), event.getEvent());
			assertEquals("Alice:hello", codec.open(event.getPayload(), String.class));
		}
	}

	@Test
	void makesAnExtensionUnavailableWhenNoSelectedSegmentProvidesItsAdapter() {
		WorkerExtension<Object> extension = extension("adapted", (player, operations) -> {
			player.adapter(Runnable.class);
			return () -> { };
		});

		try (McProtocolPlayer player = player(null, extension)) {
			WorkerPlayerCapabilities report = player.initialize();

			assertEquals(Set.of(), report.getCapabilities());
			String reason = "no mcprotocol segment selected for Minecraft 1.21.11 provides an adapter for "
					+ Runnable.class.getName();
			assertEquals(Map.of("adapted", reason), report.getUnavailable());
		}
	}

	@Test
	void releasesTheProtocolListenersOfAnExtensionThatFailsToLink() {
		List<String> received = new ArrayList<>();
		WorkerExtension<Object> partial = extension("partial", (player, operations) -> {
			player.protocol(JavaProtocol.class)
					.listen(ClientboundSystemChat.class, packet -> received.add("partial " + packet.getText()));
			throw new NoClassDefFoundError("org/example/MissingPacket");
		});
		WorkerExtension<Object> listening = extension("listening", (player, operations) -> {
			JavaProtocol protocol = player.protocol(JavaProtocol.class);

			Subscription subscription = protocol.listen(ClientboundSystemChat.class,
					packet -> received.add("listening " + packet.getText()));

			return subscription::close;
		});

		try (McProtocolPlayer player = player(null, partial, listening)) {
			player.connect();
			Object session = client.sessions().getFirst();
			client.listener(session).received(session, new ClientboundSystemChat("welcome", false));

			assertEquals(List.of("listening welcome"), received);
		}
	}

	private McProtocolPlayer player(String accessToken, WorkerExtension<?>... extensions) {
		var options = WorkerPlayerOptions.builder()
				.profile(WorkerProfile.builder().name("Alice").uniqueId(uuid).build())
				.credentials(accessToken == null ? null : WorkerCredentials.builder().accessToken(accessToken).build())
				.connection(WorkerConnection.builder().host("localhost").port(25565)
						.virtualHost("lobby.example.test").sourceAddress("127.0.0.2").build())
				.build();
		var player = new McProtocolPlayer("player", options, client, SegmentRoots.none(),
				new WorkerMessageWriter(new PrintStream(output, true, StandardCharsets.UTF_8)),
				new WorkerExtensions(McProtocolClient.LIBRARY_ID, Object.class, WorkerControlOperation.wireNames(),
						List.of(extensions)));
		player.initialize();
		return player;
	}

	private static WorkerExtension<Object> extension(
			String id,
			BiFunction<PlayerBindingContext<Object>, OperationRegistry, WorkerBinding> binding
	) {
		return new WorkerExtension<>() {
			@Override
			public @NotNull String id() {
				return id;
			}

			@Override
			public @NotNull Class<Object> nativeSessionType() {
				return Object.class;
			}

			@Override
			public @NotNull WorkerBinding bind(
					@NotNull PlayerBindingContext<Object> player,
					@NotNull OperationRegistry operations
			) {
				return binding.apply(player, operations);
			}
		};
	}

	private ClientProfile granted(String name) {
		return ClientProfile.builder().name(name).uniqueId(uuid).build();
	}

	private List<PlayerProfile> grants() {
		WorkerMessageCodec codec = new WorkerMessageCodec();
		List<PlayerProfile> grants = new ArrayList<>();
		for (String line : output.toString(StandardCharsets.UTF_8).lines().toList()) {
			WorkerEvent event = (WorkerEvent) codec.decodeMessage(line).orElseThrow();
			assertEquals(PlayerConnectionEvent.GRANTED.getId(), event.getEvent());
			grants.add(codec.open(event.getPayload(), PlayerProfile.class));
		}

		return grants;
	}

	private List<PlayerConnectionEvent> events() {
		WorkerMessageCodec codec = new WorkerMessageCodec();
		List<PlayerConnectionEvent> events = new ArrayList<>();
		for (String line : output.toString(StandardCharsets.UTF_8).lines().toList()) {
			WorkerEvent event = (WorkerEvent) codec.decodeMessage(line).orElseThrow();
			assertEquals(PlayerConnectionEvent.CHANGED.getId(), event.getEvent());
			events.add(codec.open(event.getPayload(), PlayerConnectionEvent.class));
		}

		return events;
	}
}
