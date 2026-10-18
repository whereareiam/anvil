package me.whereareiam.anvil.capability.world.java;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.model.BlockCoordinates;
import me.whereareiam.anvil.capability.world.model.BlockReading;
import me.whereareiam.anvil.capability.world.model.KnownDimension;
import me.whereareiam.anvil.capability.world.model.LoadedChunks;
import me.whereareiam.anvil.capability.world.model.ReceivedBlock;
import me.whereareiam.anvil.capability.world.model.ReceivedBlocks;
import me.whereareiam.anvil.capability.world.model.SeenChunk;
import me.whereareiam.anvil.capability.world.model.SeenDimension;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.JavaWorld;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockChange;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundBlockUpdate;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSectionBlocksUpdate;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Installs the built-in world capability for players that speak the Java Edition protocol. It answers reads of the
 * level, the blocks and the loaded chunk columns from the player's {@link JavaWorld}, and emits the blocks each block
 * update set, numbered by the player's connection. The world has already applied an update when its listeners see
 * it, so an update of a column the client does not have, which the world ignores as the client does, is not emitted
 * either. Block states stay global IDs; the host names them.
 */
public final class JavaWorldExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return WorldChannel.ID;
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
		JavaProtocol protocol = player.protocol(JavaProtocol.class);
		Binding binding = new Binding(player, player.protocol(JavaWorld.class));
		operations.register(WorldChannel.DIMENSION, ignored -> binding.dimension());
		operations.register(WorldChannel.BLOCK, binding::block);
		operations.register(WorldChannel.CHUNKS, ignored -> binding.chunks());

		return binding.listen(protocol);
	}

	/**
	 * One player's reads of its world and the listeners that report its block updates.
	 */
	@RequiredArgsConstructor
	private static final class Binding {
		private final PlayerBindingContext<Object> player;
		private final JavaWorld world;
		private final AtomicInteger connection = new AtomicInteger();

		private KnownDimension dimension() {
			Optional<Dimension> dimension = world.dimension();
			return new KnownDimension(dimension
					.map(level -> new SeenDimension(level.getKey(), level.getMinY(), level.getHeight()))
					.orElse(null));
		}

		private BlockReading block(BlockCoordinates coordinates) {
			OptionalInt state = world.blockState(coordinates.getX(), coordinates.getY(), coordinates.getZ());
			return new BlockReading(state.isPresent(), state.orElse(0));
		}

		private LoadedChunks chunks() {
			return new LoadedChunks(world.loadedChunks().stream()
					.map(chunk -> new SeenChunk(chunk.getX(), chunk.getZ()))
					.toList());
		}

		private WorkerBinding listen(JavaProtocol protocol) {
			// A connection only takes the next number; nothing of it needs closing when it ends.
			Subscription connections = player.bindConnection(() -> {
				connection.incrementAndGet();
				return () -> { };
			});
			Subscription single = protocol.listen(ClientboundBlockUpdate.class,
					update -> emit(List.of(update.getChange())));
			Subscription section = protocol.listen(ClientboundSectionBlocksUpdate.class,
					update -> emit(update.getChanges()));
			Subscription binding = Subscription.all(single, section, connections);

			return binding::close;
		}

		/**
		 * Emits the blocks of one update that the world applied: those in a column the client has, inside the level.
		 */
		private void emit(List<BlockChange> changes) {
			Optional<Dimension> dimension = world.dimension();
			if (dimension.isEmpty()) return;

			List<ReceivedBlock> applied = changes.stream()
					.filter(change -> world.blockState(change.getPosition()).isPresent())
					.map(Binding::received)
					.toList();
			if (applied.isEmpty()) return;

			player.emit(WorldChannel.CHANGED, new ReceivedBlocks(connection.get(), dimension.get().getKey(), applied));
		}

		private static ReceivedBlock received(BlockChange change) {
			BlockPosition position = change.getPosition();
			return new ReceivedBlock(position.getX(), position.getY(), position.getZ(), change.getBlockState());
		}
	}
}
