package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.model.DimensionChange;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelWorldTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";

	private final RecordingChannel channel = new RecordingChannel();
	private final ChannelWorld world = new ChannelWorld(channel);

	@Test
	void keepsEveryChangeInOrderAndTheDimensionOfTheLast() {
		assertEquals(Optional.empty(), world.dimension());

		channel.publish(WorldChannel.DIMENSION, new DimensionChange(null, OVERWORLD));
		channel.publish(WorldChannel.DIMENSION, new DimensionChange(OVERWORLD, NETHER));

		assertEquals(Optional.of(NETHER), world.dimension());
		assertEquals(List.of(new DimensionChange(null, OVERWORLD), new DimensionChange(OVERWORLD, NETHER)), world.changes());
		assertThrows(UnsupportedOperationException.class, () -> world.changes().clear());
	}

	@Test
	void isInNoDimensionOnceTheConnectionLeftItAndKeepsTheHistory() {
		channel.publish(WorldChannel.DIMENSION, new DimensionChange(null, NETHER));
		channel.publish(WorldChannel.LEFT, NETHER);

		assertEquals(Optional.empty(), world.dimension());
		assertEquals(List.of(new DimensionChange(null, NETHER)), world.changes());
	}

	@Test
	void entersADimensionByJoiningOrComingFromAnotherButNotByRespawningInIt() {
		DimensionWatch dimensions = world.watch();

		channel.publish(WorldChannel.DIMENSION, new DimensionChange(null, OVERWORLD));
		assertEquals(new DimensionChange(null, OVERWORLD), dimensions.entered(OVERWORLD, TIMEOUT));

		channel.publish(WorldChannel.DIMENSION, new DimensionChange(OVERWORLD, OVERWORLD));
		assertEquals("Player did not enter a matching dimension", assertThrows(IllegalStateException.class,
				() -> dimensions.entered(OVERWORLD, TIMEOUT)).getMessage());

		channel.publish(WorldChannel.DIMENSION, new DimensionChange(OVERWORLD, NETHER));
		assertEquals(new DimensionChange(OVERWORLD, NETHER), dimensions.entered("the_nether", TIMEOUT));
		assertEquals(List.of(), dimensions.unread());
	}

	@Test
	void watchesOnlyChangesSinceItAndContinuesAfterEachMatch() {
		channel.publish(WorldChannel.DIMENSION, new DimensionChange(null, NETHER));
		DimensionWatch dimensions = world.watch();

		assertThrows(IllegalStateException.class, () -> dimensions.entered(NETHER, TIMEOUT));

		channel.publish(WorldChannel.DIMENSION, new DimensionChange(NETHER, OVERWORLD));
		channel.publish(WorldChannel.DIMENSION, new DimensionChange(OVERWORLD, NETHER));
		assertEquals(new DimensionChange(OVERWORLD, NETHER),
				dimensions.changed(change -> NETHER.equals(change.getTo()), TIMEOUT));
		assertThrows(IllegalStateException.class, () -> dimensions.changed(change -> true, TIMEOUT));
	}

	@Test
	void failsWhenTheForbiddenDimensionWasEnteredAndPassesOtherwise() {
		DimensionWatch dimensions = world.watch();
		channel.publish(WorldChannel.DIMENSION, new DimensionChange(null, OVERWORLD));

		dimensions.neverEntered(NETHER, TIMEOUT);
		assertEquals("Player made a dimension change it must not make: joined minecraft:overworld",
				assertThrows(IllegalStateException.class, () -> dimensions.neverEntered(OVERWORLD, TIMEOUT)).getMessage());

		channel.publish(WorldChannel.DIMENSION, new DimensionChange(OVERWORLD, NETHER));
		assertEquals("Player made a dimension change it must not make: from minecraft:overworld to minecraft:the_nether",
				assertThrows(IllegalStateException.class, () -> dimensions.neverEntered(NETHER, TIMEOUT)).getMessage());
		assertEquals(2, dimensions.unread().size());
	}

	@Test
	void expectsAChangeForWaitingOnSeveralThings() {
		DimensionWatch dimensions = world.watch();
		Expectation nether = dimensions.expectChanged(DimensionWatch.entering(NETHER));

		assertFalse(nether.met());
		channel.publish(WorldChannel.DIMENSION, new DimensionChange(null, NETHER));
		assertTrue(nether.met());
	}

	/**
	 * Delivers published events synchronously; a wait whose condition does not hold fails immediately instead of
	 * timing out.
	 */
	private static final class RecordingChannel implements ProtocolChannel {
		private final Map<String, List<Consumer<Object>>> listeners = new HashMap<>();

		private <E> void publish(EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			listeners.getOrDefault(eventDescriptor.getId(), List.of()).forEach(listener -> listener.accept(payload));
		}

		@Override
		public <Q, R> @Nullable R request(@NotNull OperationDescriptor<Q, R> operation, @Nullable Q request) {
			throw new AssertionError("The world sends no requests");
		}

		@Override
		@SuppressWarnings("unchecked")
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
			listeners.computeIfAbsent(eventDescriptor.getId(), ignored -> new ArrayList<>()).add((Consumer<Object>) listener);
			return () -> listeners.get(eventDescriptor.getId()).remove(listener);
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			if (!condition.getAsBoolean()) throw new IllegalStateException("Player did not " + description);
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(WorldChannel.ID);
		}
	}
}
