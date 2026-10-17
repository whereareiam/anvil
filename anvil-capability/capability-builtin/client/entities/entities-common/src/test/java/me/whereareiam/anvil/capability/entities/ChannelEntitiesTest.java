package me.whereareiam.anvil.capability.entities;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.EntityQuery;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import me.whereareiam.anvil.capability.entities.type.EntityChangeKind;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelEntitiesTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	@Test
	void readsTheKnownEntitiesFromTheWorker() {
		RecordingChannel channel = new RecordingChannel();
		ChannelEntities entities = new ChannelEntities(channel);
		SeenEntity zombie = entity(1, 8, null);
		channel.known = List.of(zombie);

		assertEquals(List.of(zombie), entities.all());
		assertEquals(List.of(EntitiesOperations.KNOWN.getId()), channel.requests);
	}

	@Test
	void waitsUntilAKnownEntitySatisfiesAConditionNow() {
		RecordingChannel channel = new RecordingChannel();
		ChannelEntities entities = new ChannelEntities(channel);
		SeenEntity far = entity(1, 8, null).toBuilder().playerDistance(20.0).build();
		channel.known = List.of(far);

		assertEquals("Player did not know an entity within 3.0 blocks of the player", assertThrows(IllegalStateException.class,
				() -> entities.present(EntityQuery.nearPlayer(3), TIMEOUT)).getMessage());

		SeenEntity near = far.toBuilder().playerDistance(2.0).build();
		channel.known = List.of(near);
		assertEquals(near, entities.present(EntityQuery.nearPlayer(3), TIMEOUT));
		assertEquals("Player did not know a matching entity", assertThrows(IllegalStateException.class,
				() -> entities.present(entity -> entity.getId() == 9, TIMEOUT)).getMessage());
	}

	@Test
	void forgetsTheOlderConnectionOnceANewerOneReportsAndIgnoresItsLateChanges() {
		RecordingChannel channel = new RecordingChannel();
		ChannelEntities entities = new ChannelEntities(channel);
		SeenEntity old = entity(1, 7, "old");
		channel.appear(old);
		EntityWatch watch = entities.watch();

		SeenEntity fresh = entity(2, 7, "new");
		channel.appear(fresh);
		channel.appear(entity(1, 9, "late"));

		assertEquals(List.of(new EntityChange(EntityChangeKind.DISAPPEARED, old, null),
				new EntityChange(EntityChangeKind.APPEARED, fresh, null)), watch.unread());
		watch.disappeared(old, TIMEOUT);
	}

	@Test
	void findsAnEntityWhenAChangeMakesItMatchAndContinuesAfterIt() {
		RecordingChannel channel = new RecordingChannel();
		ChannelEntities entities = new ChannelEntities(channel);
		EntityWatch watch = entities.watch();
		SeenEntity unnamed = entity(1, 7, null);
		SeenEntity named = entity(1, 7, "Alice: hello");

		channel.appear(unnamed);
		channel.publish(new EntityChange(EntityChangeKind.CHANGED, named, unnamed));
		channel.appear(entity(1, 8, "Bob: hello"));

		assertEquals(named, watch.appeared(EntityQuery.labelled("hello"), TIMEOUT));
		assertEquals(8, watch.appeared(EntityQuery.labelled("hello"), TIMEOUT).getId());
		assertEquals("Player did not see an entity appear labelled 'hello'", assertThrows(IllegalStateException.class,
				() -> watch.appeared(EntityQuery.labelled("hello"), TIMEOUT)).getMessage());
	}

	@Test
	void doesNotFindAnEntityThatAlreadyMatchedBeforeAChange() {
		RecordingChannel channel = new RecordingChannel();
		ChannelEntities entities = new ChannelEntities(channel);
		SeenEntity named = entity(1, 7, "hello");
		channel.appear(named);
		EntityWatch watch = entities.watch();

		channel.publish(new EntityChange(EntityChangeKind.CHANGED, named.toBuilder().x(4).build(), named));

		assertEquals(1, watch.unread().size());
		assertEquals("Player did not see a matching entity appear", assertThrows(IllegalStateException.class,
				() -> watch.appeared(entity -> entity.labelContains("hello"), TIMEOUT)).getMessage());
	}

	@Test
	void waitsUntilAnEntityDisappearsWithoutMovingTheWatch() {
		RecordingChannel channel = new RecordingChannel();
		ChannelEntities entities = new ChannelEntities(channel);
		EntityWatch watch = entities.watch();
		SeenEntity first = entity(1, 7, "hello");
		channel.appear(first);
		channel.appear(entity(1, 8, "hello"));

		SeenEntity bubble = watch.appeared(EntityQuery.labelled("hello"), TIMEOUT);
		Expectation gone = watch.expectDisappeared(bubble);
		assertFalse(gone.met());
		assertEquals("Player did not see minecraft:armor_stand 7 labelled 'hello' disappear",
				assertThrows(IllegalStateException.class, () -> watch.disappeared(bubble, TIMEOUT)).getMessage());

		channel.publish(new EntityChange(EntityChangeKind.DISAPPEARED, first, null));
		assertTrue(gone.met());
		watch.disappeared(bubble, TIMEOUT);
		assertEquals(8, watch.appeared(EntityQuery.labelled("hello"), TIMEOUT).getId());
	}

	@Test
	void treatsAnotherEntityUnderTheSameIdAsGone() {
		RecordingChannel channel = new RecordingChannel();
		ChannelEntities entities = new ChannelEntities(channel);
		SeenEntity stand = entity(1, 7, "hello");
		channel.appear(stand);

		channel.publish(new EntityChange(EntityChangeKind.DISAPPEARED, stand, null));
		channel.appear(stand.toBuilder().uniqueId(new UUID(0, 99)).build());

		entities.watch().disappeared(stand, TIMEOUT);
	}

	@Test
	void expectsAnAppearanceOnlySinceThePreviousWait() {
		RecordingChannel channel = new RecordingChannel();
		ChannelEntities entities = new ChannelEntities(channel);
		channel.appear(entity(1, 7, "hello"));
		EntityWatch watch = entities.watch();
		Expectation hello = watch.expectAppeared(EntityQuery.labelled("hello"));

		assertFalse(hello.met());
		assertEquals("see an entity appear labelled 'hello'", hello.getDescription());

		channel.appear(entity(1, 8, "hello"));
		assertTrue(hello.met());
		assertEquals(8, watch.appeared(EntityQuery.labelled("hello"), TIMEOUT).getId());
		assertFalse(hello.met());
	}

	@Test
	void failsWhenAForbiddenEntityAppearsSinceThePreviousWaitOnly() {
		RecordingChannel channel = new RecordingChannel();
		ChannelEntities entities = new ChannelEntities(channel);
		EntityWatch watch = entities.watch();

		watch.neverAppeared(EntityQuery.labelled("hello"), TIMEOUT);

		channel.appear(entity(1, 7, "hello"));
		assertEquals("Player saw an entity appear that must not appear: minecraft:armor_stand 7 labelled 'hello'",
				assertThrows(IllegalStateException.class, () -> watch.neverAppeared(EntityQuery.labelled("hello"), TIMEOUT)).getMessage());

		watch.appeared(EntityQuery.labelled("hello"), TIMEOUT);
		watch.neverAppeared(EntityQuery.labelled("hello"), TIMEOUT);
	}

	private static SeenEntity entity(int connection, int id, @Nullable String customName) {
		return SeenEntity.builder()
				.connection(connection)
				.id(id)
				.uniqueId(new UUID(0, id))
				.type("minecraft:armor_stand")
				.customName(customName)
				.passengerIds(List.of())
				.build();
	}

	/**
	 * Answers reads of the known entities with the entities the test set and delivers published events synchronously;
	 * a wait whose condition does not hold fails immediately instead of timing out.
	 */
	private static final class RecordingChannel implements CapabilityChannel {
		private final Map<String, List<Consumer<Object>>> listeners = new HashMap<>();
		private final List<String> requests = new ArrayList<>();
		private List<SeenEntity> known = List.of();

		private void appear(SeenEntity entity) {
			publish(new EntityChange(EntityChangeKind.APPEARED, entity, null));
		}

		private void publish(EntityChange change) {
			listeners.getOrDefault(EntitiesOperations.CHANGED.getId(), List.of()).forEach(listener -> listener.accept(change));
		}

		@Override
		public <Q, R> @Nullable R request(@NotNull ChannelOperation<Q, R> channelOperation, @Nullable Q request) {
			requests.add(channelOperation.getId());
			return channelOperation.getResponseType().cast(new KnownEntities(known));
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
			return Set.of(EntitiesProvider.ID);
		}
	}
}
