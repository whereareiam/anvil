package me.whereareiam.anvil.protocol.java.physics.world;

import me.whereareiam.anvil.protocol.java.block.BlockRegistry;
import me.whereareiam.anvil.protocol.java.block.JavaBlockData;
import me.whereareiam.anvil.protocol.java.block.model.collision.BlockOffset;
import me.whereareiam.anvil.protocol.java.geometry.Vector;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the offsets of offset blocks against the vanilla client's: each expected value is what the release's own
 * {@code Mth.getSeed} and {@code BlockState.getOffset} arithmetic gave at that column, run from its client jar.
 */
class PositionOffsetTest {
	@Test
	void movesBambooAndDripstoneAs26_3Does() {
		BlockRegistry registry = JavaBlockData.shipped().require(777);
		BlockOffset bamboo = offset(registry, "minecraft:bamboo");
		BlockOffset dripstone = offset(registry, "minecraft:pointed_dripstone");

		assertEquals(new Vector(-0.25, 0.0, -0.25), PositionOffset.at(bamboo, 0, 0));
		assertEquals(new Vector(0.15000000596046448, 0.0, -0.0833333283662796), PositionOffset.at(bamboo, 8, 8));
		assertEquals(new Vector(-0.0833333283662796, 0.0, -0.25), PositionOffset.at(bamboo, 123456, -98765));
		assertEquals(new Vector(0.125, 0.0, -0.0833333283662796), PositionOffset.at(dripstone, 8, 8));
		assertEquals(new Vector(-0.125, 0.0, 0.125), PositionOffset.at(dripstone, -30000000, 29999999));
	}

	@Test
	void movesGrassDownByTheVerticalOffsetOfItsRelease() {
		BlockOffset modern = offset(JavaBlockData.shipped().require(777), "minecraft:short_grass");
		BlockOffset legacy = offset(JavaBlockData.shipped().require(754), "minecraft:grass");

		assertEquals(new Vector(0.11666667461395264, -0.07999999642372124, 0.050000011920928955),
				PositionOffset.at(modern, -3, 17));
		assertEquals(new Vector(0.11666667461395264, -0.07999999523162843, 0.050000011920928955),
				PositionOffset.at(legacy, -3, 17));
		assertEquals(new Vector(-0.25, -0.013333332538604737, 0.15000000596046448),
				PositionOffset.at(legacy, -30000000, 29999999));
	}

	private static @NotNull BlockOffset offset(@NotNull BlockRegistry registry, @NotNull String block) {
		return Objects.requireNonNull(registry.block(block).orElseThrow().getOffset(), block + " has no offset");
	}
}
