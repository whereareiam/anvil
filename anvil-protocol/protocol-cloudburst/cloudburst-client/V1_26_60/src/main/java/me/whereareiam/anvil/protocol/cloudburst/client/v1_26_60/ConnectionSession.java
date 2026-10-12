package me.whereareiam.anvil.protocol.cloudburst.client.v1_26_60;

import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstSession;
import me.whereareiam.anvil.protocol.cloudburst.client.PacketSubscription;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientPosition;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.cloudburst.client.model.GameStart;
import me.whereareiam.anvil.protocol.cloudburst.client.world.v1_26_60.WorldTracker;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * One RakNet connection of the release: its {@link BedrockPeer}, the client session the peer creates once RakNet
 * connected, the subscriptions made on it and the trackers of what the client knows about its own player and its
 * world, and the {@link InputTicker} that sends the player's input on every tick from its spawn on. The packet handler
 * dispatches every received packet here before handling it, and records the game start and item definitions as they
 * arrive. Each received packet reaches the trackers, the {@link WorldTracker} among them, before the subscriptions,
 * so a subscription already reads the state the packet set, and each sent packet reaches the player's trackers before
 * it leaves, so an answer never overtakes the prediction it settles. Closing the connection stops the inputs and ends
 * every subscription, so a reconnect, which opens a new connection session with trackers of its own, never reaches
 * them.
 */
final class ConnectionSession implements CloudburstSession<BedrockPacket, ItemDefinition, ItemData> {
	private static final long TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

	private final BedrockPeer peer;
	private final BedrockCodec codec;
	private final ClientProfile profile;
	private final List<Subscription<?>> subscriptions = new CopyOnWriteArrayList<>();
	private final InventoryTracker inventory = new InventoryTracker();
	private final PositionTracker position = new PositionTracker();
	private final InputTicker input;
	private final WorldTracker world;

	private volatile @Nullable BedrockClientSession session;
	private volatile @Nullable GameStart start;
	private volatile long startNanos;
	private volatile boolean serverMovement;
	private volatile List<ItemDefinition> items = List.of();
	private volatile boolean closed;

	ConnectionSession(@NotNull BedrockPeer peer, @NotNull BedrockCodec codec, @NotNull ClientProfile profile) {
		this.peer = peer;
		this.codec = codec;
		this.profile = profile;
		this.input = new InputTicker(position, this::tick, () -> serverMovement, this::send);
		this.world = new WorldTracker(codec.getProtocolVersion(), this::send);
	}

	@Override
	public int protocol() {
		return codec.getProtocolVersion();
	}

	@Override
	public boolean connected() {
		return !closed && peer.isConnected();
	}

	@Override
	public @NotNull ClientProfile profile() {
		return profile;
	}

	@Override
	public void send(@NotNull BedrockPacket packet) {
		BedrockClientSession current = session;
		if (current == null || !connected()) throw new IllegalStateException("The Bedrock session is not connected");

		inventory.sent(packet);
		position.sent(packet);
		input.sent(packet);
		current.sendPacket(packet);
	}

	@Override
	public <T extends BedrockPacket> @NotNull PacketSubscription subscribe(@NotNull Class<T> type, @NotNull Consumer<? super T> listener) {
		Subscription<T> subscription = new Subscription<>(type, listener);
		if (closed) return subscription;

		subscriptions.add(subscription);
		// A close that raced with the registration must not leave it behind.
		if (closed) subscriptions.remove(subscription);
		return subscription;
	}

	@Override
	public @NotNull Optional<GameStart> start() {
		return Optional.ofNullable(start);
	}

	@Override
	public boolean serverAuthoritativeMovement() {
		if (start == null) throw new IllegalStateException("The Bedrock session has not started its game yet");

		return serverMovement;
	}

	@Override
	public @NotNull List<ItemDefinition> itemDefinitions() {
		return items;
	}

	@Override
	public long tick() {
		GameStart current = start;
		if (current == null) throw new IllegalStateException("The Bedrock session has not started its game yet");

		return current.getTick() + (System.nanoTime() - startNanos) / TICK_NANOS;
	}

	@Override
	public boolean serverAuthoritativeInventory() {
		return inventory.serverAuthoritative();
	}

	@Override
	public int nextItemStackRequestId() {
		return inventory.nextRequestId();
	}

	@Override
	public int selectedSlot() {
		return inventory.selectedSlot();
	}

	@Override
	public @NotNull Optional<ItemData> item(int containerId, int slot) {
		return inventory.item(containerId, slot);
	}

	@Override
	public void select(int slot) {
		send(inventory.equip(slot));
	}

	@Override
	public @NotNull ClientPosition position() {
		return position.position();
	}

	@Override
	public @NotNull OptionalInt blockAt(int x, int y, int z) {
		return world.blockAt(x, y, z);
	}

	/**
	 * Returns the peer of the connection.
	 *
	 * @return RakNet peer
	 */
	@NotNull BedrockPeer peer() {
		return peer;
	}

	/**
	 * Attaches the client session the peer created once RakNet connected.
	 *
	 * @param session the connection's client session
	 */
	void attach(@NotNull BedrockClientSession session) {
		this.session = session;
	}

	/**
	 * Applies a received packet to the trackers, then delivers it to the subscriptions of its type in subscription
	 * order.
	 *
	 * @param packet received packet
	 */
	void dispatch(@NotNull BedrockPacket packet) {
		if (closed) return;

		inventory.received(packet);
		position.received(packet);
		input.received(packet);
		packet.handle(world);

		for (Subscription<?> subscription : subscriptions)
			subscription.deliver(packet);
	}

	/**
	 * Records the game start.
	 *
	 * @param started what the game start told the client
	 * @param serverMovement whether the game start made the server authoritative over the player's movement
	 */
	void started(@NotNull GameStart started, boolean serverMovement) {
		this.serverMovement = serverMovement;
		startNanos = System.nanoTime();
		start = started;
	}

	/**
	 * Starts the inputs a client sends on every tick once the server spawned its player.
	 */
	void spawned() {
		input.start(peer.getChannel().eventLoop());
	}

	/**
	 * Records the movement authority a server's later movement authority packet set.
	 *
	 * @param serverMovement whether the server is authoritative over the player's movement
	 */
	void movementAuthority(boolean serverMovement) {
		this.serverMovement = serverMovement;
	}

	/**
	 * Records the item definitions the server registered.
	 *
	 * @param definitions registered definitions
	 */
	void registered(@NotNull List<ItemDefinition> definitions) {
		items = List.copyOf(definitions);
	}

	/**
	 * Stops the inputs and ends every subscription once the connection closed.
	 */
	void close() {
		closed = true;
		input.stop();
		subscriptions.clear();
	}

	/**
	 * One listener of the packets of a type.
	 */
	private final class Subscription<T extends BedrockPacket> implements PacketSubscription {
		private final Class<T> type;
		private final Consumer<? super T> listener;
		private volatile boolean active = true;

		private Subscription(Class<T> type, Consumer<? super T> listener) {
			this.type = type;
			this.listener = listener;
		}

		private void deliver(BedrockPacket packet) {
			if (!active || !type.isInstance(packet)) return;

			try {
				listener.accept(type.cast(packet));
			} catch (RuntimeException failure) {
				// The worker's diagnostics show standard error; the packet still reaches the others and the client.
				System.err.println("[Anvil] A subscription to " + type.getSimpleName() + " failed: " + failure);
			}
		}

		@Override
		public void close() {
			active = false;
			subscriptions.remove(this);
		}
	}
}
