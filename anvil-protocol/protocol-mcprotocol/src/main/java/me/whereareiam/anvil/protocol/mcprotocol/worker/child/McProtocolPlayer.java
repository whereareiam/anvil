package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import me.whereareiam.anvil.protocol.api.channel.ProtocolSubscription;
import me.whereareiam.anvil.protocol.api.worker.NativePlayer;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageWriter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ParticleStatus;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * MCProtocolLib-bound core player state shared with worker capabilities through a narrow provider API.
 */
@RequiredArgsConstructor
@Accessors(fluent = true)
final class McProtocolPlayer implements NativePlayer<ClientSession>, AutoCloseable {
	private static final String CLIENT_LOCALE = "en_us";
	private static final int VIEW_DISTANCE = 8;

	private final String id;
	@Getter
	private final @NotNull String name;
	private final String host;
	private final int port;
	@Getter
	private final @NotNull UUID uuid;
	private final @Nullable String accessToken;
	private final WorkerMessageWriter events;
	private final WorkerCapabilityRegistry capabilities;

	private final @NotNull WorkerMessageCodec codec = new WorkerMessageCodec();
	private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
	private final AtomicBoolean disconnectNotified = new AtomicBoolean();
	private final NativeSessionBindings nativeBindings = new NativeSessionBindings();

	private WorkerCapabilityRegistry.PlayerBindings bindings;
	private volatile @Nullable ClientSession session;
	private volatile float yaw;
	private volatile float pitch;

	void initialize() {
		try {
			bindings = capabilities.bind(this);
		} catch (RuntimeException | Error failure) {
			try {
				close();
			} catch (RuntimeException | Error cleanup) {
				if (failure != cleanup) failure.addSuppressed(cleanup);
			}
			throw failure;
		}
	}

	@NotNull JsonNode execute(@NotNull String operation, @NotNull JsonNode arguments) {
		return bindings.execute(operation, arguments);
	}

	@Override
	public @NotNull UUID uniqueId() {
		return uuid;
	}

	@Override
	public synchronized void connect() {
		if (session != null && session.isConnected()) return;

		disconnectNotified.set(false);
		MinecraftProtocol protocol = new MinecraftProtocol(new GameProfile(uuid, name), accessToken);
		ClientSession created = ClientNetworkSessionFactory.factory()
				.setAddress(host, port)
				.setProtocol(protocol)
				.create();

		created.addListener(new Listener());
		session = created;
		nativeBindings.attach(created);
		created.connect(true);
	}

	@Override
	public synchronized void disconnect() {
		ClientSession current = session;
		if (current != null && current.isConnected())
			current.disconnect("Disconnected by Anvil");
	}

	@Override
	public synchronized void rejoin() {
		disconnect();
		session = null;
		connect();
	}

	@Override
	public @NotNull ClientSession backend() {
		ClientSession current = session;
		if (current == null || !current.isConnected())
			throw new IllegalStateException("Player '" + name + "' is not connected");
		return current;
	}

	@Override
	public boolean isCurrentBackend(@NotNull ClientSession backend) {
		return session == backend;
	}

	@Override
	public synchronized @NotNull ProtocolSubscription bindBackend(@NotNull Function<ClientSession, ProtocolSubscription> listener) {
		return nativeBindings.register(listener);
	}

	@Override
	public void emit(@NotNull String event, byte @NotNull [] payload) {
		events.event(id, event, WorkerMessageCodec.message(payload));
	}

	@Override
	public float yaw() {
		return yaw;
	}

	@Override
	public float pitch() {
		return pitch;
	}

	@Override
	public void view(float yaw, float pitch) {
		this.yaw = yaw;
		this.pitch = pitch;
	}

	@Override
	public void close() {
		try (nativeBindings; var ignored = bindings) {
			disconnect();
		} finally {
			session = null;
		}
	}

	private final class Listener extends SessionAdapter {
		@Override
		public void packetReceived(@NotNull Session current, @NotNull Packet packet) {
			if (current != McProtocolPlayer.this.session) return;

			if (packet instanceof ClientboundLoginPacket) {
				current.send(new ServerboundClientInformationPacket(
						CLIENT_LOCALE,
						VIEW_DISTANCE,
						ChatVisibility.FULL,
						true,
						List.of(SkinPart.values()),
						HandPreference.RIGHT_HAND,
						false,
						true,
						ParticleStatus.ALL
				));
				emit("player.connection", codec.connection(true, null));
			}

			if (packet instanceof ClientboundPlayerPositionPacket position) {
				view(position.getYRot(), position.getXRot());
				current.send(new ServerboundAcceptTeleportationPacket(position.getId()));
				current.send(ServerboundPlayerLoadedPacket.INSTANCE);
			}

			if (packet instanceof ClientboundDisconnectPacket disconnect) disconnected(disconnect.getReason());
			if (packet instanceof ClientboundLoginDisconnectPacket disconnect) disconnected(disconnect.getReason());

		}

		@Override
		public void disconnected(@NotNull DisconnectedEvent event) {
			if (event.getSession() != McProtocolPlayer.this.session) return;
			disconnected(event.getReason());
		}

		private void disconnected(Component reason) {
			if (!disconnectNotified.compareAndSet(false, true)) return;
			emit("player.connection", codec.connection(false, plain.serialize(reason)));
		}
	}
}
