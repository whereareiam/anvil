package me.whereareiam.anvil.protocol.java.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A behaviour of vanilla's {@code Entity} and {@code LivingEntity} that only some Minecraft versions have, with the
 * versions that have it: from {@code since}, when set, up to before {@code until}, when set. Each constant names the
 * vanilla code it stands for. Anvil supports no release between 1.21.1 and 1.21.11, so a change that lies between them
 * is placed at the version that brought it as the client's code shows it, which no supported release can tell apart.
 */
public enum EntityBehaviour {
	/**
	 * Before 1.17 an entity moves by moving its bounding box and reading its position back from the box
	 * ({@code setLocationFromBoundingbox}); later versions set the position and rebuild the box from it.
	 */
	BOX_DRIVEN_POSITION(null, "1.17"),
	/**
	 * From 1.20 an entity on the ground remembers the block it stands on ({@code checkSupportingBlock}), whose friction
	 * and factors apply, instead of the block below its centre, and finds the blocks it is inside with its box inset by
	 * {@code 1.0E-7} instead of {@code 0.001}.
	 */
	SUPPORTING_BLOCK("1.20", null),
	/**
	 * From 1.20 the sprint-jump boost is computed in double precision ({@code -Mth.sin(f) * 0.2}); before, in float.
	 */
	DOUBLE_SPRINT_JUMP_BOOST("1.20", null),
	/**
	 * Before 1.21 the eyes test for water {@code 0.11111111F} below the eye height ({@code updateFluidOnEyes}); 1.21.1
	 * tests at the eye height. Releases 1.20.5 and 1.20.6 could not be read and keep the older test.
	 */
	LOWERED_EYE_FLUID_TEST(null, "1.21"),
	/**
	 * From 1.21 stepping up tries the heights of the blocks around the entity in ascending order
	 * ({@code collectCandidateStepUpHeights}); before, the full step height and the height it can rise.
	 */
	CANDIDATE_STEP_HEIGHTS("1.21", null),
	/**
	 * From 1.21 attribute modifiers have namespaced IDs, so sprinting's is {@code minecraft:sprinting}; before, a UUID.
	 */
	NAMESPACED_MODIFIERS("1.21", null),
	/**
	 * From 1.21.2 {@code Mth.sin} and {@code Mth.cos} take a double and index a table built from
	 * {@code i / 10430.378350470453}; before, they take a float.
	 */
	DOUBLE_TRIGONOMETRY("1.21.2", null),
	/**
	 * From 1.21.2 a jump keeps a higher upward speed ({@code Math.max(jumpPower, deltaMovement.y)}), an entity moves even
	 * when its collided movement is tiny but unobstructed, and the effects of the blocks it passes through apply after
	 * its movement along its path ({@code applyEffectsFromBlocks}).
	 */
	PATH_BLOCK_EFFECTS("1.21.2", null),
	/**
	 * From 1.21.2 the air travel reads the gravity after the movement ({@code travelInAir} calls
	 * {@code getEffectiveGravity}), so slow falling sees the velocity the movement left; 1.21.1 reads it before.
	 */
	GRAVITY_AFTER_MOVE("1.21.2", null),
	/**
	 * Before 1.21.5 {@code LivingEntity.aiStep} damps the strafing and forward input by {@code 0.98F} after the jump;
	 * later versions leave the input as the entity's controls set it.
	 */
	DAMPED_INPUT(null, "1.21.5"),
	/**
	 * From 1.21.5 {@code LivingEntity.aiStep} drops a horizontal speed whose square is below {@code 9.0E-6}; before,
	 * each horizontal component below {@code 0.003}.
	 */
	SQUARED_DEAD_ZONE("1.21.5", null),
	/**
	 * From 1.21.5 an entity keeps its fall distance as a double and counts a fall before landing resets it
	 * ({@code Entity.checkFallDamage}); before, as a float, reset on landing before it counts.
	 */
	DOUBLE_FALL_DISTANCE("1.21.5", null);

	private final @Nullable MinecraftVersion since;
	private final @Nullable MinecraftVersion until;

	EntityBehaviour(@Nullable String since, @Nullable String until) {
		this.since = since == null ? null : MinecraftVersion.parse(since);
		this.until = until == null ? null : MinecraftVersion.parse(until);
	}

	/**
	 * Tells whether entities of a version behave so.
	 *
	 * @param version the client's Minecraft version
	 * @return whether the version lies in the behaviour's range
	 */
	public boolean appliesTo(@NotNull MinecraftVersion version) {
		return (since == null || version.isAtLeast(since)) && (until == null || !version.isAtLeast(until));
	}
}
