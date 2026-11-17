package me.whereareiam.anvil.capability.world.block;

import me.whereareiam.anvil.capability.world.channel.AreaWatch;
import me.whereareiam.anvil.capability.world.channel.BlockReading;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.model.block.Block;
import me.whereareiam.anvil.capability.world.model.block.BlockArea;
import me.whereareiam.anvil.capability.world.model.block.BlockPosition;
import me.whereareiam.anvil.capability.world.model.level.ChunkPosition;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * The block view of a player's World capability over its typed channel: reads ask the player's worker, which keeps
 * the blocks its client knows, and each watch opens an area on the worker under an ID of its own, so the worker sends
 * only the changes of open areas, which this view hands to the watch they name.
 */
public final class ChannelBlockView implements BlockView {
	private final ProtocolChannel channel;
	private final Map<Integer, ChannelBlockWatch> watches = new ConcurrentHashMap<>();
	private final AtomicInteger ids = new AtomicInteger();

	/**
	 * Creates the view and starts receiving the changes of its watches.
	 *
	 * @param channel the player's channel
	 */
	public ChannelBlockView(@NotNull ProtocolChannel channel) {
		this.channel = channel;
		channel.subscribe(WorldChannel.CHANGES, changes -> {
			ChannelBlockWatch watch = watches.get(changes.getWatch());
			if (watch != null) watch.receive(changes);
		});
	}

	@Override
	public @NotNull Optional<Block> block(@NotNull BlockPosition position) {
		return Optional.ofNullable(channel.request(WorldChannel.BLOCK, position)).map(ChannelBlockView::block);
	}

	@Override
	public @NotNull Block until(
			@NotNull BlockPosition position,
			@NotNull Predicate<Block> condition,
			@NotNull Duration timeout
	) {
		try (ChannelBlockWatch watch = open(BlockArea.of(position))) {
			Optional<Block> known = block(position);
			if (known.isPresent() && condition.test(known.get())) return known.get();

			return watch.changed(position, condition, timeout);
		}
	}

	@Override
	public boolean isLoaded(@NotNull ChunkPosition chunk) {
		return Boolean.TRUE.equals(channel.request(WorldChannel.CHUNK, chunk));
	}

	@Override
	public @NotNull Set<ChunkPosition> loadedChunks() {
		return Set.copyOf(channel.request(WorldChannel.CHUNKS, null).getChunks());
	}

	@Override
	public @NotNull BlockWatch watch(@NotNull BlockArea area) {
		return open(area);
	}

	/**
	 * Turns a block the worker read into the block a test sees.
	 *
	 * @param reading the worker's reading
	 * @return the block
	 */
	static @NotNull Block block(@NotNull BlockReading reading) {
		return new Block(reading.getPosition(), reading.getState(), reading.getIdentity(), reading.getProtocol());
	}

	/**
	 * Registers a watch before the worker starts sending its changes, so none is lost, and forgets it again when the
	 * worker refuses it.
	 */
	private ChannelBlockWatch open(BlockArea area) {
		int id = ids.incrementAndGet();
		ChannelBlockWatch watch = new ChannelBlockWatch(id, area, channel, this::closed);
		watches.put(id, watch);
		try {
			channel.request(WorldChannel.WATCH, new AreaWatch(id, area));
		} catch (RuntimeException refused) {
			watches.remove(id);
			throw refused;
		}

		return watch;
	}

	private void closed(int id) {
		watches.remove(id);
		channel.request(WorldChannel.UNWATCH, id);
	}
}
