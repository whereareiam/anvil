package me.whereareiam.anvil.capability.world.java;

import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.model.BlockCoordinates;
import me.whereareiam.anvil.capability.world.model.BlockReading;
import me.whereareiam.anvil.capability.world.model.KnownDimension;
import me.whereareiam.anvil.capability.world.model.ReceivedBlock;
import me.whereareiam.anvil.capability.world.model.ReceivedBlocks;
import me.whereareiam.anvil.capability.world.model.SeenChunk;
import me.whereareiam.anvil.capability.world.model.SeenDimension;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.JavaWorld;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockChange;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundBlockUpdate;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSectionBlocksUpdate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaWorldExtensionTest {
	private static final Dimension OVERWORLD = new Dimension("minecraft:overworld", -64, 384);

	private final JavaWorldExtension extension = new JavaWorldExtension();

	@Test
	void isDiscoveredAsTheEditionWideWorldExtension() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaWorldExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.world", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void readsTheDimensionTheBlocksAndTheChunksOfThePlayersWorld() {
		RecordingWorld world = new RecordingWorld();
		RecordingOperations operations = new RecordingOperations();
		extension.bind(new RecordingPlayer(new RecordingProtocol(world), world), operations);
		assertEquals(new KnownDimension(null), operations.execute(WorldChannel.DIMENSION, null));
		assertEquals(new BlockReading(false, 0), operations.execute(WorldChannel.BLOCK, new BlockCoordinates(1, 64, 1)));

		world.dimension = OVERWORLD;
		world.load(new ChunkPosition(0, 0));
		world.states.put(position(1, 64, 1), 9);

		assertEquals(new KnownDimension(new SeenDimension("minecraft:overworld", -64, 384)),
				operations.execute(WorldChannel.DIMENSION, null));
		assertEquals(new BlockReading(true, 9), operations.execute(WorldChannel.BLOCK, new BlockCoordinates(1, 64, 1)));
		assertEquals(new BlockReading(true, 0), operations.execute(WorldChannel.BLOCK, new BlockCoordinates(2, 64, 1)));
		assertEquals(List.of(new SeenChunk(0, 0)), operations.execute(WorldChannel.CHUNKS, null).getChunks());
		assertEquals(List.of("world.dimension", "world.block", "world.chunks"), List.copyOf(operations.handlers.keySet()));
	}

	@Test
	void emitsTheBlocksEachUpdateSetNumberedByConnection() {
		RecordingWorld world = new RecordingWorld();
		RecordingProtocol protocol = new RecordingProtocol(world);
		RecordingPlayer player = new RecordingPlayer(protocol, world);
		extension.bind(player, new RecordingOperations());
		world.dimension = OVERWORLD;
		world.load(new ChunkPosition(0, 0));

		player.open();
		protocol.deliver(new ClientboundBlockUpdate(change(1, 64, 1, 9)));
		player.open();
		protocol.deliver(new ClientboundSectionBlocksUpdate(List.of(change(2, 64, 1, 10), change(3, 64, 1, 11))));

		assertEquals(List.of(
				new ReceivedBlocks(1, "minecraft:overworld", List.of(new ReceivedBlock(1, 64, 1, 9))),
				new ReceivedBlocks(2, "minecraft:overworld", List.of(new ReceivedBlock(2, 64, 1, 10),
						new ReceivedBlock(3, 64, 1, 11)))
		), player.emitted);
	}

	@Test
	void leavesOutTheBlocksOfColumnsTheClientDoesNotHave() {
		RecordingWorld world = new RecordingWorld();
		RecordingProtocol protocol = new RecordingProtocol(world);
		RecordingPlayer player = new RecordingPlayer(protocol, world);
		extension.bind(player, new RecordingOperations());
		player.open();
		protocol.deliver(new ClientboundBlockUpdate(change(1, 64, 1, 9)));
		world.dimension = OVERWORLD;
		world.load(new ChunkPosition(0, 0));

		protocol.deliver(new ClientboundBlockUpdate(change(40, 64, 1, 9)));
		protocol.deliver(new ClientboundSectionBlocksUpdate(List.of(change(40, 64, 1, 9), change(1, 64, 1, 12))));

		assertEquals(List.of(new ReceivedBlocks(1, "minecraft:overworld", List.of(new ReceivedBlock(1, 64, 1, 12)))),
				player.emitted);
	}

	@Test
	void stopsEmittingOnceTheBindingCloses() {
		RecordingWorld world = new RecordingWorld();
		RecordingProtocol protocol = new RecordingProtocol(world);
		RecordingPlayer player = new RecordingPlayer(protocol, world);
		WorkerBinding binding = extension.bind(player, new RecordingOperations());
		world.dimension = OVERWORLD;
		world.load(new ChunkPosition(0, 0));
		player.open();

		binding.close();
		protocol.deliver(new ClientboundBlockUpdate(change(1, 64, 1, 9)));

		assertTrue(player.emitted.isEmpty());
		assertTrue(protocol.listeners.isEmpty());
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocolOrKeepItsWorld() {
		RecordingWorld world = new RecordingWorld();

		assertThrows(ProtocolUnavailableException.class,
				() -> extension.bind(new RecordingPlayer(null, world), new RecordingOperations()));
		assertThrows(ProtocolUnavailableException.class,
				() -> extension.bind(new RecordingPlayer(new RecordingProtocol(world), null), new RecordingOperations()));
	}

	private static BlockChange change(int x, int y, int z, int state) {
		return new BlockChange(position(x, y, z), state);
	}

	private static BlockPosition position(int x, int y, int z) {
		return BlockPosition.builder().x(x).y(y).z(z).build();
	}

	/**
	 * The player's world as a library's dispatcher keeps it: loaded columns and the states of the blocks in them,
	 * every other block of a loaded column being air.
	 */
	private static final class RecordingWorld implements JavaWorld {
		private final Set<ChunkPosition> chunks = new HashSet<>();
		private final Map<BlockPosition, Integer> states = new HashMap<>();
		private @Nullable Dimension dimension;

		private void load(ChunkPosition chunk) {
			chunks.add(chunk);
		}

		private void apply(BlockChange change) {
			BlockPosition position = change.getPosition();
			if (blockState(position).isEmpty()) return;

			states.put(position, change.getBlockState());
		}

		@Override
		public @NotNull Optional<Dimension> dimension() {
			return Optional.ofNullable(dimension);
		}

		@Override
		public boolean isLoaded(@NotNull ChunkPosition chunk) {
			return chunks.contains(chunk);
		}

		@Override
		public @NotNull Set<ChunkPosition> loadedChunks() {
			return Set.copyOf(chunks);
		}

		@Override
		public @NotNull OptionalInt blockState(int x, int y, int z) {
			if (dimension == null || !dimension.contains(y) || !isLoaded(ChunkPosition.containing(x, z)))
				return OptionalInt.empty();

			return OptionalInt.of(states.getOrDefault(position(x, y, z), 0));
		}
	}

	/**
	 * A protocol that applies each delivered block update to the player's world before its listeners see it, as a
	 * library's dispatcher does.
	 */
	private static final class RecordingProtocol implements JavaProtocol {
		private final RecordingWorld world;
		private final Map<Class<?>, Consumer<Object>> listeners = new LinkedHashMap<>();

		private RecordingProtocol(RecordingWorld world) {
			this.world = world;
		}

		@Override
		public <P extends ClientboundPacket> @NotNull Subscription listen(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			listeners.put(type, packet -> listener.accept(type.cast(packet)));
			return () -> listeners.remove(type);
		}

		@Override
		public void send(@NotNull ServerboundPacket packet) {
			throw new AssertionError("The world sends no packets");
		}

		@Override
		public <P extends ServerboundPacket> @NotNull Subscription listenSent(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			throw new AssertionError("The world follows no sent packets");
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return true;
		}

		private void deliver(ClientboundPacket packet) {
			switch (packet) {
				case ClientboundBlockUpdate update -> world.apply(update.getChange());
				case ClientboundSectionBlocksUpdate update -> update.getChanges().forEach(world::apply);
				default -> throw new AssertionError("The test delivers only block updates");
			}
			Consumer<Object> listener = listeners.get(packet.getClass());
			if (listener != null) listener.accept(packet);
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
			handlers.put(operation.getId(), (Function<Object, Object>) handler);
		}

		private <Q, R> R execute(OperationDescriptor<Q, R> operation, @Nullable Q request) {
			return operation.getResponseType().cast(handlers.get(operation.getId()).apply(request));
		}
	}

	/**
	 * A player that speaks the given protocol and keeps the given world, or none, whose connections the test opens
	 * one after another; opening a connection ends the previous one.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final @Nullable JavaProtocol protocol;
		private final @Nullable JavaWorld world;
		private final List<ReceivedBlocks> emitted = new ArrayList<>();
		private @Nullable Function<Object, Subscription> generations;
		private @Nullable Subscription current;

		private RecordingPlayer(@Nullable JavaProtocol protocol, @Nullable JavaWorld world) {
			this.protocol = protocol;
			this.world = world;
		}

		private void open() {
			if (current != null) current.close();
			current = generations.apply(new Object());
		}

		@Override
		public <P> @NotNull P protocol(@NotNull Class<P> type) {
			Object spoken = type == JavaWorld.class ? world : type == JavaProtocol.class ? protocol : null;
			if (spoken == null) return PlayerBindingContext.super.protocol(type);

			return type.cast(spoken);
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			generations = listener;
			return () -> {
				generations = null;
				if (current != null) current.close();
				current = null;
			};
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
			assertSame(WorldChannel.CHANGED, event);
			emitted.add((ReceivedBlocks) payload);
		}

		@Override
		public @NotNull String name() {
			return "Alice";
		}

		@Override
		public @NotNull UUID uniqueId() {
			return UUID.nameUUIDFromBytes("Alice".getBytes());
		}

		@Override
		public void connect() {
			throw new AssertionError("The world does not drive the lifecycle");
		}

		@Override
		public void disconnect() {
			throw new AssertionError("The world does not drive the lifecycle");
		}

		@Override
		public void rejoin() {
			throw new AssertionError("The world does not drive the lifecycle");
		}

		@Override
		public @NotNull Object nativeSession() {
			throw new AssertionError("The world uses the protocol, not the native session");
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			throw new AssertionError("The world uses the protocol, not the native session");
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			throw new AssertionError("The world does not use the view rotation");
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			throw new AssertionError("The world does not change the view rotation");
		}
	}
}
