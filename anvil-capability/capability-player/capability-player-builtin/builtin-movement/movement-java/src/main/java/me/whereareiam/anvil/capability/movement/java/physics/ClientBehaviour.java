package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A behaviour of the vanilla client's movement that only some Minecraft versions have, with the versions that have it:
 * from {@code since}, when set, up to before {@code until}, when set. Each constant names the vanilla code it stands
 * for. Anvil supports no release between 1.21.1 and 1.21.11, so a change that lies between them is placed at the
 * version that brought it as the client's code shows it, which no supported release can tell apart.
 */
public enum ClientBehaviour {
	/**
	 * Before 1.17 an entity moves by moving its bounding box and reading its position back from the box
	 * ({@code setLocationFromBoundingbox}); later versions set the position and rebuild the box from it.
	 */
	BOX_DRIVEN_POSITION(null, "1.17"),
	/**
	 * From 1.18 a sprint stops at a wall only when the player does not run almost along it
	 * ({@code isHorizontalCollisionMinor}).
	 */
	MINOR_HORIZONTAL_COLLISION("1.18", null),
	/**
	 * From 1.19.4 the air control speed follows the sprint state of the current tick ({@code Player.getFlyingSpeed});
	 * before, {@code Player.aiStep} set it after the tick's movement, so it follows the previous tick.
	 */
	LIVE_FLYING_SPEED("1.19.4", null),
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
	 * From 1.20.5 a sneaking player backs off an edge by testing whether it could fall the step height
	 * ({@code canFallAtLeast}), and not while it moves up; before, by testing its box moved down the step height.
	 */
	FALL_TESTING_EDGE_BACK_OFF("1.20.5", null),
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
	 * From 1.21.2 a flying player's fall distance resets before its movement ({@code Player.aiStep}); 1.21.1 resets it
	 * after its travel ({@code Player.travel}).
	 */
	FLIGHT_RESETS_FALL_FIRST("1.21.2", null),
	/**
	 * From 1.21.2 a server teleport clamps the pitch to a quarter turn ({@code PositionMoveRotation.calculateAbsolute}).
	 */
	CLAMPED_TELEPORT_PITCH("1.21.2", null),
	/**
	 * From 1.21.2 the movement packets report horizontal collision, and a change of it alone is reported.
	 */
	REPORTED_HORIZONTAL_COLLISION("1.21.2", null),
	/**
	 * From 1.21.5 the client normalizes its movement keys into a direction ({@code ClientInput.moveVector}), scales it by
	 * 0.98, sneaking and the square-movement correction ({@code LocalPlayer.modifyInput}), drops a horizontal speed whose
	 * square is below {@code 9.0E-6}, keeps the fall distance as a double, counts it before landing, and backs off edges
	 * with its box inset by {@code 1.0E-7}.
	 */
	VECTOR_INPUT("1.21.5", null),
	/**
	 * Before 26.3 the client follows the acceptance of a teleport with its position and view
	 * ({@code ClientPacketListener.handleMovePlayer}); from 26.3 the acceptance carries them itself.
	 */
	TELEPORT_CONFIRMATION(null, "26.3");

	private final @Nullable MinecraftVersion since;
	private final @Nullable MinecraftVersion until;

	ClientBehaviour(@Nullable String since, @Nullable String until) {
		this.since = since == null ? null : MinecraftVersion.parse(since);
		this.until = until == null ? null : MinecraftVersion.parse(until);
	}

	/**
	 * Tells whether a client of a version behaves so.
	 *
	 * @param version the client's Minecraft version
	 * @return whether the version lies in the behaviour's range
	 */
	public boolean appliesTo(@NotNull MinecraftVersion version) {
		return (since == null || version.isAtLeast(since)) && (until == null || !version.isAtLeast(until));
	}
}
