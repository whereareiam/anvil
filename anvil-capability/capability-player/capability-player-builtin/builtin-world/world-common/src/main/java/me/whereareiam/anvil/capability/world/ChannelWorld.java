package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.model.BlockChange;
import me.whereareiam.anvil.capability.world.model.BlockCoordinates;
import me.whereareiam.anvil.capability.world.model.BlockReading;
import me.whereareiam.anvil.capability.world.model.KnownDimension;
import me.whereareiam.anvil.capability.world.model.ReceivedBlock;
import me.whereareiam.anvil.capability.world.model.ReceivedBlocks;
import me.whereareiam.anvil.capability.world.model.SeenBlock;
import me.whereareiam.anvil.capability.world.model.SeenChunk;
import me.whereareiam.anvil.capability.world.model.SeenDimension;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * World capability driven over a player's typed channel: reads of the dimension, the blocks and the loaded columns
 * ask the worker, and every block update the worker's world extension emits is kept in the player's history, across
 * reconnects, for watches to follow. The worker reports block states by their global IDs; this capability names them
 * when it hands a block out, through the block data of the player's release.
 *
 * <p>Updates arrive on the channel's thread while waits read them on the test's, so the history is guarded by this
 * object's monitor; reads copy what they return.</p>
 */
final class ChannelWorld implements World {
	private final ProtocolChannel channel;
	private final BlockNames names;
	private final List<Update> history = new ArrayList<>();

	ChannelWorld(@NotNull ProtocolChannel channel, @NotNull BlockNames names) {
		this.channel = channel;
		this.names = names;
		channel.subscribe(WorldChannel.CHANGED, this::record);
	}

	@Override
	public @NotNull Optional<SeenDimension> dimension() {
		KnownDimension known = Objects.requireNonNull(channel.request(WorldChannel.DIMENSION, null));
		return Optional.ofNullable(known.getDimension());
	}

	@Override
	public @NotNull Optional<SeenBlock> block(int x, int y, int z) {
		BlockCoordinates coordinates = new BlockCoordinates(x, y, z);
		BlockReading reading = Objects.requireNonNull(channel.request(WorldChannel.BLOCK, coordinates));
		if (!reading.isLoaded()) return Optional.empty();

		return Optional.of(names.block(x, y, z, reading.getStateId()));
	}

	@Override
	public @NotNull SeenBlock awaitBlock(
			int x,
			int y,
			int z,
			@NotNull Predicate<SeenBlock> matcher,
			@NotNull Duration timeout
	) {
		AtomicReference<SeenBlock> found = new AtomicReference<>();
		String expected = matcher instanceof BlockQuery query ? " " + query : " matching";
		channel.await(() -> {
			found.set(block(x, y, z).filter(matcher).orElse(null));
			return found.get() != null;
		}, "know the block at " + x + " " + y + " " + z + expected, timeout);

		return found.get();
	}

	@Override
	public boolean isChunkLoaded(int chunkX, int chunkZ) {
		return loadedChunks().contains(new SeenChunk(chunkX, chunkZ));
	}

	@Override
	public @NotNull Set<SeenChunk> loadedChunks() {
		return Set.copyOf(Objects.requireNonNull(channel.request(WorldChannel.CHUNKS, null)).getChunks());
	}

	@Override
	public boolean knowsBlockNames() {
		return names.known();
	}

	@Override
	public synchronized @NotNull WorldWatch watch() {
		return new Watch(history.size());
	}

	private synchronized void record(ReceivedBlocks received) {
		for (ReceivedBlock block : received.getBlocks())
			history.add(new Update(received.getConnection(), received.getDimension(), block));
	}

	/**
	 * Finds the first update from a position of the history that sets the block at a position to one satisfying a
	 * condition.
	 *
	 * @return the position of the update, or -1 when there is none yet
	 */
	private synchronized int find(int x, int y, int z, Predicate<SeenBlock> matcher, int from) {
		for (int index = from; index < history.size(); index++) {
			Update update = history.get(index);
			if (update.at(x, y, z) && matcher.test(change(update).getBlock())) return index;
		}

		return -1;
	}

	private synchronized BlockChange at(int index) {
		return change(history.get(index));
	}

	private synchronized List<BlockChange> from(int position) {
		return history.subList(Math.min(position, history.size()), history.size()).stream().map(this::change).toList();
	}

	private BlockChange change(Update update) {
		ReceivedBlock block = update.block();
		return new BlockChange(update.connection(), update.dimension(),
				names.block(block.getX(), block.getY(), block.getZ(), block.getStateId()));
	}

	private static String describe(int x, int y, int z, Predicate<SeenBlock> matcher) {
		String position = "see the block at " + x + " " + y + " " + z + " change";
		return matcher instanceof BlockQuery query ? position + " to " + query : position + " to a matching block";
	}

	/**
	 * One block an update set, in the order the player received it.
	 */
	private record Update(int connection, String dimension, ReceivedBlock block) {
		private boolean at(int x, int y, int z) {
			return block.getX() == x && block.getY() == y && block.getZ() == z;
		}
	}

	/**
	 * A watch over this player's history, which waits through the player's channel.
	 */
	private final class Watch implements WorldWatch {
		private int position;

		private Watch(int position) {
			this.position = position;
		}

		@Override
		public @NotNull SeenBlock changed(
				int x,
				int y,
				int z,
				@NotNull Predicate<SeenBlock> matcher,
				@NotNull Duration timeout
		) {
			channel.await(() -> find(x, y, z, matcher, position()) >= 0, describe(x, y, z, matcher), timeout);
			return take(x, y, z, matcher);
		}

		@Override
		public void unchanged(int x, int y, int z, @NotNull Duration duration) {
			Predicate<SeenBlock> any = BlockQuery.any();
			try {
				channel.await(() -> find(x, y, z, any, position()) >= 0, describe(x, y, z, any), duration);
			} catch (IllegalStateException quiet) {
				if (find(x, y, z, any, position()) < 0) return;
			}

			throw new IllegalStateException("Player saw the block at " + x + " " + y + " " + z
					+ " change, which must stay unchanged: an update set it to "
					+ at(find(x, y, z, any, position())).getBlock().describe());
		}

		@Override
		public @NotNull Expectation expectChanged(int x, int y, int z, @NotNull Predicate<SeenBlock> matcher) {
			return Expectation.of(describe(x, y, z, matcher), () -> find(x, y, z, matcher, position()) >= 0);
		}

		@Override
		public @NotNull List<BlockChange> unread() {
			return from(position());
		}

		private synchronized int position() {
			return position;
		}

		/**
		 * Moves the watch directly after the update found from its position, which a successful wait guarantees.
		 */
		private synchronized @NotNull SeenBlock take(int x, int y, int z, Predicate<SeenBlock> matcher) {
			int index = find(x, y, z, matcher, position);
			if (index < 0) throw new IllegalStateException("Player did not " + describe(x, y, z, matcher));

			position = index + 1;
			return at(index).getBlock();
		}
	}
}
