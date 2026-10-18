package me.whereareiam.anvil.protocol.java.data.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollisionShapeTest {
	private static final CollisionBox FULL = new CollisionBox(0, 0, 0, 1, 1, 1);

	@Test
	void theEmptyShapeHasNoBox() {
		assertTrue(CollisionShape.EMPTY.isEmpty());
		assertFalse(CollisionShape.EMPTY.isFullBlock());
	}

	@Test
	void onlyOneFullBoxIsAFullBlock() {
		assertTrue(new CollisionShape(List.of(FULL)).isFullBlock());
		assertFalse(new CollisionShape(List.of(new CollisionBox(0, 0, 0, 1, 0.5, 1))).isFullBlock());
		assertFalse(new CollisionShape(List.of(new CollisionBox(0, 0, 0, 1, 1.5, 1))).isFullBlock());
		assertFalse(new CollisionShape(List.of(FULL, FULL)).isFullBlock());
	}
}
