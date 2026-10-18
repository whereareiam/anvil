package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import org.jetbrains.annotations.NotNull;

/**
 * What the vanilla client of one Minecraft version does differently when it moves its player. Each switch names the
 * vanilla behaviour it stands for and the version that brought it; versions Anvil supports that no source was
 * available for take the behaviour of the nearest version that was read, as the movement guide lists.
 */
final class MovementRules {
	private static final MinecraftVersion V1_17 = MinecraftVersion.parse("1.17");
	private static final MinecraftVersion V1_18 = MinecraftVersion.parse("1.18");
	private static final MinecraftVersion V1_19_4 = MinecraftVersion.parse("1.19.4");
	private static final MinecraftVersion V1_20 = MinecraftVersion.parse("1.20");
	private static final MinecraftVersion V1_20_5 = MinecraftVersion.parse("1.20.5");
	private static final MinecraftVersion V1_21 = MinecraftVersion.parse("1.21");
	private static final MinecraftVersion V1_21_2 = MinecraftVersion.parse("1.21.2");
	private static final MinecraftVersion V1_21_5 = MinecraftVersion.parse("1.21.5");
	private static final MinecraftVersion V1_21_6 = MinecraftVersion.parse("1.21.6");
	private static final MinecraftVersion V26_3 = MinecraftVersion.parse("26.3");

	private final @NotNull MinecraftVersion version;

	MovementRules(@NotNull MinecraftVersion version) {
		this.version = version;
	}

	@NotNull MinecraftVersion version() {
		return version;
	}

	/**
	 * 1.16 moves an entity by moving its bounding box and reading the position back from the box
	 * ({@code setLocationFromBoundingbox}); later versions set the position and rebuild the box from it.
	 */
	boolean boxDrivenPosition() {
		return !version.isAtLeast(V1_17);
	}

	/**
	 * From 1.18 a sprint stops at a wall only when the player does not run almost along it
	 * ({@code isHorizontalCollisionMinor}).
	 */
	boolean minorHorizontalCollision() {
		return version.isAtLeast(V1_18);
	}

	/**
	 * From 1.19.4 the air control speed follows the sprint state of the current tick ({@code Player.getFlyingSpeed});
	 * before, {@code Player.aiStep} set it after the tick's movement, so it follows the previous tick.
	 */
	boolean liveFlyingSpeed() {
		return version.isAtLeast(V1_19_4);
	}

	/**
	 * From 1.20 an entity on the ground remembers the block it stands on ({@code checkSupportingBlock}), whose
	 * friction and factors apply, instead of the block below its centre, and finds the blocks it is inside with its
	 * box inset by {@code 1.0E-7} instead of {@code 0.001}.
	 */
	boolean supportingBlock() {
		return version.isAtLeast(V1_20);
	}

	/**
	 * From 1.20 the sprint-jump boost is computed in double precision ({@code -Mth.sin(f) * 0.2}); before, in float
	 * ({@code -Mth.sin(f) * 0.2F}).
	 */
	boolean doubleSprintJumpBoost() {
		return version.isAtLeast(V1_20);
	}

	/**
	 * From 1.20.5 a sneaking player backs off an edge by testing whether it could fall the step height
	 * ({@code canFallAtLeast}), and not while it moves up; before, by testing its box moved down the step height.
	 */
	boolean fallTestingEdgeBackOff() {
		return version.isAtLeast(V1_20_5);
	}

	/**
	 * From 1.21 stepping up tries the heights of the blocks around the entity in ascending order
	 * ({@code collectCandidateStepUpHeights}); before, the full step height and the height it can rise.
	 */
	boolean candidateStepHeights() {
		return version.isAtLeast(V1_21);
	}

	/**
	 * From 1.21.2 the client ticks its player only after it told the server it loaded, tells the keys it holds
	 * ({@code ServerboundPlayerInputPacket}), ends each tick ({@code ServerboundClientTickEndPacket}) and reports
	 * horizontal collision in its movement packets.
	 */
	boolean playerInputPackets() {
		return version.isAtLeast(V1_21_2);
	}

	/**
	 * Before 1.21.6 the client tells sneaking through a player command; from 1.21.6 only through its held keys.
	 */
	boolean sneakCommands() {
		return !version.isAtLeast(V1_21_6);
	}

	/**
	 * From 1.21.2 {@code Mth.sin} and {@code Mth.cos} take a double and index a table built from
	 * {@code i / 10430.378350470453}; before, they take a float and index a table of {@code i * 2 * PI / 65536}.
	 */
	boolean doubleTrigonometry() {
		return version.isAtLeast(V1_21_2);
	}

	/**
	 * From 1.21.2 a jump keeps a higher upward speed ({@code Math.max(jumpPower, deltaMovement.y)}), a server
	 * teleport clamps the pitch, an entity moves even when its collided movement is tiny but unobstructed, and the
	 * effects of the blocks it passes through apply after its movement along its path
	 * ({@code applyEffectsFromBlocks}).
	 */
	boolean pathBlockEffects() {
		return version.isAtLeast(V1_21_2);
	}

	/**
	 * Before 26.3 the client follows the acceptance of a teleport with its position and view
	 * ({@code ClientPacketListener.handleMovePlayer}); from 26.3 the acceptance carries them itself.
	 */
	boolean teleportConfirmation() {
		return !version.isAtLeast(V26_3);
	}

	/**
	 * From 1.21.5 the client normalizes its movement keys into a direction ({@code ClientInput.moveVector}), scales it
	 * by 0.98, sneaking and the square-movement correction ({@code LocalPlayer.modifyInput}), drops a horizontal speed
	 * whose square is below {@code 9.0E-6}, keeps the fall distance as a double, counts it before landing, and backs
	 * off edges with its box inset by {@code 1.0E-7}.
	 */
	boolean vectorInput() {
		return version.isAtLeast(V1_21_5);
	}
}
