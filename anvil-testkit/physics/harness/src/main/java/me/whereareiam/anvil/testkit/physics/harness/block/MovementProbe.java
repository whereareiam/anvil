package me.whereareiam.anvil.testkit.physics.harness.block;

import java.util.Set;

/**
 * Reads what a bootstrapped vanilla client's blocks do to a moving entity, for {@link BlockTableReader}, as part of a
 * {@link BlockProbe}: their movement numbers, fluids, and the kind, tags and classes vanilla's movement code asks for.
 * Every answer is the client's own.
 *
 * @param <B> the client's block type
 * @param <S> the client's block state type
 */
public interface MovementProbe<B, S> {
	/**
	 * The block's friction, {@code Block.getFriction}.
	 */
	float friction(B block);

	/**
	 * The block's speed factor, {@code Block.getSpeedFactor}.
	 */
	float speedFactor(B block);

	/**
	 * The block's jump factor, {@code Block.getJumpFactor}.
	 */
	float jumpFactor(B block);

	/**
	 * How much of its fall speed a living entity that does not suppress bouncing keeps, reversed, landing on the
	 * block: what the block's {@code updateEntityAfterFallOn} leaves of a player's downward speed of {@code 1.0}, or its
	 * bounce restitution ({@code getBounceRestitution}) from 26.2, which states it as data.
	 */
	double bounce(B block);

	/**
	 * The speed factors the state's {@code entityInside} hands {@code Entity.makeStuckInBlock} for a player standing in
	 * it, as {@code x, y, z}, or {@code null} when it hands none.
	 */
	double[] stuck(S state);

	/**
	 * The state's fluid state.
	 */
	VanillaFluid fluid(S state);

	/**
	 * Whether the state stops a fluid beside it from flowing toward it or down beside it, as {@code FlowingFluid.getFlow}
	 * asks of its neighbours: the state's material blocks motion until 1.19.4, the state blocks motion
	 * ({@code BlockState.blocksMotion}) from 1.20, and the state is in the {@code blocks_fluid_flow} tag from 26.3.
	 */
	boolean blocksFluidFlow(S state);

	/**
	 * What the block is where vanilla's movement asks by class, identity or tag.
	 */
	BlockKind kind(B block);

	/**
	 * Every block tag the block is in, by name, such as {@code minecraft:climbable}.
	 */
	Set<String> tags(B block);

	/**
	 * The block's movement behaviours, by its tags and classes.
	 */
	Set<MovementBehaviour> behaviours(B block);
}
