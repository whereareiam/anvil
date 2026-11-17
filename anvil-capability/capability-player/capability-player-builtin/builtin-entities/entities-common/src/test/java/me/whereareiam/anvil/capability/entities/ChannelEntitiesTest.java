package me.whereareiam.anvil.capability.entities;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.entities.channel.EntitiesChannel;
import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.KnownEntities;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import me.whereareiam.anvil.capability.entities.type.EntityChangeKind;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelEntitiesTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	private final TestProtocolChannel channel = new TestProtocolChannel(Set.of(EntitiesChannel.ID));
	private final ChannelEntities entities = new ChannelEntities(channel);

	ChannelEntitiesTest() {
		know();
	}

	@Test
	void readsTheKnownEntitiesFromTheWorker() {
		SeenEntity zombie = entity(1, 8, null);
		know(zombie);

		assertEquals(List.of(zombie), entities.all());
		assertEquals(List.of(EntitiesChannel.KNOWN.getId()), channel.requests());
	}

	@Test
	void waitsUntilAKnownEntitySatisfiesAConditionNow() {
		SeenEntity far = entity(1, 8, null).toBuilder().playerDistance(20.0).build();
		know(far);

		assertEquals("Player did not know an entity within 3.0 blocks of the player", assertThrows(IllegalStateException.class,
				() -> entities.present(EntityQuery.nearPlayer(3), TIMEOUT)).getMessage());

		SeenEntity near = far.toBuilder().playerDistance(2.0).build();
		know(near);
		assertEquals(near, entities.present(EntityQuery.nearPlayer(3), TIMEOUT));
		assertEquals("Player did not know a matching entity", assertThrows(IllegalStateException.class,
				() -> entities.present(entity -> entity.getId() == 9, TIMEOUT)).getMessage());
	}

	@Test
	void forgetsTheOlderConnectionOnceANewerOneReportsAndIgnoresItsLateChanges() {
		SeenEntity old = entity(1, 7, "old");
		appear(old);
		EntityWatch watch = entities.watch();

		SeenEntity fresh = entity(2, 7, "new");
		appear(fresh);
		appear(entity(1, 9, "late"));

		assertEquals(List.of(new EntityChange(EntityChangeKind.DISAPPEARED, old, null),
				new EntityChange(EntityChangeKind.APPEARED, fresh, null)), watch.unread());
		watch.disappeared(old, TIMEOUT);
	}

	@Test
	void findsAnEntityWhenAChangeMakesItMatchAndContinuesAfterIt() {
		EntityWatch watch = entities.watch();
		SeenEntity unnamed = entity(1, 7, null);
		SeenEntity named = entity(1, 7, "Alice: hello");

		appear(unnamed);
		channel.publish(EntitiesChannel.CHANGED, new EntityChange(EntityChangeKind.CHANGED, named, unnamed));
		appear(entity(1, 8, "Bob: hello"));

		assertEquals(named, watch.appeared(EntityQuery.labelled("hello"), TIMEOUT));
		assertEquals(8, watch.appeared(EntityQuery.labelled("hello"), TIMEOUT).getId());
		assertEquals("Player did not see an entity appear labelled 'hello'", assertThrows(IllegalStateException.class,
				() -> watch.appeared(EntityQuery.labelled("hello"), TIMEOUT)).getMessage());
	}

	@Test
	void doesNotFindAnEntityThatAlreadyMatchedBeforeAChange() {
		SeenEntity named = entity(1, 7, "hello");
		appear(named);
		EntityWatch watch = entities.watch();

		channel.publish(EntitiesChannel.CHANGED, new EntityChange(EntityChangeKind.CHANGED, named.toBuilder().x(4).build(), named));

		assertEquals(1, watch.unread().size());
		assertEquals("Player did not see a matching entity appear", assertThrows(IllegalStateException.class,
				() -> watch.appeared(entity -> entity.labelContains("hello"), TIMEOUT)).getMessage());
	}

	@Test
	void waitsUntilAnEntityDisappearsWithoutMovingTheWatch() {
		EntityWatch watch = entities.watch();
		SeenEntity first = entity(1, 7, "hello");
		appear(first);
		appear(entity(1, 8, "hello"));

		SeenEntity bubble = watch.appeared(EntityQuery.labelled("hello"), TIMEOUT);
		Expectation gone = watch.expectDisappeared(bubble);
		assertFalse(gone.met());
		assertEquals("Player did not see minecraft:armor_stand 7 labelled 'hello' disappear",
				assertThrows(IllegalStateException.class, () -> watch.disappeared(bubble, TIMEOUT)).getMessage());

		channel.publish(EntitiesChannel.CHANGED, new EntityChange(EntityChangeKind.DISAPPEARED, first, null));
		assertTrue(gone.met());
		watch.disappeared(bubble, TIMEOUT);
		assertEquals(8, watch.appeared(EntityQuery.labelled("hello"), TIMEOUT).getId());
	}

	@Test
	void treatsAnotherEntityUnderTheSameIdAsGone() {
		SeenEntity stand = entity(1, 7, "hello");
		appear(stand);

		channel.publish(EntitiesChannel.CHANGED, new EntityChange(EntityChangeKind.DISAPPEARED, stand, null));
		appear(stand.toBuilder().uniqueId(new UUID(0, 99)).build());

		entities.watch().disappeared(stand, TIMEOUT);
	}

	@Test
	void expectsAnAppearanceOnlySinceThePreviousWait() {
		appear(entity(1, 7, "hello"));
		EntityWatch watch = entities.watch();
		Expectation hello = watch.expectAppeared(EntityQuery.labelled("hello"));

		assertFalse(hello.met());
		assertEquals("see an entity appear labelled 'hello'", hello.getDescription());

		appear(entity(1, 8, "hello"));
		assertTrue(hello.met());
		assertEquals(8, watch.appeared(EntityQuery.labelled("hello"), TIMEOUT).getId());
		assertFalse(hello.met());
	}

	@Test
	void failsWhenAForbiddenEntityAppearsSinceThePreviousWaitOnly() {
		EntityWatch watch = entities.watch();

		watch.neverAppeared(EntityQuery.labelled("hello"), TIMEOUT);

		appear(entity(1, 7, "hello"));
		assertEquals("Player saw an entity appear that must not appear: minecraft:armor_stand 7 labelled 'hello'",
				assertThrows(IllegalStateException.class, () -> watch.neverAppeared(EntityQuery.labelled("hello"), TIMEOUT)).getMessage());

		watch.appeared(EntityQuery.labelled("hello"), TIMEOUT);
		watch.neverAppeared(EntityQuery.labelled("hello"), TIMEOUT);
	}

	private void know(SeenEntity... known) {
		channel.answer(EntitiesChannel.KNOWN, ignored -> new KnownEntities(List.of(known)));
	}

	private void appear(SeenEntity entity) {
		channel.publish(EntitiesChannel.CHANGED, new EntityChange(EntityChangeKind.APPEARED, entity, null));
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
}
