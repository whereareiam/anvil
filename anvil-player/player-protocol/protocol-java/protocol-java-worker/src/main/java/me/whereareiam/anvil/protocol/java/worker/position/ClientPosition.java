package me.whereareiam.anvil.protocol.java.worker.position;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.JavaWorld;
import me.whereareiam.anvil.protocol.java.api.LocalPlayer;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.protocol.java.api.type.player.GameMode;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One worker player's {@link LocalPlayer}: the client's own player as the vanilla client keeps it, which every library
 * that embeds the kit gets through its {@code JavaPacketDispatcher}. It follows the packets the dispatcher hands it
 * ({@link #apply}) and its {@link ClientLoop} runs the client's tick 20 times a second once {@link #start started}: the
 * connection's tick, which waits for the level and tells the server it loaded, the features' ticks of the player, the
 * end of the client tick where the release carries it, then the frame after the tick.
 */
public final class ClientPosition implements LocalPlayer {
	private final @NotNull Set<LocalPlayerBehaviour> behaviours;
	private final @NotNull JavaProtocol protocol;
	private final @NotNull JavaWorld world;
	private final @NotNull ClientLoop loop;
	private final @NotNull List<Runnable> ticks;
	private final @NotNull Consumer<Subscription> track;
	private final @NotNull ServerState server;
	private final @NotNull KnownPosition known;

	/**
	 * Creates the stopped client of a player, which knows no position yet.
	 *
	 * @param version the Minecraft version of the player's client
	 * @param name the player's name, which names the client's thread and its failures
	 * @param protocol the player's protocol, which carries what the client sends
	 * @param world the world the player's client knows
	 */
	public ClientPosition(
			@NotNull MinecraftVersion version,
			@NotNull String name,
			@NotNull JavaProtocol protocol,
			@NotNull JavaWorld world
	) {
		this.behaviours = LocalPlayerBehaviour.of(version);
		this.protocol = protocol;
		this.world = world;
		this.loop = new ClientLoop(name, ClientLoop.PERIOD, this::tickClient, failure -> System.err.println(
				"[Anvil] Warning: the client of player '" + name + "' stopped ticking after a tick failed: " + failure));
		this.ticks = new CopyOnWriteArrayList<>();
		this.track = ignored -> { };
		this.server = new ServerState();
		this.known = new KnownPosition(behaviours, protocol, server);
	}

	private ClientPosition(@NotNull ClientPosition client, @NotNull Consumer<Subscription> track) {
		this.behaviours = client.behaviours;
		this.protocol = client.protocol;
		this.world = client.world;
		this.loop = client.loop;
		this.ticks = client.ticks;
		this.track = track;
		this.server = client.server;
		this.known = client.known;
	}

	@Override
	public @NotNull Optional<Vec3d> position() {
		return loop.get(() -> Optional.ofNullable(known.getPosition()));
	}

	@Override
	public void position(@NotNull Vec3d position) {
		loop.run(() -> known.setPosition(position));
	}

	@Override
	public @NotNull ViewRotation rotation() {
		return loop.get(known::getRotation);
	}

	@Override
	public void rotation(@NotNull ViewRotation rotation) {
		loop.run(() -> known.setRotation(rotation));
	}

	@Override
	public boolean onGround() {
		return loop.get(known::isOnGround);
	}

	@Override
	public void onGround(boolean onGround) {
		loop.run(() -> known.setOnGround(onGround));
	}

	@Override
	public @NotNull Vec3d velocity() {
		return loop.get(known::getVelocity);
	}

	@Override
	public void velocity(@NotNull Vec3d velocity) {
		loop.run(() -> known.setVelocity(velocity));
	}

	@Override
	public @NotNull Optional<EntityId> entityId() {
		return loop.get(() -> Optional.ofNullable(server.getEntityId()));
	}

	@Override
	public @NotNull GameMode gameMode() {
		return loop.get(server::getGameMode);
	}

	@Override
	public boolean isDead() {
		return loop.get(server::isDead);
	}

	@Override
	public void run(@NotNull Runnable action) {
		loop.run(action);
	}

	@Override
	public <T> T get(@NotNull Supplier<? extends T> reading) {
		return loop.get(reading);
	}

	@Override
	public @NotNull Subscription onTick(@NotNull Runnable action) {
		ticks.add(action);
		Subscription subscription = () -> ticks.remove(action);
		track.accept(subscription);
		return subscription;
	}

	/**
	 * Follows a packet the player received, before its listeners see it: what the server sets of the player's entity, a
	 * login and a respawn, which make a new player, a teleport, which it accepts, the velocity of the player's own
	 * entity and the event that starts the level's loading. The caller holds the client's lock, as the dispatcher holds
	 * it for each packet it delivers.
	 *
	 * @param packet the received packet
	 */
	public void apply(@NotNull ClientboundPacket packet) {
		server.apply(packet);
		known.apply(packet);
	}

	/**
	 * Tells that the player's connection closed, which forgets its entity and makes a new player.
	 */
	public void disconnected() {
		loop.run(() -> {
			server.disconnected();
			known.forget();
		});
	}

	/**
	 * Places the player in a level the client has loaded, without the server's handshake, as the reference traces'
	 * placed courses start the vanilla client's player: at rest, having told the server nothing.
	 *
	 * @param at the position of the player's feet
	 * @param view where it looks
	 * @param onGround whether it stands on the ground
	 */
	public void place(@NotNull Vec3d at, @NotNull ViewRotation view, boolean onGround) {
		loop.run(() -> known.place(at, view, onGround));
	}

	/**
	 * Tells whether the client's own rules read which chunks it has: the wait for the level from 1.21.4, and the ticks
	 * of the player before 1.17, which need the chunk the player is in.
	 *
	 * @return whether the player's world must keep chunks
	 */
	public boolean readsChunks() {
		return LocalPlayerBehaviour.readsChunks(behaviours);
	}

	/**
	 * Starts the client's ticks, 20 a second on a virtual thread named after the player, unless they run.
	 *
	 * @throws IllegalStateException when the client is closed
	 */
	public void start() {
		loop.start();
	}

	/**
	 * Runs one tick of the client now, under its lock, as a test that ticks the client itself does.
	 */
	public void tick() {
		loop.run(this::tickClient);
	}

	/**
	 * Stops the client's ticks for good.
	 */
	public void close() {
		loop.close();
	}

	/**
	 * Returns a view of this player that reports each subscription it creates, for the worker to release the ticks of an
	 * extension whose binding failed.
	 *
	 * @param track receives every subscription the view creates
	 * @return the scoped view
	 */
	public @NotNull LocalPlayer scoped(@NotNull Consumer<Subscription> track) {
		return new ClientPosition(this, track);
	}

	/**
	 * Runs one tick of the client while the player has joined a level, as {@code Minecraft.tick} runs it.
	 */
	private void tickClient() {
		if (server.getEntityId() == null || world.level().isEmpty()) return;

		known.connectionTick(world);
		if (known.ticksPlayer(world)) ticks.forEach(ClientPosition::tickPlayer);
		if (protocol.supports(ServerboundClientTickEnd.class)) protocol.send(new ServerboundClientTickEnd());
		known.frame(world);
	}

	private static void tickPlayer(@NotNull Runnable tick) {
		try {
			tick.run();
		} catch (RuntimeException failure) {
			System.err.println("[Anvil] Warning: a tick of the client's player failed: " + failure);
		}
	}
}
