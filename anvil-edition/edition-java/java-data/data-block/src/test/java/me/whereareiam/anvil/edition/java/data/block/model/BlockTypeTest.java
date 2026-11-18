package me.whereareiam.anvil.edition.java.data.block.model;

import me.whereareiam.anvil.edition.java.data.block.model.movement.BlockMovement;
import me.whereareiam.anvil.edition.java.data.block.type.BlockKind;
import me.whereareiam.anvil.edition.java.data.block.type.CollisionKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlockTypeTest {
	private final BlockType step = BlockType.builder()
			.id(1).name("minecraft:step").minStateId(10).maxStateId(15).defaultStateId(13)
			.property(new BlockProperty("half", List.of("top", "bottom", "middle")))
			.property(new BlockProperty("waterlogged", List.of("true", "false")))
			.kind(BlockKind.OTHER).collision(CollisionKind.FIXED)
			.movement(BlockMovement.builder().friction(0.6F).speedFactor(1.0F).jumpFactor(1.0F).build())
			.build();

	@Test
	void numbersStatesWithTheLastPropertyVaryingFastest() {
		assertEquals(Map.of("half", "top", "waterlogged", "true"), step.propertiesAt(10));
		assertEquals(Map.of("half", "top", "waterlogged", "false"), step.propertiesAt(11));
		assertEquals(Map.of("half", "bottom", "waterlogged", "true"), step.propertiesAt(12));
		assertEquals(Map.of("half", "middle", "waterlogged", "false"), step.propertiesAt(15));
		assertEquals(List.of("half", "waterlogged"), List.copyOf(step.propertiesAt(10).keySet()));
	}

	@Test
	void refusesAStateOfAnotherBlock() {
		assertThrows(IllegalArgumentException.class, () -> step.propertiesAt(16));
		assertThrows(IllegalArgumentException.class, () -> step.propertiesAt(9));
	}
}
