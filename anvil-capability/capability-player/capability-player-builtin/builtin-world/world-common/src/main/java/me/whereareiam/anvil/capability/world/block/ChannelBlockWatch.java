package me.whereareiam.anvil.capability.world.block;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.api.history.HistoryCursor;
import me.whereareiam.anvil.capability.world.channel.BlockReading;
import me.whereareiam.anvil.capability.world.channel.WatchedChanges;
import me.whereareiam.anvil.capability.world.model.block.Block;
import me.whereareiam.anvil.capability.world.model.block.BlockArea;
import me.whereareiam.anvil.capability.world.model.block.BlockChange;
import me.whereareiam.anvil.capability.world.model.block.BlockPosition;
import me.whereareiam.anvil.player.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * One open watch of an area: it keeps the changes the worker sent for it and reads them through a cursor, which each
 * wait advances past the change it returns.
 */
final class ChannelBlockWatch implements BlockWatch {
	private final int id;
	private final BlockArea area;
	private final ProtocolChannel channel;
	private final IntConsumer closer;
	private final List<BlockChange> changes = new CopyOnWriteArrayList<>();
	private final HistoryCursor<BlockChange> cursor = new HistoryCursor<>(() -> changes);
	private final AtomicBoolean closed = new AtomicBoolean();

	ChannelBlockWatch(int id, @NotNull BlockArea area, @NotNull ProtocolChannel channel, @NotNull IntConsumer closer) {
		this.id = id;
		this.area = area;
		this.channel = channel;
		this.closer = closer;
	}

	@Override
	public @NotNull BlockArea area() {
		return area;
	}

	@Override
	public @NotNull Block changed(
			@NotNull BlockPosition position,
			@NotNull Predicate<Block> condition,
			@NotNull Duration timeout
	) {
		Predicate<BlockChange> matcher = matcher(position, condition);
		channel.await(() -> cursor.find(matcher) != null, description(position, condition), timeout);

		return Objects.requireNonNull(cursor.take(matcher)).block();
	}

	@Override
	public void neverChanged(
			@NotNull BlockPosition position,
			@NotNull Predicate<Block> condition,
			@NotNull Duration duration
	) {
		Predicate<BlockChange> matcher = matcher(position, condition);
		try {
			channel.await(() -> cursor.find(matcher) != null, description(position, condition), duration);
		} catch (IllegalStateException quiet) {
			if (cursor.find(matcher) == null) return;
		}

		Block block = Objects.requireNonNull(cursor.find(matcher)).block();
		throw new IllegalStateException("Player saw the block at " + describe(position) + " change, which it must not: "
				+ "it became state " + block.state() + block.identity().map(name -> " (" + name.name() + ")")
				.orElse(""));
	}

	@Override
	public @NotNull Expectation expectChanged(@NotNull BlockPosition position, @NotNull Predicate<Block> condition) {
		Predicate<BlockChange> matcher = matcher(position, condition);
		return Expectation.of(description(position, condition), () -> cursor.find(matcher) != null);
	}

	@Override
	public @NotNull List<BlockChange> unread() {
		return cursor.unread();
	}

	@Override
	public void close() {
		if (closed.compareAndSet(false, true)) closer.accept(id);
	}

	/**
	 * Keeps the blocks of one packet's changes in the watch's area, in the order the worker sent them.
	 */
	void receive(@NotNull WatchedChanges received) {
		for (BlockReading reading : received.blocks())
			changes.add(new BlockChange(received.level(), ChannelBlockView.block(reading)));
	}

	private Predicate<BlockChange> matcher(BlockPosition position, Predicate<Block> condition) {
		if (!area.contains(position))
			throw new IllegalArgumentException("Block " + describe(position) + " lies outside the watched area from "
					+ describe(area.min()) + " to " + describe(area.max()));

		return change -> change.block().position().equals(position) && condition.test(change.block());
	}

	private static String description(BlockPosition position, Predicate<Block> condition) {
		String state = condition instanceof BlockQuery query ? query.toString() : "a matching state";
		return "see the block at " + describe(position) + " become " + state;
	}

	private static String describe(BlockPosition position) {
		return position.x() + " " + position.y() + " " + position.z();
	}
}
