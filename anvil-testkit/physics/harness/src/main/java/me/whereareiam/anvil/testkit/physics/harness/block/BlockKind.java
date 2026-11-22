package me.whereareiam.anvil.testkit.physics.harness.block;

/**
 * What a block is where vanilla's movement code asks by its class, its identity or a tag: the names of
 * {@code data-block}'s {@code BlockKind}, which documents each. A family reads them from its client's own
 * classes, blocks and tags.
 */
public enum BlockKind {
	OTHER,
	AIR,
	FENCE_GATE,
	TRAPDOOR,
	LADDER,
	SCAFFOLDING,
	POWDER_SNOW,
	WATER,
	ICE,
	MOVING_PISTON
}
