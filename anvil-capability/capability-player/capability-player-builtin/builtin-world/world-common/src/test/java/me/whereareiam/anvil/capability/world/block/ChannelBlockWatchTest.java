package me.whereareiam.anvil.capability.world.block;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.world.channel.BlockReading;
import me.whereareiam.anvil.capability.world.channel.WatchedChanges;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.exception.UnknownBlockNameException;
import me.whereareiam.anvil.capability.world.model.block.BlockArea;
import me.whereareiam.anvil.capability.world.model.block.BlockChange;
import me.whereareiam.anvil.capability.world.model.block.BlockIdentity;
import me.whereareiam.anvil.capability.world.model.block.BlockPosition;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelBlockWatchTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final BlockPosition DOOR = new BlockPosition(12, 64, -3);
	private static final BlockPosition FLOOR = new BlockPosition(12, 63, -3);
	private static final BlockIdentity STONE = new BlockIdentity("minecraft:stone", Map.of(), false);
	private static final BlockIdentity AIR = new BlockIdentity("minecraft:air", Map.of(), true);

	private final TestProtocolChannel channel = new TestProtocolChannel(Set.of(WorldChannel.ID));
	private final List<Integer> closed = new ArrayList<>();
	private final ChannelBlockView blocks = new ChannelBlockView(channel);

	ChannelBlockWatchTest() {
		channel.answer(WorldChannel.WATCH, watch -> null);
		channel.answer(WorldChannel.UNWATCH, id -> {
			closed.add(id);
			return null;
		});
	}

	@Test
	void takesEachChangeOnceInTheOrderTheWorkerSentThem() {
		try (BlockWatch watch = blocks.watch(BlockArea.of(FLOOR, DOOR))) {
			changed(1, reading(DOOR, 1, STONE), reading(DOOR, 0, AIR));

			assertEquals(1, watch.changed(DOOR, BlockQuery.any()).getState());
			assertEquals(0, watch.changed(DOOR, BlockQuery.any()).getState());
			assertEquals(List.of(), watch.unread());
		}
	}

	@Test
	void keepsTheChangesAfterItsCursorWithTheirLevel() {
		try (BlockWatch watch = blocks.watch(BlockArea.of(FLOOR, DOOR))) {
			changed(1, reading(DOOR, 0, AIR), reading(FLOOR, 1, STONE));

			watch.changed(DOOR, BlockQuery.air());

			List<BlockChange> unread = watch.unread();
			assertEquals(1, unread.size());
			assertEquals(OVERWORLD, unread.getFirst().getLevel());
			assertEquals(FLOOR, unread.getFirst().getBlock().getPosition());
		}
	}

	@Test
	void seesOnlyTheChangesOfItsOwnWatch() {
		try (BlockWatch first = blocks.watch(BlockArea.of(DOOR));
		     BlockWatch second = blocks.watch(BlockArea.of(DOOR))) {
			changed(2, reading(DOOR, 1, STONE));

			assertEquals(List.of(), first.unread());
			assertEquals(1, second.unread().size());
		}
	}

	@Test
	void reportsABlockThatChangedWhileItMustNot() {
		try (BlockWatch watch = blocks.watch(BlockArea.of(FLOOR, DOOR))) {
			channel.meanwhile(() -> changed(1, reading(FLOOR, 0, AIR)));
			watch.neverChanged(DOOR, Duration.ofMillis(10));

			channel.meanwhile(() -> changed(1, reading(DOOR, 0, AIR)));
			IllegalStateException failure = assertThrows(IllegalStateException.class,
					() -> watch.neverChanged(DOOR, Duration.ofMillis(10)));

			assertTrue(failure.getMessage().contains("minecraft:air"), failure.getMessage());
		}
	}

	@Test
	void expectsAChangeWithoutWaiting() {
		try (BlockWatch watch = blocks.watch(BlockArea.of(DOOR))) {
			Expectation expectation = watch.expectChanged(DOOR, BlockQuery.air());
			assertFalse(expectation.met());

			changed(1, reading(DOOR, 0, AIR));

			assertTrue(expectation.met());
		}
	}

	@Test
	void refusesAPositionOutsideItsArea() {
		try (BlockWatch watch = blocks.watch(BlockArea.of(DOOR))) {
			assertThrows(IllegalArgumentException.class, () -> watch.changed(FLOOR, BlockQuery.any()));
			assertThrows(IllegalArgumentException.class, () -> watch.neverChanged(FLOOR, Duration.ZERO));
			assertThrows(IllegalArgumentException.class, () -> watch.expectChanged(FLOOR, BlockQuery.any()));
		}
	}

	@Test
	void failsAtOnceWhenAQueryNeedsANameTheBlockLacks() {
		try (BlockWatch watch = blocks.watch(BlockArea.of(DOOR))) {
			changed(1, reading(DOOR, 99999, null));

			assertThrows(UnknownBlockNameException.class, () -> watch.changed(DOOR, BlockQuery.named("stone")));
			assertEquals(99999, watch.changed(DOOR, BlockQuery.state(99999)).getState());
		}
	}

	@Test
	void closesOnceAndThenIgnoresItsChanges() {
		BlockWatch watch = blocks.watch(BlockArea.of(DOOR));

		watch.close();
		watch.close();
		changed(1, reading(DOOR, 0, AIR));

		assertEquals(List.of(1), closed);
		assertEquals(List.of(), watch.unread());
	}

	private void changed(int watch, BlockReading... blocks) {
		channel.publish(WorldChannel.CHANGES, new WatchedChanges(watch, OVERWORLD, List.of(blocks)));
	}

	private static BlockReading reading(BlockPosition position, int state, @Nullable BlockIdentity identity) {
		return new BlockReading(position, state, identity, 774);
	}
}
