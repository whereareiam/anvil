package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.physics.EntityBehaviour;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * How the vanilla client moves its own player ({@code Player} and {@code LocalPlayer}) where only some Minecraft
 * versions do so, with the versions that do: from {@code since}, when set, up to before {@code until}, when set. Each
 * constant names the vanilla code it stands for. What every entity or living entity does differently by version is the
 * physics kit's {@link EntityBehaviour}; how the client ticks its player, loads its level and accepts teleports, which
 * every Java player does, is the Java worker kit's. Anvil supports no release between 1.21.1 and 1.21.11, so a change that
 * lies between them is placed at the version that brought it as the client's code shows it, which no supported release
 * can tell apart.
 */
public enum ClientBehaviour {
	/**
	 * Before 1.18 the client reports its position once it moved more than {@code 0.03} since its last report, a squared
	 * distance of {@code 9.0E-4} ({@code LocalPlayer.sendPosition}); from 1.18 once it moved more than {@code 2.0E-4}.
	 */
	COARSE_POSITION_REPORT(null, "1.18"),
	/**
	 * Before 1.18 {@code Player.aiStep} raises a sprinting player's air control speed by the double
	 * {@code 0.005999999865889549} and rounds the sum to a float; from 1.18 it adds {@code 0.006F}.
	 */
	DOUBLE_SPRINT_AIR_SPEED(null, "1.18"),
	/**
	 * Before 1.19 the client's default options turn auto-jump on ({@code Options.autoJump}), so the player jumps onto a
	 * block it walks into ({@code LocalPlayer.updateAutoJump}); from 1.19 they turn it off.
	 */
	AUTO_JUMP(null, "1.19"),
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
	 * From 1.21.2 the movement packets report horizontal collision, and a change of it alone is reported.
	 */
	REPORTED_HORIZONTAL_COLLISION("1.21.2", null),
	/**
	 * From 1.21.5 the client normalizes its movement keys into a direction ({@code ClientInput.moveVector}), scales it by
	 * 0.98, sneaking and the square-movement correction ({@code LocalPlayer.modifyInput}), and backs off edges with its
	 * box inset by {@code 1.0E-7}.
	 */
	VECTOR_INPUT("1.21.5", null);

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
