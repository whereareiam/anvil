package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.physics.EntityBehaviour;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A behaviour of the vanilla client's own player ({@code Player} and {@code LocalPlayer}) that only some Minecraft
 * versions have, with the versions that have it: from {@code since}, when set, up to before {@code until}, when set.
 * Each constant names the vanilla code it stands for. What every entity or living entity does differently by version
 * is the physics kit's {@link EntityBehaviour}. Anvil supports no release between 1.21.1 and 1.21.11, so a change that
 * lies between them is placed at the version that brought it as the client's code shows it, which no supported release
 * can tell apart.
 */
public enum ClientBehaviour {
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
	 * From 1.20.5 a sneaking player backs off an edge by testing whether it could fall the step height
	 * ({@code canFallAtLeast}), and not while it moves up; before, by testing its box moved down the step height.
	 */
	FALL_TESTING_EDGE_BACK_OFF("1.20.5", null),
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
	 * 0.98, sneaking and the square-movement correction ({@code LocalPlayer.modifyInput}), and backs off edges with its
	 * box inset by {@code 1.0E-7}.
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
