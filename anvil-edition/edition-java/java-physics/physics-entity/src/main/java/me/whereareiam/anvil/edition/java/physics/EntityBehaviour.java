package me.whereareiam.anvil.edition.java.physics;

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
	 * Before 1.17 a vector's length is a float ({@code Vec3.length} and {@code Vec3.normalize} call
	 * {@code Mth.sqrt(double)}, which returns a float); later versions keep it a double.
	 */
	FLOAT_VECTOR_LENGTH(null, "1.17"),
	/**
	 * Before 1.18 a movement stops the entity's velocity along each horizontal axis whose collided distance differs from
	 * the requested one at all ({@code Entity.move} compares them with {@code !=}), such as one below {@code 1.0E-7}
	 * that the collision took for none; from 1.18 only along an axis the entity collided on ({@code Mth.equal}).
	 */
	EXACT_COLLISION_STOP(null, "1.18"),
	/**
	 * Before 1.18 the world border's shape is among the colliders of every entity inside it ({@code Entity.collide}), so
	 * a movement along an axis shorter than {@code 1.0E-7} is none even with no block near ({@code Shapes.collide}); from
	 * 1.18 the border joins the colliders only near it ({@code isInsideCloseToBorder}).
	 */
	WORLD_BORDER_COLLIDER(null, "1.18"),
	/**
	 * From 1.18 a shape splits into boxes walking its cells up first ({@code BitSetDiscreteVoxelShape.forAllBoxes});
	 * before, east-west first, which orders the boxes an entity's collision queries see differently.
	 */
	HEIGHT_FIRST_SHAPE_BOXES("1.18", null),
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
	 * From 1.21.2 a vector has a direction down to a length of {@code 1.0E-5F} ({@code Vec3.normalize}); before, down to
	 * {@code 1.0E-4}.
	 */
	FINE_NORMALIZATION("1.21.2", null),
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
	DOUBLE_FALL_DISTANCE("1.21.5", null),
	/**
	 * From 26.2 a collision restitutes the entity's velocity ({@code Entity.restituteMovementAfterCollisions}): a blocked
	 * component is reversed and scaled by the bounciness instead of set to zero, and a landing bounces by the larger of
	 * the entity's bounciness attribute and the block's bounce instead of the block's
	 * {@code updateEntityMovementAfterFallOn}.
	 */
	RESTITUTION("26.2", null),
	/**
	 * From 26.3 a landing bounces only when it falls faster than gravity; 26.2 bounces one as fast as gravity too.
	 */
	BOUNCE_ABOVE_GRAVITY("26.3", null),
	/**
	 * From 26.2 the friction and air drag attributes modify the friction of the block below and the air's drag
	 * ({@code computeModifiedFriction}), and on a block whose friction is at most {@code 0.6} the entity walks at its
	 * plain speed ({@code getFrictionInfluencedSpeed}).
	 */
	FRICTION_MODIFIERS("26.2", null);

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
