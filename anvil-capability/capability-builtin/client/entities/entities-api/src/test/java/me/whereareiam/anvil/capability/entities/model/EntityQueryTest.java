package me.whereareiam.anvil.capability.entities.model;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityQueryTest {
	private static final SeenEntity NAMED_STAND = entity("minecraft:armor_stand", "Alice: hello", null, 1.5, false);
	private static final SeenEntity RIDING_DISPLAY = entity("minecraft:text_display", null, "hello", 9.0, true);
	private static final SeenEntity FAR_ZOMBIE = entity("minecraft:zombie", "Bob", null, 20.0, false);

	@Test
	void matchesTheCustomNameOrTheDisplayText() {
		EntityQuery hello = EntityQuery.labelled("hello");

		assertTrue(hello.test(NAMED_STAND));
		assertTrue(hello.test(RIDING_DISPLAY));
		assertFalse(hello.test(FAR_ZOMBIE));
		assertEquals("labelled 'hello'", hello.toString());
	}

	@Test
	void matchesATypeWithOrWithoutItsNamespace() {
		assertTrue(EntityQuery.ofType("armor_stand").test(NAMED_STAND));
		assertTrue(EntityQuery.ofType("minecraft:text_display").test(RIDING_DISPLAY));
		assertFalse(EntityQuery.ofType("armor_stand").test(FAR_ZOMBIE));
		assertEquals("of type minecraft:armor_stand", EntityQuery.ofType("armor_stand").toString());
	}

	@Test
	void matchesWhatIsCloseToOrRidesThePlayer() {
		assertTrue(EntityQuery.nearPlayer(2).test(NAMED_STAND));
		assertFalse(EntityQuery.nearPlayer(2).test(RIDING_DISPLAY));
		assertFalse(EntityQuery.nearPlayer(100).test(entity("minecraft:zombie", null, null, null, false)));
		assertTrue(EntityQuery.ridingPlayer().test(RIDING_DISPLAY));
		assertEquals(List.of(true, true, false), List.of(EntityQuery.aroundPlayer(4).test(NAMED_STAND),
				EntityQuery.aroundPlayer(4).test(RIDING_DISPLAY), EntityQuery.aroundPlayer(4).test(FAR_ZOMBIE)));
	}

	@Test
	void combinesQueriesAndDescribesTheCombination() {
		EntityQuery bubble = EntityQuery.labelled("hello").and(EntityQuery.nearPlayer(4).or(EntityQuery.ridingPlayer()));
		EntityQuery visible = EntityQuery.any().and(entity -> !entity.isInvisible()).negate();

		assertTrue(bubble.test(NAMED_STAND));
		assertTrue(bubble.test(RIDING_DISPLAY));
		assertFalse(bubble.test(FAR_ZOMBIE));
		assertEquals("labelled 'hello' and (within 4.0 blocks of the player or riding the player)", bubble.toString());
		assertFalse(visible.test(NAMED_STAND));
		assertEquals("not (any entity and a condition)", visible.toString());
		assertEquals("a glowing zombie", EntityQuery.matching("a glowing zombie", entity -> true).toString());
	}

	private static SeenEntity entity(String type, @Nullable String customName, @Nullable String text, @Nullable Double distance,
	                                 boolean riding) {
		return SeenEntity.builder()
				.connection(1)
				.id(7)
				.type(type)
				.customName(customName)
				.text(text)
				.passengerIds(List.of())
				.ridingPlayer(riding)
				.playerDistance(distance)
				.build();
	}
}
