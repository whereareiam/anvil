package me.whereareiam.anvil.protocol.java.block;

import me.whereareiam.anvil.protocol.java.block.model.BlockDataSource;
import me.whereareiam.anvil.protocol.java.block.model.BlockProperty;
import me.whereareiam.anvil.protocol.java.block.model.BlockState;
import me.whereareiam.anvil.protocol.java.block.model.BlockType;
import me.whereareiam.anvil.protocol.java.block.model.collision.CollisionBox;
import me.whereareiam.anvil.protocol.java.block.model.collision.CollisionShape;
import me.whereareiam.anvil.protocol.java.block.model.movement.BlockMovement;
import me.whereareiam.anvil.protocol.java.block.model.movement.StuckSpeed;
import me.whereareiam.anvil.protocol.java.block.type.BlockKind;
import me.whereareiam.anvil.protocol.java.block.type.CollisionKind;
import me.whereareiam.anvil.protocol.java.block.type.FluidType;
import me.whereareiam.anvil.protocol.java.block.type.MovementBehaviour;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlockDataCodecTest {
	private static final BlockDataSource SOURCE = BlockDataSource.builder()
			.repository("demo/data")
			.version("1.0.0")
			.commit("abc")
			.blocks("data/blocks.json")
			.collisionShapes("data/shapes.json")
			.build();
	private static final CollisionShape SLAB = new CollisionShape(List.of(new CollisionBox(0, 0, 0, 1, 0.5, 1)));

	@Test
	void readsTheRegistryItWrote() throws IOException {
		BlockRegistry written = registry();
		BlockRegistry read = BlockDataCodec.readRegistry(new ByteArrayInputStream(write(written)));

		assertEquals(766, read.protocol());
		assertEquals(SOURCE, read.source());
		assertEquals(written.blocks(), read.blocks());
		for (int stateId = 0; stateId < written.stateCount(); stateId++)
			assertEquals(written.state(stateId), read.state(stateId));
		assertSame(CollisionShape.EMPTY, read.state(0).getCollision());
	}

	@Test
	void readsTheIndexItWrote() throws IOException {
		JavaBlockData written = new JavaBlockData(Set.of(754, 766), Map.of(777, "demo/data 1.0.0 has no block data for it"));
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		BlockDataCodec.writeIndex(written, output);
		JavaBlockData read = BlockDataCodec.readIndex(new ByteArrayInputStream(output.toByteArray()));

		assertEquals(Set.of(754, 766), read.protocols());
		assertEquals(Set.of(777), read.missingProtocols());
		assertEquals("demo/data 1.0.0 has no block data for it", read.missingReason(777));
	}

	@Test
	void refusesARegistryWhoseStatesAreOutOfOrder() {
		BlockRegistry registry = registry();
		BlockState[] swapped = {registry.state(1), registry.state(0), registry.state(2)};

		assertThrows(IllegalArgumentException.class, () -> new BlockRegistry(766, SOURCE, registry.blocks(), swapped));
	}

	private static BlockRegistry registry() {
		BlockType air = BlockType.builder()
				.id(0).name("minecraft:air").minStateId(0).maxStateId(0).defaultStateId(0)
				.kind(BlockKind.AIR).collision(CollisionKind.FIXED)
				.movement(BlockMovement.builder().friction(0.6F).speedFactor(1.0F).jumpFactor(1.0F).build())
				.build();
		BlockType slab = BlockType.builder()
				.id(1).name("minecraft:honey_slab").minStateId(1).maxStateId(2).defaultStateId(2)
				.property(new BlockProperty("waterlogged", List.of("true", "false")))
				.kind(BlockKind.OTHER).collision(CollisionKind.DYNAMIC)
				.movement(BlockMovement.builder()
						.friction(0.98F).speedFactor(0.4F).jumpFactor(0.5F).bounceFactor(0.66F)
						.stuckSpeed(new StuckSpeed(0.25, 0.05F))
						.behaviour(MovementBehaviour.HONEY_SLIDE)
						.behaviour(MovementBehaviour.SLIME_STEP)
						.build())
				.build();
		BlockState[] states = {
				new BlockState(0, air, Map.of(), CollisionShape.EMPTY, FluidType.NONE),
				new BlockState(1, slab, slab.propertiesAt(1), SLAB, FluidType.WATER),
				new BlockState(2, slab, slab.propertiesAt(2), SLAB, FluidType.NONE)
		};

		return new BlockRegistry(766, SOURCE, List.of(air, slab), states);
	}

	private static byte[] write(BlockRegistry registry) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		BlockDataCodec.writeRegistry(registry, output);
		return output.toByteArray();
	}
}
